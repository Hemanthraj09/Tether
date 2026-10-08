package com.tether.app.ui.leaderboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.model.Group
import com.tether.app.data.repository.BoardRepository
import com.tether.app.data.repository.GroupRepository
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.data.repository.NudgeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LeaderboardViewModel(app: Application) : AndroidViewModel(app) {

    private val nudgeRepository = NudgeRepository()

    val groups: StateFlow<List<Group>?> = GroupRepository().observeUserGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedGroupId = MutableStateFlow<String?>(null)

    /** The chosen group, falling back to the first one (as before). */
    val currentGroup: StateFlow<Group?> = combine(groups, selectedGroupId) { list, selected ->
        list?.firstOrNull { it.id == selected } ?: list?.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _weeklyMode = MutableStateFlow(false) // Today is the default view
    val weeklyMode: StateFlow<Boolean> = _weeklyMode.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<LeaderboardEntry>?> = currentGroup
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { groupId ->
            if (groupId == null) flowOf(emptyList())
            else BoardRepository(app).observe(groupId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun selectGroup(groupId: String) {
        selectedGroupId.value = groupId
    }

    fun setWeeklyMode(weekly: Boolean) {
        _weeklyMode.value = weekly
    }

    fun sendNudge(nudgedUid: String) {
        val groupId = currentGroup.value?.id ?: return
        viewModelScope.launch {
            val result = nudgeRepository.sendNudge(groupId, nudgedUid)
            if (result.isFailure) {
                _messages.trySend(result.exceptionOrNull()?.message ?: "Couldn't send nudge")
            }
        }
    }
}
