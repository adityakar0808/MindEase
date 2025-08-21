package com.example.mindease.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mindease.call.CallViewModel
import com.example.mindease.chat.LocalChatViewModel
import com.google.firebase.firestore.FirebaseFirestore

class SharedViewModelProvider private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: SharedViewModelProvider? = null

        fun getInstance(): SharedViewModelProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedViewModelProvider().also { INSTANCE = it }
            }
        }
    }

    private var _localChatViewModel: LocalChatViewModel? = null
    private var _callViewModel: CallViewModel? = null

    fun getLocalChatViewModel(context: Context): LocalChatViewModel {
        return _localChatViewModel ?: LocalChatViewModel(context).also {
            _localChatViewModel = it
        }
    }

    fun getCallViewModel(context: Context): CallViewModel {
        return _callViewModel ?: CallViewModel(context, FirebaseFirestore.getInstance()).also {
            _callViewModel = it
            // Connect the ViewModels
            _localChatViewModel?.let { localChat ->
                it.setLocalChatViewModel(localChat)
            }
        }
    }

    fun connectViewModels() {
        _callViewModel?.let { call ->
            _localChatViewModel?.let { localChat ->
                call.setLocalChatViewModel(localChat)
            }
        }
    }

    fun clear() {
        _localChatViewModel = null
        _callViewModel = null
    }
}

class SharedCallViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(CallViewModel::class.java) -> {
                SharedViewModelProvider.getInstance().getCallViewModel(context) as T
            }
            modelClass.isAssignableFrom(LocalChatViewModel::class.java) -> {
                SharedViewModelProvider.getInstance().getLocalChatViewModel(context) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
