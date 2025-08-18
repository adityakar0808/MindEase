package com.example.mindease.data.models

data class User(
    val uid: String = "",
    val email: String? = null,
    val name: String? = null,   // ✅ Added
    val isAnonymous: Boolean = false
)
