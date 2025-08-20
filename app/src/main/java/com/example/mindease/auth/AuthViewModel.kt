@file:Suppress("DEPRECATION")

package com.example.mindease.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindease.data.models.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    // New state for navigation
    private val _navigateToLogin = MutableStateFlow(false)
    val navigateToLogin: StateFlow<Boolean> = _navigateToLogin

    init {
        listenToCurrentUser()
    }

    // ----------------- SAVE USER TO FIRESTORE -----------------
    private fun saveUserToFirestore(user: User) {
        db.collection("users").document(user.uid)
            .set(user)
            .addOnFailureListener { e -> _error.value = e.message }
    }

    private fun buildUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = if (firebaseUser.isAnonymous) {
                "Friend"
            } else {
                firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "User"
            },
            isAnonymous = firebaseUser.isAnonymous
        )
    }

    private fun fetchUserData(uid: String) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    _currentUser.value = user
                } else {
                    // If user document doesn't exist, create one
                    val newUser = User(uid = uid, email = auth.currentUser?.email ?: "")
                    db.collection("users").document(uid).set(newUser)
                    _currentUser.value = newUser
                }
            }
            .addOnFailureListener { e ->
                _error.value = e.message
            }
    }

    private fun listenToCurrentUser() {
        val firebaseUser = auth.currentUser ?: return
        db.collection("users").document(firebaseUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _error.value = error.message
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    _currentUser.value = user
                }
            }
    }

    // ----------------- AUTH METHODS -----------------
    fun login(email: String, password: String) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && user.isEmailVerified) {
                            fetchUserData(user.uid)
                        } else {
                            auth.signOut()
                            _error.value = "Please verify your email before logging in."
                        }
                    } else {
                        _error.value = task.exception?.message
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message
            }
        }
    }


    fun signup(email: String, password: String, name: String = "User") {
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
                        val firebaseUser = auth.currentUser
                        firebaseUser?.sendEmailVerification() // 🔥 Send verification email
                            ?.addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    val user = buildUser()?.copy(name = name)
                                    // _currentUser.value = user // User should not be set here as they need to verify
                                    user?.let { saveUserToFirestore(it) } // Save user details

                                    _successMessage.value =
                                        "Verification email sent to ${firebaseUser.email}. Please verify before logging in."

                                    auth.signOut() // 🔥 Logout until email verified
                                    _navigateToLogin.value = true // Trigger navigation
                                } else {
                                    _error.value =
                                        verifyTask.exception?.localizedMessage ?: "Failed to send verification email"
                                }
                            }
                    } else {
                        _error.value = task.exception?.localizedMessage ?: "Signup failed"
                    }
                }
        }
    }

    /*fun loginAnonymously() {
        _isLoading.value = true
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    listenToCurrentUser()
                } else {
                    _error.value = task.exception?.localizedMessage ?: "Anonymous login failed"
                }
            }
    }*/

    fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        _isLoading.value = true
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    val user = buildUser()?.copy(
                        name = account.displayName ?: "User",
                        email = account.email ?: auth.currentUser?.email.orEmpty()
                    )
                    _currentUser.value = user
                    user?.let { saveUserToFirestore(it) }
                    listenToCurrentUser()
                } else {
                    _error.value = task.exception?.localizedMessage ?: "Google login failed"
                }
            }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    // ----------------- UPDATE USER NAME -----------------
    fun updateUserName(uid: String, newName: String) {
        _isLoading.value = true
        db.collection("users").document(uid)
            .update("name", newName)
            .addOnSuccessListener {
                _isLoading.value = false
                _successMessage.value = "Name updated successfully ✅"
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _error.value = e.message
            }
    }
    fun giveConsent(sessionId: String, isUserA: Boolean) {
        val field = if (isUserA) "consentA" else "consentB"
        db.collection("call_sessions").document(sessionId)
            .update(field, true)
            .addOnSuccessListener {
                // Check if both consented
                db.collection("call_sessions").document(sessionId).get()
                    .addOnSuccessListener { doc ->
                        val consentA = doc.getBoolean("consentA") ?: false
                        val consentB = doc.getBoolean("consentB") ?: false
                        if (consentA && consentB) {
                            db.collection("call_sessions").document(sessionId)
                                .update("status", "connected")
                            // Create chat document
                            db.collection("chats").document(sessionId).set(
                                mapOf(
                                    "userA_uid" to doc.getString("userA_uid"),
                                    "userB_uid" to doc.getString("userB_uid")
                                )
                            )
                        }
                    }
            }
    }


    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }

    // Method to reset navigation state
    fun onNavigatedToLogin() {
        _navigateToLogin.value = false
    }
}
