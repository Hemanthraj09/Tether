package com.tether.app.data.tracks

import org.json.JSONObject

/**
 * A track is a structured challenge a group works through together
 * (e.g. a DSA sheet for Coding groups). Bundled as JSON in assets/tracks.
 */
data class Track(
    val id: String,
    val name: String,
    val author: String,
    val sourceUrl: String,
    /** Group goal this track belongs to ("Coding", "Study", …). */
    val goal: String,
    /** How items can be verified automatically ("leetcode"), or "" for manual-only tracks. */
    val verifier: String,
    val sections: List<TrackSection>
) {
    val items: List<TrackItem> by lazy { sections.flatMap { it.items } }
    val itemCount: Int get() = items.size
}

data class TrackSection(val name: String, val items: List<TrackItem>)

data class TrackItem(
    /** Completion key: the LeetCode slug when there is one, else a track-local id. */
    val key: String,
    val title: String,
    val difficulty: String?,
    val slug: String?,
    val url: String?,
    val pattern: String?
) {
    val isVerifiable: Boolean get() = slug != null
    val link: String? get() = slug?.let { "https://leetcode.com/problems/$it/" } ?: url
}

object TrackParser {

    fun parse(json: String): Track {
        val root = JSONObject(json)
        val sections = root.getJSONArray("sections")
        return Track(
            id = root.getString("id"),
            name = root.getString("name"),
            author = root.optString("author"),
            sourceUrl = root.optString("sourceUrl"),
            goal = root.optString("goal"),
            verifier = root.optString("verifier"),
            sections = (0 until sections.length()).map { i ->
                val s = sections.getJSONObject(i)
                val problems = s.getJSONArray("problems")
                TrackSection(
                    name = s.getString("name"),
                    items = (0 until problems.length()).map { j ->
                        val p = problems.getJSONObject(j)
                        TrackItem(
                            key = p.getString("key"),
                            title = p.getString("title"),
                            difficulty = p.optStringOrNull("difficulty"),
                            slug = p.optStringOrNull("slug"),
                            url = p.optStringOrNull("url"),
                            pattern = p.optStringOrNull("pattern")
                        )
                    }
                )
            }
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name).takeIf { it.isNotBlank() } else null
}
