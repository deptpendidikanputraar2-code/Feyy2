package com.example.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreNotificationRepository
import com.example.data.UserNotification
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NotificationUiState {
    data object Loading : NotificationUiState
    data class Success(val items: List<UserNotification>) : NotificationUiState
    data class Error(val message: String) : NotificationUiState
}

class FirestoreNotificationViewModel(
    private val repository: FirestoreNotificationRepository,
    private val currentUserId: String
) : ViewModel() {

    val notificationsState: StateFlow<NotificationUiState> = repository.observeNotifications(currentUserId)
        .map<List<UserNotification>, NotificationUiState> { NotificationUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing notifications", rootError)
            emit(NotificationUiState.Error(rootError.message ?: "Gagal memuat notifikasi Firebase"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NotificationUiState.Loading
        )

    fun pushNotification(
        title: String,
        body: String,
        category: String = "chat",
        channelId: String? = null,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.addNotification(title, body, category, channelId)
            onResult(res)
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            repository.deleteNotification(notificationId)
        }
    }

    fun syncCloudProfile(
        displayName: String,
        mmqaRole: String,
        mmqaUsername: String,
        notificationsEnabled: Boolean,
        lastSyncedSource: String
    ) {
        viewModelScope.launch {
            repository.upsertSyncProfile(
                displayName = displayName,
                mmqaRole = mmqaRole,
                mmqaUsername = mmqaUsername,
                notificationsEnabled = notificationsEnabled,
                lastSyncedSource = lastSyncedSource
            )
        }
    }

    private companion object {
        const val TAG = "FirestoreNotifVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
