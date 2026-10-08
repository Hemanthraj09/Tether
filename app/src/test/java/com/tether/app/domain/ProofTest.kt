package com.tether.app.domain

import com.tether.app.data.model.Log
import com.tether.app.data.repository.TodayRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofTest {

    @Test
    fun `a log must say what was done`() {
        assertFalse(Proof.isNoteValid(""))
        assertFalse(Proof.isNoteValid("  ok  "))   // trimmed
        assertTrue(Proof.isNoteValid("DSA"))
        assertFalse(Proof.isNoteValid("x".repeat(201)))
    }

    @Test
    fun `photo rules per group mode`() {
        assertTrue(Proof.photoRequired("required", Proof.SOURCE_MANUAL))
        assertFalse(Proof.photoRequired("required", Proof.SOURCE_TIMER)) // the timer measured it
        assertFalse(Proof.photoRequired("optional", Proof.SOURCE_MANUAL))
        assertFalse(Proof.photoAllowed("off"))
        assertTrue(Proof.photoAllowed(null))                              // old groups → optional
        assertEquals(Proof.MODE_OPTIONAL, Proof.normalizeMode("bogus"))
    }

    @Test
    fun `photos are decoded small and scaled to 720 px`() {
        assertEquals(4, Proof.sampleSize(4000, 3000))   // 1000 px wide after decode, still ≥ 720
        assertEquals(1, Proof.sampleSize(800, 600))
        assertEquals(720 to 540, Proof.scaledSize(1000, 750))
        assertEquals(540 to 720, Proof.scaledSize(750, 1000))
        assertEquals(500 to 300, Proof.scaledSize(500, 300))  // never upscaled
    }

    private fun log(id: String, user: String, value: Double, at: Long) =
        Log(id = id, userId = user, groupId = "g", value = value, createdAt = at, note = "did $id")

    @Test
    fun `today's list - real work only, newest first, reactions counted`() {
        val logs = listOf(log("a", "asha", 1.0, 10), log("b", "me", 2.0, 20), log("sys", "rahul", 0.0, 30))
        val reactions = listOf(
            Triple("a", "me", "ok"), Triple("a", "rahul", "doubt"), Triple("a", "asha", "ok"), // own reaction ignored
            Triple("b", "asha", "ok")
        )
        val items = TodayRepository.merge(logs, reactions, me = "me")
        assertEquals(listOf("b", "a"), items.map { it.log.id })
        val a = items.first { it.log.id == "a" }
        assertEquals(1, a.okCount)
        assertEquals(1, a.doubtCount)
        assertEquals("ok", a.myReaction)
        val b = items.first { it.log.id == "b" }
        assertTrue(b.isMine)
        assertNull(b.myReaction)
    }
}
