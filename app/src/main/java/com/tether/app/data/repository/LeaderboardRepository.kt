package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class LeaderboardRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun getLeaderboard(
        groupId: String
    ): Result<List<LeaderboardEntry>> {
        return try {
            val entries = fetchLeaderboardData(groupId)
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLeaderboardFlow(
        groupId: String
    ): Flow<List<LeaderboardEntry>> = callbackFlow {
        val weekKey = getCurrentWeekKey()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd",
            java.util.Locale.getDefault()).format(java.util.Date())
        
        val groupListener = firestore
            .collection("groups")
            .document(groupId)
            .addSnapshotListener { _, _ ->
                repositoryScope.launch {
                    trySend(fetchLeaderboardData(groupId))
                }
            }

        val statsListener = firestore
            .collection("groupStats")
            .document(groupId)
            .collection("weekly")
            .document(weekKey)
            .addSnapshotListener { _, _ ->
                repositoryScope.launch {
                    trySend(fetchLeaderboardData(groupId))
                }
            }

        val dailyListener = firestore
            .collection("groupStats")
            .document(groupId)
            .collection("daily")
            .document(today)
            .addSnapshotListener { _, _ ->
                repositoryScope.launch {
                    trySend(fetchLeaderboardData(groupId))
                }
            }

        awaitClose {
            groupListener.remove()
            statsListener.remove()
            dailyListener.remove()
        }
    }

    private suspend fun fetchLeaderboardData(
        groupId: String
    ): List<LeaderboardEntry> {
        val groupDoc = firestore
            .collection("groups")
            .document(groupId)
            .get()
            .await()

        val group = groupDoc
            .toObject(Group::class.java)
            ?: return emptyList()

        val weekKey = getCurrentWeekKey()

        val hoursDocSnapshot = try {
            firestore
                .collection("groupStats")
                .document(groupId)
                .collection("weekly")
                .document(weekKey)
                .get()
                .await()
        } catch (e: Exception) {
            null
        }

        val today = java.text.SimpleDateFormat("yyyy-MM-dd",
            java.util.Locale.getDefault()).format(java.util.Date())

        val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd",
            java.util.Locale.getDefault()).let { sdf ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            sdf.format(cal.time)
        }

        val todayStatsSnapshot = try {
            firestore.collection("groupStats")
                .document(groupId)
                .collection("daily")
                .document(today)
                .get().await()
        } catch (e: Exception) { null }

        val yesterdayStatsSnapshot = try {
            firestore.collection("groupStats")
                .document(groupId)
                .collection("daily")
                .document(yesterday)
                .get().await()
        } catch (e: Exception) { null }

        return group.members.map { uid ->
            val userDoc = firestore
                .collection("users")
                .document(uid)
                .get()
                .await()

            val name = userDoc
                .getString("name") ?: "Unknown"
            
            val streakDoc = try {
                firestore.collection("groupStats")
                    .document(groupId)
                    .collection("streaks")
                    .document(uid)
                    .get().await()
            } catch (e: Exception) { null }
            val streak = streakDoc?.getLong("currentStreak")?.toInt() ?: 0

            val hours = hoursDocSnapshot?.getDouble(uid) ?: 0.0

            val todayHours = todayStatsSnapshot?.getDouble(uid) ?: 0.0
            val yesterdayHours = yesterdayStatsSnapshot?.getDouble(uid) ?: 0.0

            // Pace logic: only show if behind yesterday
            val paceLabel = if (yesterdayHours > 0.5 && todayHours < yesterdayHours) {
                val diff = yesterdayHours - todayHours
                val totalMins = (diff * 60).toInt()
                val hrs = totalMins / 60
                val mins = totalMins % 60
                if (hrs > 0) "${hrs}h ${mins}m behind yesterday"
                else "${mins}m behind yesterday"
            } else ""

            val initials = name
                .split(" ")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .take(2)
                .joinToString("")
                .uppercase()

            val avatarColors = listOf(
                "#3B82F6", "#22C55E", "#A855F7",
                "#EC4899", "#EAB308", "#EF4444",
                "#F97316", "#06B6D4"
            )
            val avatarColor = avatarColors[
                uid.hashCode().and(0x7fffffff)
                    .rem(avatarColors.size)]

            val hasNudgedToday = try {
                val nudgeKey = "${currentUid}_$uid"
                firestore.collection("nudges")
                    .document(groupId)
                    .collection(today)
                    .document(nudgeKey)
                    .get().await().exists()
            } catch (e: Exception) { false }

            LeaderboardEntry(
                uid = uid,
                name = name,
                initials = initials,
                hours = hours,
                todayHours = todayHours,
                streak = streak,
                avatarColorHex = avatarColor,
                isCurrentUser = uid == currentUid,
                paceLabel = paceLabel,
                hasNudgedToday = hasNudgedToday
            )
        }.sortedByDescending { it.todayHours }
    }

    private fun getCurrentWeekKey(): String {
        val cal = Calendar.getInstance()
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        return "$year-W$week"
    }
}

data class LeaderboardEntry(
    val uid: String,
    val name: String,
    val initials: String,
    val hours: Double,
    val todayHours: Double = 0.0,
    val streak: Int,
    val avatarColorHex: String,
    val isCurrentUser: Boolean,
    val paceLabel: String = "",
    val hasNudgedToday: Boolean = false
)
