package com.tether.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.MainActivity
import com.tether.app.R
import com.tether.app.data.model.Group
import com.tether.app.data.repository.GroupRepository
import com.tether.app.data.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform

/**
 * Listens (while the app process is alive) for:
 *  - nudges sent to the current user → system notification
 *  - other members' logs in the user's groups → in-app activity bell
 *
 * One instance for the whole app, started from MainActivity. It follows the
 * signed-in user automatically and re-subscribes when groups change or the
 * day rolls over. Each event is handled exactly once (no duplicate
 * listeners piling up every time the home screen is opened).
 */
object RealtimeWatcher {

    private const val NUDGE_CHANNEL_ID = "nudge_channel"

    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private val seenIds = mutableSetOf<String>()
    private var watcherStartedAt = 0L
    private var holders = 0

    /**
     * Each MainActivity instance acquires on create and releases on destroy.
     * Reference counting matters on logout, where the new activity is created
     * before the old one is destroyed.
     */
    fun acquire(context: Context) {
        holders++
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        if (watcherStartedAt == 0L) watcherStartedAt = System.currentTimeMillis()
        job = watchEvents(appContext).launchIn(newScope)
    }

    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders > 0) return
        scope?.cancel()
        scope = null
        job = null
    }

    private sealed class Event {
        data class Nudge(val id: String, val nudgerName: String, val groupId: String, val time: Long) : Event()
        data class GroupLog(val id: String, val message: String, val time: Long) : Event()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchEvents(context: Context): Flow<Event> =
        authUidFlow()
            .flatMapLatest { uid ->
                if (uid == null) {
                    emptyFlow()
                } else {
                    combine(GroupRepository().observeUserGroups(), DateKeys.todayFlow()) { groups, today ->
                        groups to today
                    }
                        .distinctUntilChanged()
                        .flatMapLatest { (groups, today) -> watchGroups(uid, groups, today) }
                }
            }
            .onEach { event -> handle(context, event) }

    private fun watchGroups(uid: String, groups: List<Group>, today: String): Flow<Event> {
        if (groups.isEmpty()) return emptyFlow()
        val firestore = FirebaseFirestore.getInstance()

        val flows = groups.flatMap { group ->
            val logs = firestore.collection("logs")
                .whereEqualTo("groupId", group.id)
                .whereEqualTo("date", today)
                .snapshotFlow()
                .addedEvents { doc ->
                    if (doc.getString("userId") == uid) return@addedEvents null
                    val userName = doc.getString("userName") ?: "Someone"
                    val hours = doc.getDouble("value") ?: 0.0
                    val message = if (hours > 0.0) {
                        // "Asha logged 1h 30m in Study Squad: Solved 3 graph problems 📷"
                        val note = (doc.getString("note") ?: "").trim().let {
                            if (it.length > 60) it.take(57) + "…" else it
                        }
                        val photo = if (doc.getBoolean("hasPhoto") == true) " 📷" else ""
                        "$userName logged ${Formatters.formatHours(hours)} in ${group.name}" +
                            (if (note.isNotEmpty()) ": $note" else "") + photo
                    } else if ((doc.getString("note") ?: "").startsWith("Joined")) {
                        "$userName joined ${group.name} 👋"
                    } else {
                        return@addedEvents null  // other system logs (0h) aren't real activity
                    }
                    Event.GroupLog(doc.id, message, doc.getLong("createdAt") ?: 0L)
                }

            val nudges = firestore.collection("groupStats").document(group.id)
                .collection("nudges")
                .whereEqualTo("nudgedUid", uid)
                .whereEqualTo("date", today)
                .snapshotFlow()
                .addedEvents { doc ->
                    Event.Nudge(
                        id = doc.reference.path,
                        nudgerName = doc.getString("nudgerName") ?: "Someone",
                        groupId = group.id,
                        time = doc.getLong("timestamp") ?: 0L
                    )
                }
            listOf(logs, nudges)
        }

        // Verified solves synced by people you share a group with (e.g. from LeetCode).
        // Single range filter → no composite index; the source is filtered client-side.
        val solves = groups.flatMap { it.members }.toSet().minus(uid).map { memberUid ->
            firestore.collection("users").document(memberUid).collection("completions")
                .whereGreaterThanOrEqualTo("syncedAt", DateKeys.startOfTodayMillis())
                .snapshotFlow()
                .addedEvents { doc ->
                    if (doc.getString("source") != "leetcode") return@addedEvents null
                    val name = doc.getString("userName") ?: "Someone"
                    val title = doc.getString("title") ?: return@addedEvents null
                    Event.GroupLog(doc.reference.path, "$name solved $title ✓", doc.getLong("syncedAt") ?: 0L)
                }
        }
        return merge(*(flows + solves).toTypedArray())
    }

    /** Emits mapped events for documents newly ADDED to a query. */
    private fun Flow<com.google.firebase.firestore.QuerySnapshot?>.addedEvents(
        map: (com.google.firebase.firestore.DocumentSnapshot) -> Event?
    ): Flow<Event> = transform { snapshot ->
        snapshot?.documentChanges
            ?.filter { it.type == DocumentChange.Type.ADDED }
            ?.forEach { change -> map(change.document)?.let { emit(it) } }
    }

    private fun handle(context: Context, event: Event) {
        val (id, time) = when (event) {
            is Event.Nudge -> event.id to event.time
            is Event.GroupLog -> event.id to event.time
        }
        // Everything from today goes into the activity bell exactly once, including
        // what happened while the app was closed (NotificationStore dedupes by id).
        if (time < DateKeys.startOfTodayMillis() || !seenIds.add(id)) return

        when (event) {
            is Event.Nudge -> {
                NotificationStore.addNotification(context, "⚡ ${event.nudgerName} nudged you", id, time)
                // A system notification only for nudges that arrive while we're running;
                // older ones were missed and just appear in the bell.
                if (time > watcherStartedAt) showNudgeNotification(context, event)
            }
            is Event.GroupLog -> NotificationStore.addNotification(context, event.message, id, time)
        }
    }

    private fun showNudgeNotification(context: Context, nudge: Event.Nudge) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(NUDGE_CHANNEL_ID, "Nudges", NotificationManager.IMPORTANCE_HIGH)
        )

        // Tapping the notification opens the group it came from.
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_GROUP, nudge.groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, nudge.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
            .setContentTitle("⚡ You got nudged!")
            .setContentText("${nudge.nudgerName} nudged you! Time to get back on track.")
            .setSmallIcon(R.drawable.ic_flame)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(nudge.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notification permission revoked; nothing to do.
        }
    }

    private fun authUidFlow(): Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()
}
