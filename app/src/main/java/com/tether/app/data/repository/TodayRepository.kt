package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Log
import com.tether.app.data.snapshotFlow
import com.tether.app.utils.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** One of today's logs in a group, with its reactions. */
data class TodayLog(
    val log: Log,
    val isMine: Boolean,
    val okCount: Int,
    val doubtCount: Int,
    /** The viewer's own reaction: "ok", "doubt" or null. */
    val myReaction: String?
)

/**
 * The group's "Today" list: today's logs (with what was done and an optional
 * photo) plus friends' ✓ / 🤨 reactions. Two listeners per group, both on
 * today's date, so they roll over at midnight.
 *
 * Reactions live at groupStats/{g}/reactions/{logId}_{uid}: one per person
 * per log (the id is the rate limit), members only, never on your own log.
 */
class TodayRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val uid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(groupId: String): Flow<List<TodayLog>> = DateKeys.todayFlow().flatMapLatest { today ->
        val logs = firestore.collection("logs")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("date", today)
            .snapshotFlow()
            .map { snap -> snap?.documents.orEmpty().mapNotNull { it.toObject(Log::class.java) } }
        val reactions = reactionsRef(groupId)
            .whereEqualTo("date", today)
            .snapshotFlow()
            .map { snap ->
                snap?.documents.orEmpty().mapNotNull { doc ->
                    val logId = doc.getString("logId") ?: return@mapNotNull null
                    Triple(logId, doc.getString("uid") ?: "", doc.getString("kind") ?: "")
                }
            }
        combine(logs, reactions) { logList, reactionList -> merge(logList, reactionList, uid) }
    }

    /** Sets (or replaces) your reaction; [kind] null removes it. */
    suspend fun react(groupId: String, log: Log, kind: String?): Result<Unit> = runCatching {
        val me = uid.ifEmpty { error("Not signed in") }
        val ref = reactionsRef(groupId).document("${log.id}_$me")
        if (kind == null) ref.delete().await()
        else ref.set(mapOf("logId" to log.id, "uid" to me, "date" to log.date, "kind" to kind)).await()
        Unit
    }

    /** Account deletion: remove your reactions in a group (call while still a member). */
    suspend fun deleteMyReactions(groupId: String) {
        val me = uid.ifEmpty { return }
        reactionsRef(groupId).whereEqualTo("uid", me).get().await()
            .documents.forEach { it.reference.delete().await() }
    }

    private fun reactionsRef(groupId: String) =
        firestore.collection("groupStats").document(groupId).collection("reactions")

    companion object {
        const val OK = "ok"
        const val DOUBT = "doubt"

        /** Real work only (system "joined" logs are 0 h), newest first. Pure, for tests. */
        fun merge(logs: List<Log>, reactions: List<Triple<String, String, String>>, me: String): List<TodayLog> {
            val byLog = reactions.groupBy { it.first }
            return logs.filter { it.value > 0 }
                .sortedByDescending { it.createdAt }
                .map { log ->
                    val r = byLog[log.id].orEmpty().filter { it.second != log.userId }
                    TodayLog(
                        log = log,
                        isMine = log.userId == me,
                        okCount = r.count { it.third == OK },
                        doubtCount = r.count { it.third == DOUBT },
                        myReaction = r.firstOrNull { it.second == me }?.third
                    )
                }
        }
    }
}
