package com.tether.app.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.model.Group
import com.tether.app.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class GroupState {
    object Idle : GroupState()
    object Loading : GroupState()
    data class Success(val group: Group) : GroupState()
    data class Error(val message: String) : GroupState()
}

/** Create / join a group (the list itself lives in GroupListViewModel). */
class GroupViewModel : ViewModel() {

    private val repository = GroupRepository()

    private val _groupState =
        MutableStateFlow<GroupState>(GroupState.Idle)
    val groupState: StateFlow<GroupState> = _groupState

    fun createGroup(
        name: String,
        goalType: String,
        isSolo: Boolean
    ) {
        if (_groupState.value is GroupState.Loading) return
        viewModelScope.launch {
            _groupState.value = GroupState.Loading
            val result = repository.createGroup(
                name, goalType, isSolo)
            _groupState.value = result.fold(
                onSuccess = { GroupState.Success(it) },
                onFailure = { GroupState.Error(it.message ?: "Failed to create group") }
            )
        }
    }

    fun joinGroup(inviteCode: String) {
        if (_groupState.value is GroupState.Loading) return
        viewModelScope.launch {
            _groupState.value = GroupState.Loading
            val result = repository.joinGroup(inviteCode)
            _groupState.value = result.fold(
                onSuccess = { GroupState.Success(it) },
                onFailure = { GroupState.Error(it.message ?: "Failed to join group") }
            )
        }
    }

    /** Called after an error toast has been shown, so it isn't shown again. */
    fun consumeError() {
        if (_groupState.value is GroupState.Error) _groupState.value = GroupState.Idle
    }
}
