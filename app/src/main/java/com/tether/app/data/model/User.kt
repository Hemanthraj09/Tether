package com.tether.app.data.model

/**
 * Public profile (users/{uid}), readable by other signed-in users.
 * Deliberately no email and no group list: the email stays in Firebase Auth,
 * and membership lives on each group's members list.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val totalHours: Double = 0.0
)
