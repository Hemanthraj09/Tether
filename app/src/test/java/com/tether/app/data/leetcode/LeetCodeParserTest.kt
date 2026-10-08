package com.tether.app.data.leetcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses recorded (anonymised) LeetCode GraphQL responses from src/test/resources/leetcode. */
class LeetCodeParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("leetcode/$name")!!.readText()

    @Test
    fun `profile stats are parsed`() {
        val p = LeetCodeParser.parseProfile(fixture("profile.json"), "sample_user")
        assertEquals("sample_user", p.username)
        assertEquals(123456, p.ranking)
        assertEquals(120, p.solvedTotal)
        assertEquals(60, p.easy)
        assertEquals(50, p.medium)
        assertEquals(10, p.hard)
        assertEquals(7, p.streak)
        assertEquals(42, p.activeDays)
        assertEquals("Grinding DSA | tether-abc123", p.aboutMe)
    }

    @Test
    fun `a missing bio is an empty string, not "null"`() {
        val body = fixture("profile.json").replace("\"Grinding DSA | tether-abc123\"", "null")
        assertEquals("", LeetCodeParser.parseProfile(body, "sample_user").aboutMe)
    }

    @Test
    fun `topics exclude unsolved ones and are sorted by solves`() {
        val topics = LeetCodeParser.parseProfile(fixture("profile.json"), "sample_user").topics
        assertTrue(topics.isNotEmpty())
        assertTrue(topics.all { it.solved > 0 })
        assertEquals(topics.sortedByDescending { it.solved }, topics)
        assertEquals(40, topics.first().solved)
    }

    @Test(expected = LeetCodeUserNotFoundException::class)
    fun `unknown user is reported as not found`() {
        LeetCodeParser.parseProfile(fixture("profile_not_found.json"), "ghost")
    }

    @Test
    fun `recent solves are parsed with millisecond timestamps`() {
        val solves = LeetCodeParser.parseRecentSolves(fixture("recent.json"), "sample_user")
        assertEquals(5, solves.size)
        assertEquals("minimum-swaps-to-move-zeros-to-end", solves[0].slug)
        assertEquals(1_790_000_000_000L, solves[0].solvedAtMillis)
        assertEquals("1000", solves[0].submissionId)
    }

    @Test(expected = LeetCodeSchemaException::class)
    fun `a renamed field is reported as a schema change, not a crash`() {
        LeetCodeParser.parseRecentSolves(fixture("recent_schema_changed.json"), "sample_user")
    }

    @Test(expected = LeetCodeSchemaException::class)
    fun `malformed JSON is a schema change`() {
        LeetCodeParser.parseProfile("<html>Cloudflare</html>", "sample_user")
    }

    @Test
    fun `null recent list means no solves`() {
        val body = """{"data":{"recentAcSubmissionList":null}}"""
        assertEquals(emptyList<RecentSolve>(), LeetCodeParser.parseRecentSolves(body, "u"))
    }
}
