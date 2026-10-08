package com.tether.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.repository.CompletionRepository
import com.tether.app.data.repository.GroupRepository
import com.tether.app.data.repository.ProfileRepository
import com.tether.app.domain.SaidVsDid
import com.tether.app.domain.SolveCounts
import com.tether.app.data.snapshotFlow
import com.tether.app.domain.StreakCalculator
import com.tether.app.utils.DateKeys
import com.tether.app.utils.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class ProfileUiState(
    val name: String,
    val email: String,
    val initials: String,
    val streak: Int = 0,
    val todayHours: Double = 0.0,
    val groupCount: Int = 0,
    val year: Int = DateKeys.currentYear(),
    val hoursByDate: Map<String, Double> = emptyMap(),
    /** "yyyy-MM-dd" → verified LeetCode solves (for the LeetCode heatmap). */
    val solvesByDate: Map<String, Int> = emptyMap()
)

/** "Said vs. Did": null rows = never asked about focus areas. */
data class SaidVsDidState(
    val asked: Boolean,
    val rows: List<SaidVsDid.AreaProgress>
)

/**
 * Profile data from live listeners (cache first, then server), kept in the
 * ViewModel so switching tabs back to Profile is instant.
 */
class ProfileViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private val uid = user?.uid ?: ""
    private val email = user?.email ?: ""

    /** Shown immediately, before Firestore answers. */
    private val fallbackName = user?.displayName?.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@")

    val initialState = ProfileUiState(
        name = fallbackName,
        email = email,
        initials = Formatters.initials(fallbackName).ifEmpty { "U" }
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ProfileUiState> = if (uid.isEmpty()) {
        flowOf(initialState).stateIn(viewModelScope, SharingStarted.Eagerly, initialState)
    } else {
        val userFlow = firestore.collection("users").document(uid).snapshotFlow()

        val nameFlow = userFlow.map { doc ->
            doc?.getString("name")?.takeIf { it.isNotBlank() } ?: fallbackName
        }.distinctUntilChanged()

        // Membership lives on the groups themselves (not on the public profile).
        val groupIdsFlow = GroupRepository().observeUserGroups()
            .map { groups -> groups.map { it.id } }
            .distinctUntilChanged()

        // One small document per year (users/{uid}/heatmap/{year}) feeds both the
        // heatmap and today's hours, instead of reading every log ever written.
        val hoursByDateFlow = DateKeys.todayFlow()
            .map { it.substring(0, 4) }
            .distinctUntilChanged()
            .flatMapLatest { year ->
                firestore.collection("users").document(uid).collection("heatmap").document(year)
                    .snapshotFlow()
                    .map { doc ->
                        doc?.data.orEmpty().mapNotNull { (date, hours) ->
                            (hours as? Number)?.let { date to it.toDouble() }
                        }.toMap()
                    }
            }
            .flowOn(Dispatchers.Default)

        val streakFlow = groupIdsFlow.flatMapLatest { ids -> bestActiveStreak(ids) }

        // The user's own completion index (1 document) → solves per day.
        val heatmapsFlow = combine(
            hoursByDateFlow,
            CompletionRepository().observe(uid).map { SolveCounts.byDay(it) }
        ) { hours, solves -> hours to solves }

        combine(nameFlow, groupIdsFlow, heatmapsFlow, streakFlow, DateKeys.todayFlow()) {
                name, groupIds, (hoursByDate, solvesByDate), streak, today ->
            ProfileUiState(
                name = name,
                email = email,
                initials = Formatters.initials(name).ifEmpty { "U" },
                streak = streak,
                todayHours = hoursByDate[today] ?: 0.0,
                groupCount = groupIds.size,
                year = today.substring(0, 4).toIntOrNull() ?: DateKeys.currentYear(),
                hoursByDate = hoursByDate,
                solvesByDate = solvesByDate
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState)
    }

    /**
     * What you said matters (focus areas + weekly targets) against the hours
     * you logged this week in groups with a matching goal. One weekly stats
     * document per group, so it's live and costs a handful of reads.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val saidVsDid: StateFlow<SaidVsDidState?> = if (uid.isEmpty()) {
        flowOf<SaidVsDidState?>(null).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    } else {
        val goalsFlow = GroupRepository().observeUserGroups()
            .map { groups -> groups.associate { it.id to it.goalType } }
            .distinctUntilChanged()
        val weekFlow = DateKeys.todayFlow().map { DateKeys.weekKey() }.distinctUntilChanged()
        val weeklyHoursFlow = combine(goalsFlow, weekFlow) { goals, week -> goals.keys to week }
            .flatMapLatest { (groupIds, week) -> myWeeklyHours(groupIds.toList(), week) }

        combine(ProfileRepository().observePlan(), goalsFlow, weeklyHoursFlow) { plan, goals, hours ->
            SaidVsDidState(
                asked = plan.interests != null,
                rows = SaidVsDid.compute(
                    interests = plan.interests.orEmpty(),
                    targets = plan.targets,
                    groupGoals = goals,
                    weeklyHours = hours,
                    dayOfWeek = DateKeys.weekCalendar().get(Calendar.DAY_OF_WEEK)
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    private fun myWeeklyHours(groupIds: List<String>, week: String): Flow<Map<String, Double>> {
        if (groupIds.isEmpty()) return flowOf(emptyMap())
        val flows = groupIds.map { gid ->
            firestore.collection("groupStats").document(gid).collection("weekly").document(week)
                .snapshotFlow()
                .map { doc -> gid to ((doc?.get(uid) as? Number)?.toDouble() ?: 0.0) }
        }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    /**
     * Highest *active* streak across groups. A streak whose last log is older
     * than yesterday is broken and counts as 0 (same rule as the leaderboard).
     */
    private fun bestActiveStreak(groupIds: List<String>): Flow<Int> {
        if (groupIds.isEmpty()) return flowOf(0)
        val flows = groupIds.map { gid ->
            firestore.collection("groupStats").document(gid)
                .collection("streaks").document(uid)
                .snapshotFlow()
                .map { doc ->
                    val streak = doc?.getLong("currentStreak")?.toInt() ?: 0
                    val last = doc?.getString("lastLogDate") ?: ""
                    StreakCalculator.displayed(streak, last, DateKeys.today())
                }
        }
        return combine(flows) { streaks -> streaks.maxOrNull() ?: 0 }
    }
}
