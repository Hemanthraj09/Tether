package com.tether.app.data.model

// Streaks are stored per group in groupStats/{gid}/streaks/{uid}, not here.
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val totalHours: Double = 0.0,
    val groupIds: List<String> = emptyList()
)
