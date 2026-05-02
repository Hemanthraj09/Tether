package com.tether.app.ui.leaderboard

data class LeaderboardItem(
    val id: Int,
    val name: String,
    val initials: String,
    val hours: Double,
    val streak: Int,
    val avatarColorHex: String,
    val isCurrentUser: Boolean
)