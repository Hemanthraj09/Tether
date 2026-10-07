package com.tether.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.leetcode.LeetCodeProfile
import com.tether.app.data.leetcode.LeetCodeSyncState
import com.tether.app.data.repository.LeetCodeRepository
import com.tether.app.sync.LeetCodeSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the LeetCode row in "Connected accounts". */
sealed class LeetCodeCardState {
    object Loading : LeetCodeCardState()
    object NotConnected : LeetCodeCardState()
    data class Connected(
        val username: String,
        val profile: LeetCodeProfile?,   // null while loading or if LeetCode is unreachable
        val lastSyncAt: Long,
        val syncError: String?
    ) : LeetCodeCardState()
}

class ConnectedAccountsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LeetCodeRepository(app)
    private val refreshTick = MutableStateFlow(0)

    private val _messages = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val messages: Flow<Pair<String, Boolean>> = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileFlow: Flow<Pair<String?, LeetCodeProfile?>> =
        combine(repository.observeUsername(), refreshTick) { username, tick -> username to tick }
            .flatMapLatest { (username, tick) ->
                if (username == null) flowOf<Pair<String?, LeetCodeProfile?>>(null to null)
                else flow<Pair<String?, LeetCodeProfile?>> {
                    emit(username to null)
                    emit(username to repository.profile(username, forceRefresh = tick > 0).getOrNull())
                }
            }

    val leetCode: StateFlow<LeetCodeCardState> =
        combine(profileFlow, LeetCodeSyncState.observe(app)) { (username, profile), sync ->
            if (username == null) LeetCodeCardState.NotConnected
            else LeetCodeCardState.Connected(
                username = username,
                profile = profile,
                lastSyncAt = sync?.lastSyncAt ?: 0L,
                syncError = sync?.lastError.takeIf { (sync?.consecutiveFailures ?: 0) > 0 }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeetCodeCardState.Loading)

    fun connect(input: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            repository.link(input)
                .onSuccess { username ->
                    _messages.trySend("Connected @$username. Syncing your solves…" to false)
                    LeetCodeSyncWorker.syncNow(getApplication(), force = true)
                }
                .onFailure { _messages.trySend((it.message ?: "Couldn't connect") to true) }
            _busy.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.unlink()
                .onSuccess { _messages.trySend("LeetCode disconnected" to false) }
                .onFailure { _messages.trySend((it.message ?: "Couldn't disconnect") to true) }
        }
    }

    fun syncNow() {
        LeetCodeSyncWorker.syncNow(getApplication(), force = true)
        refreshTick.value += 1
        _messages.trySend("Syncing with LeetCode…" to false)
    }
}
