package com.tether.app.domain

import com.tether.app.data.leetcode.RecentSolve

/** Where a completion came from. Stored as "leetcode" / "self". */
enum class CompletionSource(val id: String) {
    /** Seen in the user's public LeetCode data: verified. */
    LEETCODE("leetcode"),
    /** Legacy: ticked by hand in the retired sheets feature. Parsed, never counted. */
    SELF("self");

    companion object {
        fun from(id: String?): CompletionSource? = entries.firstOrNull { it.id == id }
    }
}

/** One completed item: users/{uid}/completions/{key}. key = LeetCode problem slug. */
data class Completion(
    val key: String,
    val title: String,
    val slug: String?,
    val source: CompletionSource,
    val completedAt: Long
)

/**
 * Decides which completions to write after fetching recent LeetCode solves.
 * Pure function → unit-tested; the repository just executes the result.
 */
object SolveMerger {

    fun merge(existing: Map<String, Completion>, recent: List<RecentSolve>): List<Completion> {
        // The same problem can appear several times in "recent"; the first solve counts.
        val earliestBySlug = recent.groupBy { it.slug }.mapValues { (_, solves) -> solves.minBy { it.solvedAtMillis } }

        return earliestBySlug.values.mapNotNull { solve ->
            val current = existing[solve.slug]
            when {
                current == null -> Completion(solve.slug, solve.title, solve.slug, CompletionSource.LEETCODE, solve.solvedAtMillis)
                // Self-ticked earlier, now confirmed by LeetCode → upgrade to verified, keep the earlier date.
                current.source == CompletionSource.SELF -> current.copy(
                    slug = solve.slug,
                    source = CompletionSource.LEETCODE,
                    completedAt = minOf(current.completedAt, solve.solvedAtMillis)
                )
                else -> null // already verified
            }
        }.sortedBy { it.completedAt }
    }
}

/**
 * Exponential backoff for LeetCode sync: 15 min, 30 min, 1 h, … capped at 24 h.
 * Stops a broken or rate-limiting API from being hammered by every phone.
 */
object SyncBackoff {
    const val BASE_DELAY_MS = 15 * 60_000L
    const val MAX_DELAY_MS = 24 * 60 * 60_000L

    fun delayAfter(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        val exponent = (consecutiveFailures - 1).coerceAtMost(20)
        return (BASE_DELAY_MS shl exponent).coerceAtMost(MAX_DELAY_MS)
    }

    fun isAllowed(now: Long, nextAllowedAt: Long): Boolean = now >= nextAllowedAt
}

/** What the completion index stores per item. */
data class CompletionEntry(val source: CompletionSource, val completedAt: Long)
