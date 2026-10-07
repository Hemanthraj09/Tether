package com.tether.app.data.leetcode

/** Public LeetCode profile stats (read live from LeetCode, never trusted from Firestore). */
data class LeetCodeProfile(
    val username: String,
    val ranking: Int?,
    val solvedTotal: Int,
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val streak: Int,
    val activeDays: Int,
    /** Topics with at least one solve, most-solved first. */
    val topics: List<TopicCount>
)

data class TopicCount(val name: String, val solved: Int)

/** One accepted submission from LeetCode's public "recent AC" list. */
data class RecentSolve(
    val submissionId: String,
    val title: String,
    val slug: String,
    val solvedAtMillis: Long
)

open class LeetCodeException(message: String) : Exception(message)

class LeetCodeUserNotFoundException(username: String) :
    LeetCodeException("No LeetCode user named \"$username\"")

class LeetCodeRateLimitedException :
    LeetCodeException("LeetCode is busy right now. We'll retry later.")

/** The response no longer has the shape we expect: a query fix via Remote Config is needed. */
class LeetCodeSchemaException(detail: String) :
    LeetCodeException("LeetCode changed its response format ($detail)")
