package com.tether.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import com.tether.app.data.repository.GroupManagementRepository
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.data.repository.LeaderboardRepository
import com.tether.app.data.repository.LogRepository
import com.tether.app.data.repository.LeetCodeRepository
import com.tether.app.data.repository.NudgeRepository
import com.tether.app.data.repository.RaceRepository
import com.tether.app.data.tracks.Track
import com.tether.app.data.tracks.TrackRepository
import com.tether.app.domain.TrackProgress
import com.tether.app.ui.tracks.RaceStanding
import com.tether.app.data.snapshotFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Race card on the group screen. [available] = this group's goal has tracks. */
data class RaceCardState(
    val available: Boolean,
    val isCreator: Boolean,
    val track: Track?,
    val standings: List<RaceStanding>
)

sealed class GroupFeedEvent {
    data class Message(val text: String, val isError: Boolean = false) : GroupFeedEvent()
    object LeftGroup : GroupFeedEvent()
}

/**
 * State for one group's screen. The groupId comes from the navigation
 * arguments, and all data is kept alive while the screen is in the back
 * stack, so returning to it is instant.
 */
class GroupFeedViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app) {

    val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    private val logRepository = LogRepository()
    private val groupManagementRepository = GroupManagementRepository()
    private val leaderboardRepository = LeaderboardRepository()
    private val nudgeRepository = NudgeRepository()

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    /** Live group document (name, members, invite code, creator). */
    val group: StateFlow<Group?> =
        (if (groupId.isEmpty()) kotlinx.coroutines.flow.flowOf(null)
        else FirebaseFirestore.getInstance().collection("groups").document(groupId)
            .snapshotFlow()
            .map { it?.toObject(Group::class.java) })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Members ranked by today's hours; null while the first data is loading. */
    val memberStats: StateFlow<List<LeaderboardEntry>?> =
        (if (groupId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else leaderboardRepository.observeLeaderboard(groupId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isCreator: Boolean
        get() = group.value?.createdBy == currentUid

    // ── Track race (goals with tracks only, e.g. Coding) ─────────

    private val trackRepository = TrackRepository(app)
    private val raceRepository = RaceRepository()

    @OptIn(ExperimentalCoroutinesApi::class)
    val race: StateFlow<RaceCardState?> = group
        .filterNotNull()
        .map { Triple(it.goalType, it.trackId, it.members) to (it.createdBy == currentUid) }
        .distinctUntilChanged()
        .flatMapLatest { (info, creator) ->
            val (goal, trackId, members) = info
            when {
                !TrackRepository.goalHasTracks(goal) -> flowOf(RaceCardState(false, creator, null, emptyList()))
                trackId.isEmpty() -> flowOf(RaceCardState(true, creator, null, emptyList()))
                else -> combine(
                    flow { emit(trackRepository.get(trackId)) },
                    raceRepository.observeMembers(members),
                    raceRepository.observeCompletedKeys(members)
                ) { track, memberInfo, keys ->
                    val standings = if (track == null) emptyList() else {
                        val byUid = memberInfo.associateBy { it.uid }
                        TrackProgress.race(track, keys, members)
                            .mapNotNull { p -> byUid[p.uid]?.let { RaceStanding(it, p, it.uid == currentUid) } }
                    }
                    RaceCardState(true, creator, track, standings)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Members' LeetCode totals, read live from LeetCode for the race card. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val leetCodeTotals: StateFlow<Map<String, Int>> = race
        .map { state -> state?.standings?.mapNotNull { s -> s.member.leetcodeUsername?.let { s.member.uid to it } }.orEmpty() }
        .distinctUntilChanged()
        .mapLatest { handles -> if (handles.isEmpty()) emptyMap() else LeetCodeRepository(getApplication()).totals(handles) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun tracksForGroup(): List<Track> =
        group.value?.goalType?.let { trackRepository.forGoal(it) }.orEmpty()

    fun setTrack(trackId: String) {
        viewModelScope.launch {
            groupManagementRepository.setTrack(groupId, trackId).onFailure {
                _events.trySend(GroupFeedEvent.Message(it.message ?: "Couldn't change the track", isError = true))
            }
        }
    }

    private val _events = Channel<GroupFeedEvent>(Channel.BUFFERED)
    val events: Flow<GroupFeedEvent> = _events.receiveAsFlow()

    /**
     * Logging must finish even if the user leaves the screen right away,
     * so the write runs in a NonCancellable context.
     */
    fun writeLog(targetGroupId: String, hours: Double, note: String) {
        viewModelScope.launch {
            val result = withContext(NonCancellable) {
                logRepository.writeLog(targetGroupId, hours, note)
            }
            if (result.isFailure) {
                _events.trySend(GroupFeedEvent.Message(
                    result.exceptionOrNull()?.message ?: "Failed to log", isError = true))
            }
        }
    }

    fun sendNudge(nudgedUid: String) {
        viewModelScope.launch {
            val result = nudgeRepository.sendNudge(groupId, nudgedUid)
            if (result.isFailure) {
                _events.trySend(GroupFeedEvent.Message(
                    result.exceptionOrNull()?.message ?: "Couldn't send nudge", isError = true))
            }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            val result = groupManagementRepository.deleteGroup(groupId)
            _events.trySend(
                if (result.isSuccess) GroupFeedEvent.LeftGroup
                else GroupFeedEvent.Message(
                    result.exceptionOrNull()?.message ?: "Failed to delete group", isError = true)
            )
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            val result = groupManagementRepository.leaveGroup(groupId)
            _events.trySend(
                if (result.isSuccess) GroupFeedEvent.LeftGroup
                else GroupFeedEvent.Message(
                    result.exceptionOrNull()?.message ?: "Failed to leave group", isError = true)
            )
        }
    }
}
