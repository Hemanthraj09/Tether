package com.tether.app.domain

import com.tether.app.data.leetcode.RecentSolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionsTest {

    private fun solve(slug: String, at: Long, id: String = "$slug-$at") =
        RecentSolve(id, slug.replace('-', ' '), slug, at)

    // ── SolveMerger ──────────────────────────────────────────────

    @Test
    fun `new solves become verified completions`() {
        val writes = SolveMerger.merge(emptyMap(), listOf(solve("two-sum", 100), solve("lru-cache", 200)))
        assertEquals(listOf("two-sum", "lru-cache"), writes.map { it.key })
        assertTrue(writes.all { it.source == CompletionSource.LEETCODE })
    }

    @Test
    fun `already verified solves are not rewritten`() {
        val existing = mapOf("two-sum" to Completion("two-sum", "Two Sum", "two-sum", CompletionSource.LEETCODE, 50))
        assertEquals(emptyList<Completion>(), SolveMerger.merge(existing, listOf(solve("two-sum", 100))))
    }

    @Test
    fun `a self-ticked item is upgraded to verified and keeps the earlier date`() {
        val existing = mapOf("two-sum" to Completion("two-sum", "Two Sum", "two-sum", CompletionSource.SELF, 50))
        val write = SolveMerger.merge(existing, listOf(solve("two-sum", 100))).single()
        assertEquals(CompletionSource.LEETCODE, write.source)
        assertEquals(50, write.completedAt)
    }

    @Test
    fun `repeat solves of one problem count once, at the first solve`() {
        val write = SolveMerger.merge(emptyMap(), listOf(solve("two-sum", 300), solve("two-sum", 100))).single()
        assertEquals(100, write.completedAt)
    }

    @Test
    fun `completion sources round-trip through their ids`() {
        assertEquals(CompletionSource.LEETCODE, CompletionSource.from("leetcode"))
        assertEquals(CompletionSource.SELF, CompletionSource.from("self"))
        assertNull(CompletionSource.from("admin"))
    }

    // ── SyncBackoff ──────────────────────────────────────────────

    @Test
    fun `backoff doubles from 15 minutes and caps at 24 hours`() {
        val min = 60_000L
        assertEquals(0L, SyncBackoff.delayAfter(0))
        assertEquals(15 * min, SyncBackoff.delayAfter(1))
        assertEquals(30 * min, SyncBackoff.delayAfter(2))
        assertEquals(60 * min, SyncBackoff.delayAfter(3))
        assertEquals(SyncBackoff.MAX_DELAY_MS, SyncBackoff.delayAfter(8))
        assertEquals(SyncBackoff.MAX_DELAY_MS, SyncBackoff.delayAfter(1000))
    }

    @Test
    fun `sync is allowed once the backoff window has passed`() {
        assertFalse(SyncBackoff.isAllowed(now = 100, nextAllowedAt = 200))
        assertTrue(SyncBackoff.isAllowed(now = 200, nextAllowedAt = 200))
    }

    // ── LeetCodeUsernames ────────────────────────────────────────

    @Test
    fun `usernames are accepted as handles, @handles or profile links`() {
        assertEquals("hemxnth16", LeetCodeUsernames.normalize("hemxnth16"))
        assertEquals("hemxnth16", LeetCodeUsernames.normalize("  @hemxnth16 "))
        assertEquals("hemxnth16", LeetCodeUsernames.normalize("https://leetcode.com/u/hemxnth16/"))
        assertEquals("Some_User-1", LeetCodeUsernames.normalize("leetcode.com/Some_User-1"))
    }

    @Test
    fun `invalid usernames are rejected`() {
        assertNull(LeetCodeUsernames.normalize(""))
        assertNull(LeetCodeUsernames.normalize("has spaces"))
        assertNull(LeetCodeUsernames.normalize("emoji🔥"))
    }
}
