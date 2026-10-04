package com.tether.app.utils

/** Shared display helpers (previously copy-pasted across adapters/fragments). */
object Formatters {

    private val avatarColors = listOf(
        "#3B82F6", "#22C55E", "#A855F7",
        "#EC4899", "#EAB308", "#EF4444",
        "#F97316", "#06B6D4"
    )

    /** 0.5 → "30m", 1.0 → "1h", 1.5 → "1h 30m". */
    fun formatHours(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    fun initials(name: String): String =
        name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()

    fun avatarColor(uid: String): String =
        avatarColors[uid.hashCode().and(0x7fffffff).rem(avatarColors.size)]
}
