package com.tether.app.data.leetcode

import org.json.JSONException
import org.json.JSONObject

/**
 * Parses LeetCode GraphQL responses. Pure (org.json only) so it's unit-tested
 * against recorded responses; any shape mismatch becomes a [LeetCodeSchemaException]
 * instead of a crash, which lets sync back off and report it.
 */
object LeetCodeParser {

    fun parseProfile(body: String, requestedUsername: String): LeetCodeProfile = guard {
        val root = JSONObject(body)
        val user = root.optJSONObject("data")?.optJSONObject("matchedUser")
            ?: throw missingOrNotFound(root, requestedUsername, "matchedUser")

        val counts = HashMap<String, Int>()
        val ac = user.getJSONObject("submitStatsGlobal").getJSONArray("acSubmissionNum")
        for (i in 0 until ac.length()) {
            val entry = ac.getJSONObject(i)
            counts[entry.getString("difficulty")] = entry.getInt("count")
        }

        val calendar = user.optJSONObject("userCalendar")
        val topics = ArrayList<TopicCount>()
        user.optJSONObject("tagProblemCounts")?.let { tags ->
            for (level in listOf("fundamental", "intermediate", "advanced")) {
                val list = tags.optJSONArray(level) ?: continue
                for (i in 0 until list.length()) {
                    val tag = list.getJSONObject(i)
                    val solved = tag.optInt("problemsSolved", 0)
                    if (solved > 0) topics.add(TopicCount(tag.getString("tagName"), solved))
                }
            }
        }

        LeetCodeProfile(
            username = user.getString("username"),
            ranking = user.optJSONObject("profile")?.let { p ->
                if (p.isNull("ranking")) null else p.optInt("ranking")
            },
            solvedTotal = counts["All"] ?: 0,
            easy = counts["Easy"] ?: 0,
            medium = counts["Medium"] ?: 0,
            hard = counts["Hard"] ?: 0,
            streak = calendar?.optInt("streak", 0) ?: 0,
            activeDays = calendar?.optInt("totalActiveDays", 0) ?: 0,
            topics = topics.sortedByDescending { it.solved }
        )
    }

    fun parseRecentSolves(body: String, requestedUsername: String): List<RecentSolve> = guard {
        val root = JSONObject(body)
        val data = root.optJSONObject("data")
        if (data == null || !data.has("recentAcSubmissionList")) {
            throw missingOrNotFound(root, requestedUsername, "recentAcSubmissionList")
        }
        if (data.isNull("recentAcSubmissionList")) return@guard emptyList()
        val list = data.getJSONArray("recentAcSubmissionList")
        (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            RecentSolve(
                submissionId = s.getString("id"),
                title = s.getString("title"),
                slug = s.getString("titleSlug"),
                // LeetCode sends seconds as a string
                solvedAtMillis = s.getString("timestamp").toLong() * 1000
            )
        }
    }

    private fun missingOrNotFound(root: JSONObject, username: String, field: String): LeetCodeException {
        val message = firstErrorMessage(root)
        return if (message != null && message.contains("does not exist", ignoreCase = true)) {
            LeetCodeUserNotFoundException(username)
        } else {
            LeetCodeSchemaException(message ?: "missing $field")
        }
    }

    private fun firstErrorMessage(root: JSONObject): String? =
        root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: LeetCodeException) {
        throw e
    } catch (e: JSONException) {
        throw LeetCodeSchemaException(e.message ?: "unexpected JSON")
    } catch (e: NumberFormatException) {
        throw LeetCodeSchemaException("bad number: ${e.message}")
    }
}
