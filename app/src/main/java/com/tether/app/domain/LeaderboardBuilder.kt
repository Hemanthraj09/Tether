package com.tether.app.domain

import com.tether.app.data.repository.CodingStatsRepository
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.utils.Formatters

/**
 * Turns raw Firestore data into ranked leaderboard rows. Pure function:
 * the repository only gathers inputs from snapshot listeners.
 */
object LeaderboardBuilder {

    data class StreakInfo(val current: Int, val lastLogDate: String)

    /** Pace chip text, shown only when behind yesterday by more than a trivial amount. */
    fun paceLabel(todayHours: Double, yesterdayHours: Double): String {
        if (yesterdayHours <= 0.5 || todayHours >= yesterdayHours) return ""
        val totalMins = ((yesterdayHours - todayHours) * 60).toInt()
        val hrs = totalMins / 60
        val mins = totalMins % 60
        return if (hrs > 0) "${hrs}h ${mins}m behind yesterday" else "${mins}m behind yesterday"
    }

    fun build(
        members: List<String>,
        names: Map<String, String>,
        todayHours: Map<String, Double>,
        yesterdayHours: Map<String, Double>,
        weeklyHours: Map<String, Double>,
        streaks: Map<String, StreakInfo>,
        nudgedByMe: Set<String>,
        currentUid: String,
        today: String
    ): List<LeaderboardEntry> =
        members.map { uid ->
            val name = names[uid] ?: "Unknown"
            val todayH = todayHours[uid] ?: 0.0
            val streak = streaks[uid]?.let {
                StreakCalculator.displayed(it.current, it.lastLogDate, today)
            } ?: 0

            LeaderboardEntry(
                uid = uid,
                name = name,
                initials = Formatters.initials(name),
                hours = weeklyHours[uid] ?: 0.0,
                todayHours = todayH,
                streak = streak,
                avatarColorHex = Formatters.avatarColor(uid),
                isCurrentUser = uid == currentUid,
                paceLabel = paceLabel(todayH, yesterdayHours[uid] ?: 0.0),
                hasNudgedToday = uid in nudgedByMe
            )
        }.sortedByDescending { it.todayHours } // stable: ties keep member order

    /**
     * Adds coding stats to a Coding group's leaderboard and, if the group ranks
     * by problems solved, re-sorts by today's solves (hours break ties).
     */
    fun withSolves(
        entries: List<LeaderboardEntry>,
        stats: Map<String, CodingStatsRepository.MemberStats>,
        rankBySolves: Boolean
    ): List<LeaderboardEntry> {
        val merged = entries.map { e ->
            val s = stats[e.uid]
            e.copy(
                solvedToday = s?.solvedToday ?: 0,
                solvedWeek = s?.solvedWeek ?: 0,
                rankedBySolves = rankBySolves,
                leetcodeNote = when {
                    s == null || s.leetcodeUsername == null -> "LeetCode not connected"
                    s.unconfirmedCount > 0 -> "⚠ ${s.unconfirmedCount} unconfirmed"
                    s.ownershipVerified == true -> "✓ LeetCode verified"
                    else -> "@${s.leetcodeUsername}"
                }
            )
        }
        return if (rankBySolves) {
            merged.sortedWith(compareByDescending<LeaderboardEntry> { it.solvedToday ?: 0 }.thenByDescending { it.todayHours })
        } else merged
    }
}
