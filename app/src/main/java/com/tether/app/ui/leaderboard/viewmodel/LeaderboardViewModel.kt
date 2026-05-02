package com.tether.app.ui.leaderboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LeaderboardViewModel : ViewModel() {

    private val _leaderboardUsers = MutableStateFlow<List<User>>(emptyList())
    val leaderboardUsers: StateFlow<List<User>> = _leaderboardUsers

    init {
        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        viewModelScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("users")
                    .orderBy("totalHours", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()
                
                val users = snapshot.toObjects(User::class.java)
                _leaderboardUsers.value = users
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}