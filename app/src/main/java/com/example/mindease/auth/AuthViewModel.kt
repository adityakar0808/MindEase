package com.example.mindease.auth

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindease.data.models.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        updateCurrentUser()
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _error.value = "Email and Password cannot be empty"
                return@launch
            }
            _isLoading.value = true
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        updateCurrentUser()
                    } else {
                        _error.value = task.exception?.localizedMessage ?: "Login failed"
                    }
                }
        }
    }

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _error.value = "Email and Password cannot be empty"
                return@launch
            }
            _isLoading.value = true
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        updateCurrentUser()
                    } else {
                        _error.value = task.exception?.localizedMessage ?: "Signup failed"
                    }
                }
        }
    }

    fun loginAnonymously() {
        _isLoading.value = true
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    updateCurrentUser()
                } else {
                    _error.value = task.exception?.localizedMessage ?: "Anonymous login failed"
                }
            }
    }

    fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        _isLoading.value = true
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    updateCurrentUser()
                } else {
                    _error.value = task.exception?.localizedMessage ?: "Google login failed"
                }
            }
    }
    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    private fun updateCurrentUser() {
        auth.currentUser?.let {
            _currentUser.value = User(
                uid = it.uid,
                email = it.email ?: "",
                isAnonymous = it.isAnonymous
            )
        }
    }
}
