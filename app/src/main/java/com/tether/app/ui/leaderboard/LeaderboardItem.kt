package com.tether.app.ui.leaderboard

data class LeaderboardItem(
    val id: Int,
    val name: String,
    val initials: String,
    val hours: Double,
    val todayHours: Double = 0.0,
    val streak: Int,
    val avatarColorHex: String,
    val isCurrentUser: Boolean,
    val paceLabel: String = ""
)