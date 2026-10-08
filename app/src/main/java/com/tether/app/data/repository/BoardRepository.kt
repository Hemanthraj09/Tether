package com.tether.app.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.LeaderboardBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The leaderboard a screen shows: hours for every group, plus verified LeetCode
 * solves for Coding groups, ranked by the group's chosen metric.
 */
class BoardRepository(context: Context) {

    private val leaderboard = LeaderboardRepository()
    private val codingStats = CodingStatsRepository(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(groupId: String): Flow<List<LeaderboardEntry>> =
        FirebaseFirestore.getInstance().collection("groups").document(groupId)
            .snapshotFlow()
            .map { it?.toObject(Group::class.java) }
            .filterNotNull()
            .map { g -> Triple(CodingStatsRepository.isCodingGoal(g.goalType), g.metric, g.members) }
            .distinctUntilChanged()
            .flatMapLatest { (isCoding, metric, members) ->
                val hours = leaderboard.observeLeaderboard(groupId)
                if (!isCoding) hours
                else combine(hours, codingStats.observe(members)) { entries, stats ->
                    LeaderboardBuilder.withSolves(entries, stats, rankBySolves = metric == Group.METRIC_SOLVES)
                }
            }
}
