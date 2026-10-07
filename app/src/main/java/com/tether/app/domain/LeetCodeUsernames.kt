package com.tether.app.domain

/** Turns whatever the user typed (handle, @handle, or profile URL) into a LeetCode username. */
object LeetCodeUsernames {

    private val VALID = Regex("[A-Za-z0-9_-]{1,40}")
    private val PROFILE_URL = Regex("""leetcode\.(?:com|cn)/(?:u/)?([A-Za-z0-9_-]+)/?""", RegexOption.IGNORE_CASE)

    /** Returns the username, or null if the input can't be one. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        val candidate = PROFILE_URL.find(trimmed)?.groupValues?.get(1)
            ?: trimmed.removePrefix("@")
        return candidate.takeIf { VALID.matches(it) }
    }
}
