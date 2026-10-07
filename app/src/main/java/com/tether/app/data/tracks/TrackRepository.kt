package com.tether.app.data.tracks

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads bundled tracks from assets/tracks (parsed once, then cached in memory). */
class TrackRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun get(id: String): Track? = withContext(Dispatchers.IO) {
        cache[id] ?: runCatching {
            val json = appContext.assets.open("tracks/$id.json").bufferedReader().use { it.readText() }
            TrackParser.parse(json)
        }.getOrNull()?.also { cache[id] = it }
    }

    /** Tracks offered to groups with this goal (case-insensitive). */
    suspend fun forGoal(goal: String): List<Track> =
        ALL_IDS.mapNotNull { get(it) }.filter { it.goal.equals(goal, ignoreCase = true) }

    companion object {
        /** Display order in pickers. */
        val ALL_IDS = listOf("neetcode150", "striver-a2z", "risingbrain-patterns")
        private val cache = ConcurrentHashMap<String, Track>()

        fun goalHasTracks(goal: String): Boolean = goal.equals("Coding", ignoreCase = true)
    }
}
