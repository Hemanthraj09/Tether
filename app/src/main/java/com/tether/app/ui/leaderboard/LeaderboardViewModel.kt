package com.tether.app.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.repository.LeaderboardEntry
import com.tether.app.data.repository.LeaderboardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LeaderboardUiState {
    object Loading : LeaderboardUiState()
    data class Success(
        val entries: List<LeaderboardEntry>
    ) : LeaderboardUiState()
    data class Error(
        val message: String
    ) : LeaderboardUiState()
    object Empty : LeaderboardUiState()
}

class LeaderboardViewModel : ViewModel() {

    private val repository = LeaderboardRepository()

    private val _uiState =
        MutableStateFlow<LeaderboardUiState>(
            LeaderboardUiState.Loading)
    val uiState: StateFlow<LeaderboardUiState> =
        _uiState

    private var leaderboardJob: Job? = null

    fun loadLeaderboard(groupId: String) {
        leaderboardJob?.cancel()
        leaderboardJob = viewModelScope.launch {
            _uiState.value = LeaderboardUiState.Loading
            repository.getLeaderboardFlow(groupId).collect { entries ->
                _uiState.value = if (entries.isEmpty()) {
                    LeaderboardUiState.Empty
                } else {
                    LeaderboardUiState.Success(entries)
                }
            }
        }
    }
}
