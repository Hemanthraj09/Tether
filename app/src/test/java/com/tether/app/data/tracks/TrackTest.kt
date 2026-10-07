package com.tether.app.data.tracks

import com.tether.app.domain.TrackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrackTest {

    /** Unit tests run with the module dir as working directory. */
    private fun bundled(id: String): Track =
        TrackParser.parse(File("src/main/assets/tracks/$id.json").readText())

    private val sample = TrackParser.parse(
        """
        {"id":"t","name":"Test","author":"Me","sourceUrl":"https://x","goal":"Coding","verifier":"leetcode",
         "sections":[
           {"name":"Arrays","problems":[
             {"key":"two-sum","title":"Two Sum","difficulty":"Easy","slug":"two-sum"},
             {"key":"rb:p1","title":"GFG only","difficulty":null,"url":"https://gfg/p1"}]},
           {"name":"Graphs","problems":[
             {"key":"a2z:dfs","title":"DFS"}]}]}
        """.trimIndent()
    )

    @Test
    fun `items are parsed with optional fields`() {
        assertEquals(3, sample.itemCount)
        val (twoSum, gfg, dfs) = sample.items
        assertEquals("https://leetcode.com/problems/two-sum/", twoSum.link)
        assertTrue(twoSum.isVerifiable)
        assertEquals("https://gfg/p1", gfg.link)
        assertNull(gfg.difficulty)
        assertNull(dfs.link)
    }

    @Test
    fun `bundled tracks parse with the expected sizes`() {
        val nc = bundled("neetcode150")
        assertEquals(150, nc.itemCount)
        assertEquals(150, nc.items.count { it.isVerifiable })
        assertEquals(18, nc.sections.size)

        val a2z = bundled("striver-a2z")
        assertEquals(448, a2z.itemCount)
        assertEquals(288, a2z.items.count { it.isVerifiable })

        val rb = bundled("risingbrain-patterns")
        assertEquals(385, rb.itemCount)
        assertEquals(317, rb.items.count { it.isVerifiable })
    }

    @Test
    fun `bundled tracks are tagged for Coding groups and have valid keys`() {
        for (id in TrackRepository.ALL_IDS) {
            val track = bundled(id)
            assertEquals(id, track.id)
            assertEquals("Coding", track.goal)
            assertTrue(track.items.all { Regex("[a-z0-9:_-]+").matches(it.key) })
            // LeetCode items are keyed by slug so synced solves tick them off.
            assertTrue(track.items.filter { it.isVerifiable }.all { it.key == it.slug })
        }
    }

    @Test
    fun `progress counts completed items per section and overall`() {
        val done = setOf("two-sum", "a2z:dfs", "not-in-track")
        assertEquals(2, TrackProgress.done(sample, done))
        assertEquals(
            listOf(TrackProgress.SectionProgress("Arrays", 1, 2), TrackProgress.SectionProgress("Graphs", 1, 1)),
            TrackProgress.sections(sample, done)
        )
    }

    @Test
    fun `race ranks members by items done, ties keep group order`() {
        val race = TrackProgress.race(
            sample,
            mapOf("a" to setOf("two-sum"), "b" to setOf("two-sum", "a2z:dfs"), "c" to setOf("rb:p1")),
            memberOrder = listOf("a", "b", "c", "d")
        )
        assertEquals(listOf("b", "a", "c", "d"), race.map { it.uid })
        assertEquals(listOf(2, 1, 1, 0), race.map { it.done })
        assertEquals(2f / 3f, race.first().fraction, 0.0001f)
    }

    @Test
    fun `only goals with tracks show races`() {
        assertTrue(TrackRepository.goalHasTracks("Coding"))
        assertTrue(TrackRepository.goalHasTracks("coding"))
        assertTrue(!TrackRepository.goalHasTracks("Gym"))
        assertTrue(!TrackRepository.goalHasTracks("Study"))
    }
}
