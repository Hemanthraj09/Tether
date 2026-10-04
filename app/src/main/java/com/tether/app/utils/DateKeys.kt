package com.tether.app.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date strings used as Firestore keys ("2026-05-03", "2026-W18").
 *
 * Always formatted with Locale.US so every device produces identical keys,
 * regardless of the user's language / digit settings.
 */
object DateKeys {

    private const val PATTERN = "yyyy-MM-dd"

    private fun formatter() = SimpleDateFormat(PATTERN, Locale.US)

    fun today(): String = formatter().format(Date())

    fun yesterday(): String = previousDay(today())

    fun previousDay(date: String): String {
        val sdf = formatter()
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(date) ?: Date()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        return sdf.format(cal.time)
    }

    /**
     * Week key like "2026-W18". Week numbering follows the device calendar
     * (same as before), but the year is the *week-year*, so the last days of
     * December no longer collide with week 1 of the same calendar year.
     */
    fun weekKey(calendar: Calendar = Calendar.getInstance()): String {
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        val year = calendar.weekYear
        return "$year-W$week"
    }

    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /** Emits today's key now and again right after each midnight. */
    fun todayFlow(): Flow<String> = flow {
        while (true) {
            emit(today())
            delay(millisUntilMidnight() + 1_000)
        }
    }.distinctUntilChanged()

    private fun millisUntilMidnight(): Long {
        val now = Calendar.getInstance()
        val midnight = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(1_000)
    }
}
