package com.tether.app.domain

import com.tether.app.utils.DateKeys

/**
 * Per-group streak rules, kept free of Android/Firebase so they're unit-testable.
 *
 * Stored state: groupStats/{gid}/streaks/{uid} = { currentStreak, longestStreak, lastLogDate }.
 */
object StreakCalculator {

    data class Streak(
        val current: Int,
        val longest: Int,
        val lastLogDate: String
    )

    /**
     * New streak after logging on [today] ("yyyy-MM-dd").
     *  - already logged today → unchanged
     *  - last log was yesterday → +1
     *  - anything else (gap, first log) → restart at 1
     */
    fun afterLog(previous: Streak?, today: String): Streak {
        val last = previous?.lastLogDate.orEmpty()
        val current = previous?.current ?: 0
        val longest = previous?.longest ?: 0

        val next = when (last) {
            today -> current.coerceAtLeast(1)
            DateKeys.previousDay(today) -> current + 1
            else -> 1
        }
        return Streak(current = next, longest = maxOf(next, longest), lastLogDate = today)
    }

    /**
     * Streak to *display*. The stored value is only updated when someone logs,
     * so a streak whose last log is older than yesterday is already broken.
     */
    fun displayed(storedCurrent: Int, lastLogDate: String, today: String): Int =
        if (lastLogDate == today || lastLogDate == DateKeys.previousDay(today)) storedCurrent else 0
}
