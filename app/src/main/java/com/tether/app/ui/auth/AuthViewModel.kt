package com.tether.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tether.app.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _authState =
        MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    val isLoggedIn: Boolean
        get() = repository.isLoggedIn

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.login(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(
                    result.exceptionOrNull()
                        ?.message
                        ?: "Login failed"
                )
            }
        }
    }

    fun signup(
        name: String,
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.signup(
                name, email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success
            } else {
                AuthState.Error(
                    result.exceptionOrNull()
                        ?.message
                        ?: "Signup failed"
                )
            }
        }
    }

    fun googleSignIn(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.googleSignIn(account)
            _authState.value = if (result.isSuccess)
                AuthState.Success
            else
                AuthState.Error(result.exceptionOrNull()?.message ?: "Google Sign-In failed")
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.Idle
    }
}
