package com.tether.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.hoursByUid
import com.tether.app.data.model.Group
import com.tether.app.data.snapshotFlow
import com.tether.app.utils.DateKeys
import com.tether.app.domain.LeaderboardBuilder
import com.tether.app.domain.LeaderboardBuilder.StreakInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Real-time leaderboard built from a handful of snapshot listeners that are
 * combined in memory. Previously every change re-fetched the group, stats,
 * and 3 documents per member from the server, one after another.
 */
class LeaderboardRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val currentUid: String
        get() = auth.currentUser?.uid ?: ""

    /** Re-subscribes automatically at midnight so "today" resets on its own. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLeaderboard(groupId: String): Flow<List<LeaderboardEntry>> =
        DateKeys.todayFlow()
            .flatMapLatest { today -> observeForDate(groupId, today) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeForDate(
        groupId: String,
        today: String
    ): Flow<List<LeaderboardEntry>> {
        val me = currentUid
        val yesterday = DateKeys.previousDay(today)
        val statsRef = firestore.collection("groupStats").document(groupId)

        val groupFlow = firestore.collection("groups").document(groupId)
            .snapshotFlow()
            .map { it?.toObject(Group::class.java) }

        val namesFlow = groupFlow
            .map { it?.members ?: emptyList() }
            .distinctUntilChanged()
            .flatMapLatest { members -> observeNames(members) }

        val todayFlow = statsRef.collection("daily").document(today)
            .snapshotFlow().map { it.hoursByUid() }
        val yesterdayFlow = statsRef.collection("daily").document(yesterday)
            .snapshotFlow().map { it.hoursByUid() }
        val weeklyFlow = statsRef.collection("weekly").document(DateKeys.weekKey())
            .snapshotFlow().map { it.hoursByUid() }

        val streaksFlow = statsRef.collection("streaks")
            .snapshotFlow()
            .map { snapshot ->
                snapshot?.documents?.associate { doc -> doc.id to doc.toStreak() } ?: emptyMap()
            }

        val nudgedFlow = statsRef.collection("nudges")
            .whereEqualTo("date", today)
            .snapshotFlow()
            .map { snapshot ->
                snapshot?.documents
                    ?.filter { it.getString("nudgerUid") == me }
                    ?.mapNotNull { it.getString("nudgedUid") }
                    ?.toSet()
                    ?: emptySet()
            }

        val statsFlow = combine(todayFlow, yesterdayFlow, weeklyFlow, streaksFlow, nudgedFlow) {
                todayHours, yesterdayHours, weeklyHours, streaks, nudged ->
            Stats(todayHours, yesterdayHours, weeklyHours, streaks, nudged)
        }

        return combine(groupFlow, namesFlow, statsFlow) { group, names, stats ->
            if (group == null) return@combine emptyList()
            LeaderboardBuilder.build(
                members = group.members,
                names = names,
                todayHours = stats.today,
                yesterdayHours = stats.yesterday,
                weeklyHours = stats.weekly,
                streaks = stats.streaks,
                nudgedByMe = stats.nudged,
                currentUid = me,
                today = today
            )
        }
    }

    private fun observeNames(members: List<String>): Flow<Map<String, String>> {
        if (members.isEmpty()) return flowOf(emptyMap())
        val chunkFlows = members.chunked(10).map { chunk ->
            firestore.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .snapshotFlow()
                .map { snapshot ->
                    snapshot?.documents?.associate { doc ->
                        doc.id to (doc.getString("name")?.takeIf { it.isNotBlank() } ?: "Unknown")
                    } ?: emptyMap()
                }
        }
        return combine(chunkFlows) { maps -> maps.fold(emptyMap()) { acc, m -> acc + m } }
    }

    private fun DocumentSnapshot.toStreak() = StreakInfo(
        current = getLong("currentStreak")?.toInt() ?: 0,
        lastLogDate = getString("lastLogDate") ?: ""
    )

    private data class Stats(
        val today: Map<String, Double>,
        val yesterday: Map<String, Double>,
        val weekly: Map<String, Double>,
        val streaks: Map<String, StreakInfo>,
        val nudged: Set<String>
    )
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
    val hasNudgedToday: Boolean = false,
    /** Coding groups only (null elsewhere): verified LeetCode solves. */
    val solvedToday: Int? = null,
    val solvedWeek: Int? = null,
    /** Coding groups can rank by problems solved instead of hours. */
    val rankedBySolves: Boolean = false,
    /** e.g. "✓ LeetCode verified", "LeetCode not connected" (coding groups only). */
    val leetcodeNote: String = ""
)
