package com.tether.app.data.model

data class Log(
    val id: String = "",
    val userId: String = "",
    val groupId: String = "",
    val userName: String = "",
    val userInitials: String = "",
    val avatarColorHex: String = "",
    val date: String = "",
    val value: Double = 0.0,
    val note: String = "",
    val createdAt: Long = 0L
)
