package com.tether.app.domain

import com.tether.app.data.leetcode.RecentSolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeetCodeVerificationTest {

    private fun recent(vararg slugAndTime: Pair<String, Long>) =
        slugAndTime.map { (slug, at) -> RecentSolve("id-$slug", slug, slug, at) }

    // ── Ownership code ───────────────────────────────────────────

    @Test
    fun `ownership code is stable per user and differs between users`() {
        val code = LeetCodeVerification.ownershipCode("uid-alice")
        assertEquals(code, LeetCodeVerification.ownershipCode("uid-alice"))
        assertNotEquals(code, LeetCodeVerification.ownershipCode("uid-bob"))
        assertTrue(Regex("tether-[0-9a-f]{6}").matches(code))
    }

    @Test
    fun `ownership is proven only by the user's own code in the bio`() {
        val code = LeetCodeVerification.ownershipCode("uid-alice")
        assertTrue(LeetCodeVerification.ownsAccount("Grinding DSA | $code", "uid-alice"))
        assertTrue(LeetCodeVerification.ownsAccount(code.uppercase(), "uid-alice"))
        // A squatter can't reuse someone else's handle: the bio holds the owner's code.
        assertFalse(LeetCodeVerification.ownsAccount("Grinding DSA | $code", "uid-mallory"))
        assertFalse(LeetCodeVerification.ownsAccount("", "uid-alice"))
        assertFalse(LeetCodeVerification.ownsAccount(null, "uid-alice"))
    }

    // ── Claim cross-checks ───────────────────────────────────────

    @Test
    fun `claims confirmed by the recent list pass`() {
        val claims = mapOf("two-sum" to 100L, "lru-cache" to 200L)
        val list = recent("two-sum" to 100L, "lru-cache" to 200L)
        assertEquals(emptySet<String>(), LeetCodeVerification.unconfirmedClaims(claims, list, solvedTotal = 2))
    }

    @Test
    fun `more verified claims than total solves are all unconfirmed`() {
        val claims = mapOf("a" to 1L, "b" to 2L, "c" to 3L)
        assertEquals(claims.keys, LeetCodeVerification.unconfirmedClaims(claims, recent("a" to 1L), solvedTotal = 2))
    }

    @Test
    fun `a short recent list is the whole history, so missing claims are fake`() {
        val claims = mapOf("two-sum" to 100L, "median-of-two-sorted-arrays" to 50L)
        val list = recent("two-sum" to 100L)            // fewer than 20 → complete history
        assertEquals(setOf("median-of-two-sorted-arrays"),
            LeetCodeVerification.unconfirmedClaims(claims, list, solvedTotal = 5))
    }

    @Test
    fun `with a full list, only claims inside its time window must appear in it`() {
        val list = (1..20).map { RecentSolve("id$it", "p$it", "p$it", 1_000L + it) } // window starts at 1001
        val claims = mapOf(
            "p5" to 1_005L,          // in the list → fine
            "old-solve" to 10L,      // older than the window → can't be checked, kept
            "fresh-fake" to 1_010L   // inside the window but missing → unconfirmed
        )
        assertEquals(setOf("fresh-fake"), LeetCodeVerification.unconfirmedClaims(claims, list, solvedTotal = 300))
    }

    @Test
    fun `no claims, nothing to flag`() {
        assertEquals(emptySet<String>(), LeetCodeVerification.unconfirmedClaims(emptyMap(), emptyList(), 0))
    }
}
