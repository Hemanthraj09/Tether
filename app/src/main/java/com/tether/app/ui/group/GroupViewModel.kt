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

sealed class UserGroupsState {
    object Loading : UserGroupsState()
    data class Success(
        val groups: List<Group>) : UserGroupsState()
    data class Error(
        val message: String) : UserGroupsState()
}

class GroupViewModel : ViewModel() {

    private val repository = GroupRepository()

    private val _groupState =
        MutableStateFlow<GroupState>(GroupState.Idle)
    val groupState: StateFlow<GroupState> = _groupState

    private val _userGroupsState =
        MutableStateFlow<UserGroupsState>(
            UserGroupsState.Loading)
    val userGroupsState: StateFlow<UserGroupsState> =
        _userGroupsState

    fun createGroup(
        name: String,
        goalType: String,
        isSolo: Boolean
    ) {
        viewModelScope.launch {
            _groupState.value = GroupState.Loading
            val result = repository.createGroup(
                name, goalType, isSolo)
            _groupState.value = if (result.isSuccess) {
                GroupState.Success(result.getOrNull()!!)
            } else {
                GroupState.Error(
                    result.exceptionOrNull()?.message
                        ?: "Failed to create group")
            }
        }
    }

    fun joinGroup(inviteCode: String) {
        viewModelScope.launch {
            _groupState.value = GroupState.Loading
            val result = repository.joinGroup(inviteCode)
            _groupState.value = if (result.isSuccess) {
                GroupState.Success(result.getOrNull()!!)
            } else {
                GroupState.Error(
                    result.exceptionOrNull()?.message
                        ?: "Failed to join group")
            }
        }
    }

    fun loadUserGroups() {
        viewModelScope.launch {
            _userGroupsState.value =
                UserGroupsState.Loading
            val result = repository.getUserGroups()
            _userGroupsState.value =
                if (result.isSuccess) {
                    UserGroupsState.Success(
                        result.getOrNull()!!)
                } else {
                    UserGroupsState.Error(
                        result.exceptionOrNull()?.message
                            ?: "Failed to load groups")
                }
        }
    }
}
