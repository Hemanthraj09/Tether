package com.tether.app.data.leetcode

import android.content.Context
import com.tether.app.domain.SyncBackoff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sync bookkeeping (last success, failures, backoff), persisted and observable. */
object LeetCodeSyncState {

    data class State(
        val lastSyncAt: Long = 0L,
        val consecutiveFailures: Int = 0,
        val nextAllowedAt: Long = 0L,
        val lastError: String? = null
    )

    private const val PREFS = "tether_leetcode_sync"
    private val _state = MutableStateFlow<State?>(null)

    fun observe(context: Context): StateFlow<State?> {
        if (_state.value == null) _state.value = load(context)
        return _state.asStateFlow()
    }

    fun current(context: Context): State = _state.value ?: load(context).also { _state.value = it }

    fun recordSuccess(context: Context, now: Long) =
        save(context, State(lastSyncAt = now))

    fun recordFailure(context: Context, now: Long, error: String) {
        val failures = current(context).consecutiveFailures + 1
        save(context, current(context).copy(
            consecutiveFailures = failures,
            nextAllowedAt = now + SyncBackoff.delayAfter(failures),
            lastError = error
        ))
    }

    fun reset(context: Context) = save(context, State())

    private fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            lastSyncAt = p.getLong("last_sync_at", 0L),
            consecutiveFailures = p.getInt("failures", 0),
            nextAllowedAt = p.getLong("next_allowed_at", 0L),
            lastError = p.getString("last_error", null)
        )
    }

    private fun save(context: Context, state: State) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_sync_at", state.lastSyncAt)
            .putInt("failures", state.consecutiveFailures)
            .putLong("next_allowed_at", state.nextAllowedAt)
            .putString("last_error", state.lastError)
            .apply()
        _state.value = state
    }
}
