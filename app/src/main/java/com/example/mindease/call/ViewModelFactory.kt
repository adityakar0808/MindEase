package com.example.mindease.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore

class CallViewModelFactory(
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CallViewModel::class.java)) {
            return CallViewModel(context, db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
