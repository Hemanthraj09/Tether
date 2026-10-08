package com.tether.app.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tether.app.data.model.Group
import com.tether.app.data.repository.BoardRepository
import com.tether.app.data.repository.CodingStatsRepository
import com.tether.app.data.repository.GroupManagementRepository
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.data.repository.LeaderboardRepository
import com.tether.app.data.repository.LogRepository
import com.tether.app.data.repository.NudgeRepository
import com.tether.app.data.repository.ProofRepository
import com.tether.app.data.repository.TodayLog
import com.tether.app.data.repository.TodayRepository
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
    private val todayRepository = TodayRepository()
    private val proofRepository = ProofRepository()

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    /** Live group document (name, members, invite code, creator). */
    val group: StateFlow<Group?> =
        (if (groupId.isEmpty()) kotlinx.coroutines.flow.flowOf(null)
        else FirebaseFirestore.getInstance().collection("groups").document(groupId)
            .snapshotFlow()
            .map { it?.toObject(Group::class.java) })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Members ranked by today's hours, or by verified LeetCode solves in Coding
     * groups that chose that metric; null while the first data is loading.
     */
    val memberStats: StateFlow<List<LeaderboardEntry>?> =
        (if (groupId.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else BoardRepository(app).observe(groupId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isCreator: Boolean
        get() = group.value?.createdBy == currentUid

    val isCodingGroup: Boolean
        get() = group.value?.goalType?.let { CodingStatsRepository.isCodingGoal(it) } == true

    /** Today's logs in this group (what was done, photo, reactions), newest first. */
    val todayLogs: StateFlow<List<TodayLog>?> =
        (if (groupId.isEmpty()) flowOf(emptyList()) else todayRepository.observe(groupId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Photos are same-day only: clear my earlier ones from this group.
        if (groupId.isNotEmpty()) viewModelScope.launch { proofRepository.deleteMyOldProofs(groupId) }
    }

    suspend fun loadPhoto(logId: String): Bitmap? = proofRepository.load(groupId, logId)

    /** ✓ / 🤨 on a friend's log; null removes your reaction. */
    fun react(item: TodayLog, kind: String?) {
        viewModelScope.launch {
            todayRepository.react(groupId, item.log, kind).onFailure {
                _events.trySend(GroupFeedEvent.Message(it.message ?: "Couldn't react", isError = true))
            }
        }
    }

    /** Creator only (enforced by the security rules). */
    fun setProofMode(mode: String) {
        viewModelScope.launch {
            groupManagementRepository.setProofMode(groupId, mode).onFailure {
                _events.trySend(GroupFeedEvent.Message(it.message ?: "Couldn't change photo proof", isError = true))
            }
        }
    }

    /** Creator only (enforced by the security rules). */
    fun setMetric(metric: String) {
        viewModelScope.launch {
            groupManagementRepository.setMetric(groupId, metric).onFailure {
                _events.trySend(GroupFeedEvent.Message(it.message ?: "Couldn't change the leaderboard", isError = true))
            }
        }
    }

    private val _events = Channel<GroupFeedEvent>(Channel.BUFFERED)
    val events: Flow<GroupFeedEvent> = _events.receiveAsFlow()

    /**
     * Logging must finish even if the user leaves the screen right away,
     * so the write runs in a NonCancellable context.
     */
    fun writeLog(
        targetGroupId: String,
        hours: Double,
        note: String,
        source: String,
        photo: ByteArray? = null
    ) {
        viewModelScope.launch {
            val result = withContext(NonCancellable) {
                logRepository.writeLog(targetGroupId, hours, note, source, photo)
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
