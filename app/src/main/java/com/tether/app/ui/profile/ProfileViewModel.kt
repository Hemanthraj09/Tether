package com.tether.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

data class ProfileUiState(
    val name: String,
    val email: String,
    val initials: String,
    val streak: Int = 0,
    val todayHours: Double = 0.0,
    val groupCount: Int = 0,
    val year: Int = DateKeys.currentYear(),
    val hoursByDate: Map<String, Double> = emptyMap()
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

        val groupIdsFlow = userFlow.map { doc ->
            (doc?.get("groupIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        }.distinctUntilChanged()

        // One query feeds both the heatmap and today's hours.
        val hoursByDateFlow = firestore.collection("logs")
            .whereEqualTo("userId", uid)
            .snapshotFlow()
            .map { snapshot ->
                val totals = HashMap<String, Double>()
                snapshot?.documents?.forEach { doc ->
                    val date = doc.getString("date") ?: return@forEach
                    val hours = doc.getDouble("value") ?: 0.0
                    totals[date] = (totals[date] ?: 0.0) + hours
                }
                totals as Map<String, Double>
            }
            .flowOn(Dispatchers.Default)

        val streakFlow = groupIdsFlow.flatMapLatest { ids -> bestActiveStreak(ids) }

        combine(nameFlow, groupIdsFlow, hoursByDateFlow, streakFlow, DateKeys.todayFlow()) {
                name, groupIds, hoursByDate, streak, today ->
            ProfileUiState(
                name = name,
                email = email,
                initials = Formatters.initials(name).ifEmpty { "U" },
                streak = streak,
                todayHours = hoursByDate[today] ?: 0.0,
                groupCount = groupIds.size,
                year = today.substring(0, 4).toIntOrNull() ?: DateKeys.currentYear(),
                hoursByDate = hoursByDate
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState)
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
