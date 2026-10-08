package com.tether.app.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turns a member's completion index into the numbers the core loop uses:
 * problems solved today / this week (leaderboard, streak credit) and per day
 * (LeetCode heatmap). Only verified LeetCode solves count, minus any claims
 * LeetCode's public data contradicts.
 */
object SolveCounts {

    data class Counts(val today: Int, val week: Int)

    private fun verified(index: Map<String, CompletionEntry>, unconfirmed: Set<String>) =
        index.filter { (key, entry) -> entry.source == CompletionSource.LEETCODE && key !in unconfirmed }

    fun count(
        index: Map<String, CompletionEntry>,
        unconfirmed: Set<String>,
        dayStartMillis: Long,
        weekStartMillis: Long
    ): Counts {
        val solves = verified(index, unconfirmed).values
        return Counts(
            today = solves.count { it.completedAt >= dayStartMillis },
            week = solves.count { it.completedAt >= weekStartMillis }
        )
    }

    /** "yyyy-MM-dd" (in [timeZone]) → number of verified solves that day. */
    fun byDay(
        index: Map<String, CompletionEntry>,
        unconfirmed: Set<String> = emptySet(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Map<String, Int> {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
        return verified(index, unconfirmed).values
            .groupingBy { format.format(Date(it.completedAt)) }
            .eachCount()
    }
}
