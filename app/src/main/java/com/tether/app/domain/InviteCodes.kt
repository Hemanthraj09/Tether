package com.tether.app.domain

import kotlin.random.Random

object InviteCodes {

    const val LENGTH = 6
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** Accepts codes typed with spaces, dashes or lowercase ("x7y9-z2" → "X7Y9Z2"). */
    fun normalize(input: String): String =
        input.uppercase().filter { it in ALPHABET }

    fun generate(random: Random = Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}
