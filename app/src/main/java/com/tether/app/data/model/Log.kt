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
    val createdAt: Long = 0L,
    /** "manual" (log sheet), "timer" (focus session) or "system" (joined/created). */
    val source: String = "manual",
    /** A proof photo exists at groupStats/{groupId}/proofs/{id} (visible for 24 h). */
    val hasPhoto: Boolean = false
)
