package com.example.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = Firebase.auth.currentUser
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class UserNotification(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "chat",
    val channelId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserCloudProfile(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val mmqaRole: String = "muallim",
    val mmqaUsername: String = "",
    val notificationsEnabled: Boolean = true,
    val lastSyncedSource: String = "Firebase + Google Sheets",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreNotificationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    fun observeNotifications(userId: String): Flow<List<UserNotification>> = flow {
        val uid = requireUserId()
        check(uid == userId && userId.isNotBlank()) { "UID tidak cocok untuk observasi notifikasi." }
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addNotification(
        title: String,
        body: String,
        category: String = "chat",
        channelId: String? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanBody = body.trim().take(500)
            require(cleanTitle.isNotBlank() && cleanBody.isNotBlank()) { "Judul dan isi notifikasi wajib diisi." }
            val validCat = if (category in listOf("chat", "absensi", "ujian", "keuangan", "system")) category else "chat"
            val docId = "notif_${System.currentTimeMillis()}"
            val docRef = db.collection("users").document(uid).collection("notifications").document(docId)
            docPath = docRef.path
            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("title", cleanTitle)
                put("body", cleanBody)
                put("category", validCat)
                channelId?.take(64)?.takeIf { it.isNotBlank() }?.let { put("channelId", it) }
                put("read", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            require(notificationId.isNotBlank()) { "ID notifikasi wajib ada." }
            val docRef = db.collection("users").document(uid).collection("notifications").document(notificationId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "read" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            require(notificationId.isNotBlank()) { "ID notifikasi wajib ada." }
            val docRef = db.collection("users").document(uid).collection("notifications").document(notificationId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertSyncProfile(
        displayName: String,
        mmqaRole: String,
        mmqaUsername: String,
        notificationsEnabled: Boolean,
        lastSyncedSource: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sync_profiles/main"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("sync_profiles").document("main")
            docPath = docRef.path
            val safeRole = if (mmqaRole in listOf("dewan_harian", "muallim")) mmqaRole else "muallim"
            val safeName = displayName.trim().ifBlank { "Pengguna MMQA" }.take(120)
            val safeUser = mmqaUsername.trim().ifBlank { "guest" }.take(64)
            val safeSource = lastSyncedSource.trim().ifBlank { "Firebase" }.take(64)

            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "mmqaRole" to safeRole,
                        "mmqaUsername" to safeUser,
                        "notificationsEnabled" to notificationsEnabled,
                        "lastSyncedSource" to safeSource,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "mmqaRole" to safeRole,
                        "mmqaUsername" to safeUser,
                        "notificationsEnabled" to notificationsEnabled,
                        "lastSyncedSource" to safeSource,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
