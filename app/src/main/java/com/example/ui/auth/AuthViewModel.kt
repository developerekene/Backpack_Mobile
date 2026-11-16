package com.example.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.BackpackRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: UserProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackpackRepository(application)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _sessionToken = MutableStateFlow(System.currentTimeMillis())
    val sessionToken: StateFlow<Long> = _sessionToken

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        checkCurrentUser()
    }

    fun checkCurrentUser() {
        val firebaseUser = Firebase.auth.currentUser
        if (firebaseUser != null) {
            _authState.value = AuthState.Loading
            viewModelScope.launch {
                try {
                    val profile = repository.fetchUserProfileOnce(firebaseUser.uid)
                    _sessionToken.value = System.currentTimeMillis()
                    _authState.value = AuthState.Authenticated(profile)
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Failed to restore user session profile", e)
                    val fallbackProfile = UserProfile(
                        userId = firebaseUser.uid,
                        name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Backpack User",
                        displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Backpack User",
                        email = firebaseUser.email ?: "user@backpack.edu",
                        role = "Student",
                        logoUrl = firebaseUser.photoUrl?.toString()
                    )
                    _sessionToken.value = System.currentTimeMillis()
                    _authState.value = AuthState.Authenticated(fallbackProfile)
                }
            }
        } else {
            _authState.value = AuthState.Idle
        }
    }

    fun signInWithEmailPassword(
        emailInput: String,
        passwordInput: String,
        onSuccess: () -> Unit
    ) {
        val trimmedEmail = emailInput.trim()
        if (trimmedEmail.isBlank() || passwordInput.isBlank()) {
            _errorMessage.value = "Please enter your email and password."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                val authResult = Firebase.auth.signInWithEmailAndPassword(trimmedEmail, passwordInput).await()
                val user = authResult.user ?: throw IllegalStateException("Authentication failed: No user returned.")

                // Crucial: Fetch the user's authentic profile & role directly from Firestore!
                // NEVER overwrite their role on sign-in!
                val authenticProfile = repository.fetchUserProfileOnce(user.uid)
                _sessionToken.value = System.currentTimeMillis()
                _authState.value = AuthState.Authenticated(authenticProfile)
                _isSubmitting.value = false
                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign in error", e)
                _errorMessage.value = e.localizedMessage ?: "Invalid email or password. Please verify your credentials."
                _isSubmitting.value = false
            }
        }
    }

    fun onAuthSuccess(uid: String, name: String?, email: String?, photoUrl: String?, role: String = "Student") {
        viewModelScope.launch {
            _isSubmitting.value = true
            val existingProfile = repository.fetchUserProfileOnce(uid)
            _sessionToken.value = System.currentTimeMillis()
            if (existingProfile.name.isNotBlank() && existingProfile.role.isNotBlank()) {
                // Keep the exact role and data from Firestore!
                _authState.value = AuthState.Authenticated(existingProfile)
            } else {
                val newProfile = UserProfile(
                    userId = uid,
                    name = name ?: "Backpack User",
                    displayName = name ?: "Backpack User",
                    email = email ?: "user@backpack.edu",
                    role = role,
                    logoUrl = photoUrl
                )
                _authState.value = AuthState.Authenticated(newProfile)
            }
            _isSubmitting.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun signOut() {
        try {
            Firebase.auth.signOut()
        } catch (_: Exception) {}
        _sessionToken.value = System.currentTimeMillis()
        _authState.value = AuthState.Idle
    }
}
