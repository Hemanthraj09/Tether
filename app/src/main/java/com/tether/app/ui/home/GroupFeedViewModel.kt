package com.tether.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.model.Log
import com.tether.app.data.repository.GroupManagementRepository
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.data.repository.LeaderboardRepository
import com.tether.app.data.repository.LogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LogUiState {
    object Idle : LogUiState()
    object Loading : LogUiState()
    object Success : LogUiState()
    data class Error(val message: String) : LogUiState()
}

sealed class GroupActionState {
    object Idle : GroupActionState()
    object Loading : GroupActionState()
    object Success : GroupActionState()
    data class Error(val message: String) : GroupActionState()
}

class GroupFeedViewModel : ViewModel() {

    private val logRepository = LogRepository()
    private val groupManagementRepository = GroupManagementRepository()
    private val leaderboardRepository = LeaderboardRepository()

    private val _feedLogs = MutableStateFlow<List<Log>>(emptyList())
    val feedLogs: StateFlow<List<Log>> = _feedLogs

    private val _memberStats = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val memberStats: StateFlow<List<LeaderboardEntry>> = _memberStats

    private val _logState = MutableStateFlow<LogUiState>(LogUiState.Idle)
    val logState: StateFlow<LogUiState> = _logState

    private val _groupActionState = MutableStateFlow<GroupActionState>(GroupActionState.Idle)
    val groupActionState: StateFlow<GroupActionState> = _groupActionState

    private val _isCreator = MutableStateFlow(false)
    val isCreator: StateFlow<Boolean> = _isCreator

    private var feedJob: Job? = null
    private var statsJob: Job? = null

    fun startListeningToFeed(groupId: String) {
        feedJob?.cancel()
        statsJob?.cancel()

        feedJob = viewModelScope.launch {
            logRepository
                .getTodayLogsForGroup(groupId)
                .collect { logs ->
                    _feedLogs.value = logs
                }
        }

        statsJob = viewModelScope.launch {
            leaderboardRepository
                .getLeaderboardFlow(groupId)
                .collect { stats ->
                    _memberStats.value = stats
                }
        }
    }

    fun checkIfCreator(groupId: String) {
        viewModelScope.launch {
            _isCreator.value = groupManagementRepository.isGroupCreator(groupId)
        }
    }

    fun writeLog(groupId: String, hours: Double, note: String) {
        viewModelScope.launch {
            _logState.value = LogUiState.Loading
            val result = logRepository.writeLog(groupId, hours, note)
            _logState.value = if (result.isSuccess) {
                LogUiState.Success
            } else {
                LogUiState.Error(result.exceptionOrNull()?.message ?: "Failed to log")
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            _groupActionState.value = GroupActionState.Loading
            val result = groupManagementRepository.deleteGroup(groupId)
            _groupActionState.value = if (result.isSuccess)
                GroupActionState.Success
            else GroupActionState.Error(result.exceptionOrNull()?.message ?: "Failed to delete group")
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            _groupActionState.value = GroupActionState.Loading
            val result = groupManagementRepository.leaveGroup(groupId)
            _groupActionState.value = if (result.isSuccess)
                GroupActionState.Success
            else GroupActionState.Error(result.exceptionOrNull()?.message ?: "Failed to leave group")
        }
    }
}
