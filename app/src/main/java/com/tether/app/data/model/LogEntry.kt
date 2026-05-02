package com.tether.app.data.model

import com.google.firebase.Timestamp

data class LogEntry(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val initials: String = "",
    val avatarColorHex: String = "#FF6B2B",
    val activityType: String = "",
    val hours: Double = 0.0,
    val note: String = "",
    val photoUrl: String? = null,
    val timestamp: Timestamp = Timestamp.now()
)