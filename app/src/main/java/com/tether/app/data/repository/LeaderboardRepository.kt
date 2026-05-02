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

        awaitClose {
            groupListener.remove()
            statsListener.remove()
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
            ?: throw Exception("Group not found")

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

        return group.members.map { uid ->
            val userDoc = firestore
                .collection("users")
                .document(uid)
                .get()
                .await()

            val name = userDoc
                .getString("name") ?: "Unknown"
            val streak = userDoc
                .getLong("currentStreak")
                ?.toInt() ?: 0

            val hours = hoursDocSnapshot?.getDouble(uid) ?: 0.0

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

            LeaderboardEntry(
                uid = uid,
                name = name,
                initials = initials,
                hours = hours,
                streak = streak,
                avatarColorHex = avatarColor,
                isCurrentUser = uid == currentUid
            )
        }.sortedByDescending { it.hours }
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
    val streak: Int,
    val avatarColorHex: String,
    val isCurrentUser: Boolean
)
