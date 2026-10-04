package com.tether.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InviteCodesTest {

    @Test
    fun `codes typed with dashes, spaces or lowercase are normalized`() {
        assertEquals("X7Y9Z2", InviteCodes.normalize("x7y9-z2"))
        assertEquals("AB12CD", InviteCodes.normalize("  ab 12 cd "))
    }

    @Test
    fun `generated codes are 6 uppercase alphanumerics`() {
        val random = Random(42)
        repeat(100) {
            val code = InviteCodes.generate(random)
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() || it in 'A'..'Z' })
            assertEquals(code, InviteCodes.normalize(code))
        }
    }
}
