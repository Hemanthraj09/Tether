package com.tether.app.data.leetcode

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Client for LeetCode's public GraphQL endpoint (the same API leetcode.com uses).
 *
 * Calls are made from the user's own phone, a few times a day: low volume from
 * real devices, which avoids the server-side bot blocking that breaks most
 * LeetCode integrations. Queries come from assets/leetcode/queries.json (shared
 * with the daily CI canary) unless Remote Config supplies a replacement.
 */
class LeetCodeApi(context: Context) {

    private val appContext = context.applicationContext

    private val bundledQueries: JSONObject by lazy {
        JSONObject(appContext.assets.open("leetcode/queries.json").bufferedReader().use { it.readText() })
    }

    private fun query(name: String): String =
        LeetCodeConfig.queryOverride(name) ?: bundledQueries.getString(name)

    /** Public profile stats; cached in memory for 10 minutes per username. */
    suspend fun profile(username: String, forceRefresh: Boolean = false): LeetCodeProfile {
        val key = username.lowercase()
        if (!forceRefresh) {
            profileCache[key]?.let { (fetchedAt, profile) ->
                if (System.currentTimeMillis() - fetchedAt < CACHE_MS) return profile
            }
        }
        val body = post(query("profile"), JSONObject().put("username", username))
        val profile = LeetCodeParser.parseProfile(body, username)
        profileCache[key] = System.currentTimeMillis() to profile
        return profile
    }

    /** The user's most recent accepted submissions (LeetCode exposes at most 20). */
    suspend fun recentSolves(username: String, limit: Int = 20): List<RecentSolve> {
        val body = post(query("recent"), JSONObject().put("username", username).put("limit", limit))
        return LeetCodeParser.parseRecentSolves(body, username)
    }

    private suspend fun post(query: String, variables: JSONObject): String = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Referer", "https://leetcode.com")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Tether")
        }
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("query", query).put("variables", variables).toString().toByteArray())
            }
            when (val code = connection.responseCode) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                429 -> throw LeetCodeRateLimitedException()
                else -> throw IOException("LeetCode responded with HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://leetcode.com/graphql"
        private const val CACHE_MS = 10 * 60_000L
        private val profileCache = ConcurrentHashMap<String, Pair<Long, LeetCodeProfile>>()
    }
}
