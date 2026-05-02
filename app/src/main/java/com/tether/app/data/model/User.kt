package com.tether.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalHours: Double = 0.0,
    val groupIds: List<String> = emptyList(),
    val lastLogDate: String = ""
)
