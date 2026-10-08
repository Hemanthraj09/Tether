package com.tether.app.domain

import com.tether.app.data.leetcode.RecentSolve
import java.security.MessageDigest

/**
 * Trust checks for LeetCode data that work with no server: every viewer can run
 * them against LeetCode's public data.
 *
 * 1. Ownership: a user proves a handle is theirs by putting a short code, derived
 *    from their Tether user id, in their LeetCode bio. Anyone can recompute the
 *    code and check the public bio, so a squatter can't fake it.
 * 2. Claims: "verified" completions are written by the user's own device, so a
 *    viewer cross-checks them against LeetCode's public recent-solves list and
 *    total. Claims LeetCode contradicts are shown as unconfirmed.
 */
object LeetCodeVerification {

    private const val PREFIX = "tether-"

    fun ownershipCode(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        return PREFIX + digest.take(3).joinToString("") { "%02x".format(it) }
    }

    fun ownsAccount(aboutMe: String?, uid: String): Boolean =
        aboutMe?.contains(ownershipCode(uid), ignoreCase = true) == true

    /**
     * @param claims verified-source completions: key (LeetCode slug) → completedAt
     * @param recent the member's public recent accepted submissions
     * @param solvedTotal the member's public total solved count
     * @param recentLimit how many recent submissions LeetCode returns at most
     * @return keys LeetCode's public data contradicts
     */
    fun unconfirmedClaims(
        claims: Map<String, Long>,
        recent: List<RecentSolve>,
        solvedTotal: Int,
        recentLimit: Int = 20
    ): Set<String> {
        if (claims.isEmpty()) return emptySet()
        // More verified solves than LeetCode says were ever solved: none can be trusted.
        if (claims.size > solvedTotal) return claims.keys

        val recentSlugs = recent.map { it.slug }.toSet()
        // Fewer than the limit means the list is the member's entire history.
        if (recent.size < recentLimit) return claims.keys - recentSlugs

        // Otherwise anything claimed inside the window the list covers must be in it.
        val windowStart = recent.minOf { it.solvedAtMillis }
        return claims.filter { (key, at) -> at >= windowStart && key !in recentSlugs }.keys
    }
}
