package com.tether.app.data.repository

import android.content.Context
import com.tether.app.domain.SolveCounts
import com.tether.app.utils.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest

/**
 * Per-member coding stats for a Coding group: verified LeetCode solves today and
 * this week. Built from each member's one-document completion index, then
 * refined with viewer-side LeetCode checks (claims LeetCode contradicts are
 * dropped). Emits immediately from Firestore, then again once checks arrive.
 */
class CodingStatsRepository(context: Context) {

    data class MemberStats(
        val solvedToday: Int,
        val solvedWeek: Int,
        val leetcodeUsername: String?,
        val ownershipVerified: Boolean?,
        val unconfirmedCount: Int
    )

    private val appContext = context.applicationContext
    private val members = MemberRepository()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(memberUids: List<String>): Flow<Map<String, MemberStats>> {
        if (memberUids.isEmpty()) return flowOf(emptyMap())
        return combine(
            members.observeMembers(memberUids),
            members.observeCompletions(memberUids),
            DateKeys.todayFlow()
        ) { profiles, indexes, _ -> profiles to indexes }
            .transformLatest { (profiles, indexes) ->
                val handles = profiles.mapNotNull { m -> m.leetcodeUsername?.let { m.uid to it } }.toMap()

                fun build(checks: Map<String, LeetCodeRepository.MemberCheck>) =
                    profiles.associate { m ->
                        val unconfirmed = checks[m.uid]?.unconfirmedKeys.orEmpty()
                        val counts = SolveCounts.count(
                            indexes[m.uid].orEmpty(), unconfirmed,
                            DateKeys.startOfTodayMillis(), DateKeys.startOfWeekMillis()
                        )
                        m.uid to MemberStats(
                            solvedToday = counts.today,
                            solvedWeek = counts.week,
                            leetcodeUsername = m.leetcodeUsername,
                            ownershipVerified = checks[m.uid]?.ownershipVerified,
                            unconfirmedCount = unconfirmed.size
                        )
                    }

                emit(build(emptyMap()))   // instant, from Firestore
                if (handles.isNotEmpty()) {
                    emit(build(LeetCodeRepository(appContext).checkMembers(handles, indexes)))
                }
            }
    }

    companion object {
        /** Groups whose goal is coding get LeetCode-powered stats. */
        fun isCodingGoal(goal: String): Boolean = goal.equals("Coding", ignoreCase = true)
    }
}
