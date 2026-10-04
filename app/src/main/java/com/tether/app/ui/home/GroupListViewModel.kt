package com.tether.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.model.Group
import com.tether.app.data.repository.GroupManagementRepository
import com.tether.app.data.repository.GroupRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupListViewModel : ViewModel() {

    private val managementRepository = GroupManagementRepository()

    /** Live list of the user's groups; null until the first (cached) result arrives. */
    val groups: StateFlow<List<Group>?> = GroupRepository().observeUserGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val messages: Flow<Pair<String, Boolean>> = _messages.receiveAsFlow()

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            val result = managementRepository.deleteGroup(group.id)
            _messages.trySend(
                if (result.isSuccess) "${group.name} deleted." to false
                else "Failed to delete group" to true
            )
        }
    }

    fun leaveGroup(group: Group) {
        viewModelScope.launch {
            val result = managementRepository.leaveGroup(group.id)
            _messages.trySend(
                if (result.isSuccess) "Left ${group.name}." to false
                else "Failed to leave group" to true
            )
        }
    }
}
