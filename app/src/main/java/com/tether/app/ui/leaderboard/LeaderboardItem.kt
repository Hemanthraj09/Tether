package com.tether.app.ui.leaderboard

import com.tether.app.data.repository.LeaderboardEntry

data class LeaderboardItem(
    val id: Int,            // 1-based rank
    val name: String,
    val initials: String,
    val hours: Double,
    val todayHours: Double = 0.0,
    val streak: Int,
    val avatarColorHex: String,
    val isCurrentUser: Boolean,
    val paceLabel: String = "",
    val uid: String = "",
    val hasNudgedToday: Boolean = false,
    /** Coding groups only: problems solved in the shown period (null elsewhere). */
    val solves: Int? = null,
    val solvesPeriod: String = "today",
    val rankedBySolves: Boolean = false,
    val leetcodeNote: String = ""
)

/** Converts ranked entries to rows. [weekly] shows weekly hours/solves and hides pace. */
fun List<LeaderboardEntry>.toLeaderboardItems(weekly: Boolean = false): List<LeaderboardItem> =
    mapIndexed { index, entry ->
        LeaderboardItem(
            id = index + 1,
            name = entry.name,
            initials = entry.initials,
            hours = if (weekly) entry.hours else entry.todayHours,
            todayHours = entry.todayHours,
            streak = entry.streak,
            avatarColorHex = entry.avatarColorHex,
            isCurrentUser = entry.isCurrentUser,
            paceLabel = if (weekly) "" else entry.paceLabel,
            uid = entry.uid,
            hasNudgedToday = entry.hasNudgedToday,
            solves = if (weekly) entry.solvedWeek else entry.solvedToday,
            solvesPeriod = if (weekly) "this week" else "today",
            rankedBySolves = entry.rankedBySolves,
            leetcodeNote = entry.leetcodeNote
        )
    }
