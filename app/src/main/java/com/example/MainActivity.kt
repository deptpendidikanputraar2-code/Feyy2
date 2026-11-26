package com.example

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ChatAttachmentDraft
import com.example.data.FirestoreNotificationRepository
import com.example.ui.MmqaAppShell
import com.example.ui.MmqaViewModel
import com.example.ui.ToastMessage
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutGoogleAndFirebase
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.NotificationInboxDialog
import com.example.ui.notifications.FirestoreNotificationViewModel
import com.example.ui.notifications.MmqaLocalNotifier
import com.example.ui.notifications.NotificationUiState
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MmqaAr2Theme {
                val vm: MmqaViewModel = viewModel()
                MmqaRootApp(viewModel = vm)
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri, fallback: String): String {
    var fileName = fallback
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIdx >= 0) {
            fileName = cursor.getString(nameIdx) ?: fallback
        }
    }
    return fileName
}

private fun compressImageUriToBase64(context: Context, uri: Uri, maxDim: Int, quality: Int): Pair<ByteArray, String> {
    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Gagal membaca gambar.")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        rawBytes, 0, rawBytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: throw IllegalStateException("Format gambar tidak didukung.")

    val maxSide = max(decoded.width, decoded.height)
    val scaled = if (maxSide > maxDim) {
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    } else decoded

    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    if (scaled !== decoded) decoded.recycle()
    scaled.recycle()
    val outBytes = baos.toByteArray()
    val b64 = Base64.encodeToString(outBytes, Base64.NO_WRAP)
    return outBytes to b64
}

@Composable
private fun MmqaRootApp(viewModel: MmqaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val firebaseUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val db by viewModel.database.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val supabaseStatus by viewModel.supabaseStatus.collectAsStateWithLifecycle()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var draftAvatarBase64 by remember { mutableStateOf("") }
    var showNotifDialog by remember { mutableStateOf(false) }
    var activeToast by remember { mutableStateOf<ToastMessage?>(null) }
    var requestedNotifPermThisSession by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(MmqaLocalNotifier.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || MmqaLocalNotifier.hasNotificationPermission(context)
        if (granted) {
            viewModel.showToast("Izin notifikasi Android aktif!")
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            MmqaLocalNotifier.ensureChannel(context)
        }
        delay(450L)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    LaunchedEffect(firebaseUser?.uid) {
        if (firebaseUser != null &&
            !requestedNotifPermThisSession &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MmqaLocalNotifier.hasNotificationPermission(context)
        ) {
            requestedNotifPermThisSession = true
            delay(500L)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Zero-permission Pickers & Document Savers
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }
    var pendingPdfCallback by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    var pendingChatAttachCallback by remember { mutableStateOf<((ChatAttachmentDraft) -> Unit)?>(null) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val content = pendingCsvContent
        pendingCsvContent = null
        if (uri != null && content != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                            writer.write(content)
                        }
                    }
                }.onSuccess {
                    viewModel.showToast("File CSV berhasil diekspor!")
                }.onFailure {
                    viewModel.showToast("Gagal mengekspor CSV: ${it.message}", isError = true)
                }
            }
        }
    }

    val saveAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (uri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                }.onSuccess {
                    viewModel.showToast("Lampiran berhasil disimpan ke perangkat!")
                }.onFailure {
                    viewModel.showToast("Gagal menyimpan lampiran: ${it.message}", isError = true)
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val callback = pendingPdfCallback
        pendingPdfCallback = null
        if (uri != null && callback != null) {
            scope.launch(Dispatchers.IO) {
                val loaded = runCatching {
                    val fileName = queryDisplayName(context, uri, "template_surat.pdf")
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.size > 3_500_000) {
                        throw IllegalArgumentException("Ukuran PDF terlalu besar (maks 3,5 MB).")
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    fileName to "data:application/pdf;base64,$b64"
                }
                withContext(Dispatchers.Main) {
                    loaded.onSuccess { (name, dataUrl) ->
                        callback(name, dataUrl)
                        viewModel.showToast("Template PDF $name dimuat!")
                    }.onFailure {
                        viewModel.showToast(it.message ?: "Gagal membaca file PDF", isError = true)
                    }
                }
            }
        }
    }

    // Zero-permission Photo Picker for Profile Avatar
    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val res = runCatching {
                    val (_, b64) = compressImageUriToBase64(context, uri, maxDim = 256, quality = 80)
                    "data:image/jpeg;base64,$b64"
                }
                withContext(Dispatchers.Main) {
                    res.onSuccess { dataUrl ->
                        draftAvatarBase64 = dataUrl
                        viewModel.showToast("Foto profil dipilih. Ketuk Simpan Profil untuk menerapkan.")
                    }.onFailure {
                        viewModel.showToast(it.message ?: "Gagal memuat foto profil", isError = true)
                    }
                }
            }
        }
    }

    // Zero-permission Photo Picker for Chat Image Attachment
    val chatPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val cb = pendingChatAttachCallback
        pendingChatAttachCallback = null
        if (uri != null && cb != null) {
            scope.launch(Dispatchers.IO) {
                val res = runCatching {
                    val fileName = queryDisplayName(context, uri, "foto_majelis.jpg")
                    val (outBytes, b64) = compressImageUriToBase64(context, uri, maxDim = 1024, quality = 78)
                    ChatAttachmentDraft(
                        fileName = if (fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true)) fileName else "$fileName.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = outBytes.size.toLong(),
                        dataUrl = "data:image/jpeg;base64,$b64"
                    )
                }
                withContext(Dispatchers.Main) {
                    res.onSuccess { draft -> cb(draft) }
                        .onFailure { viewModel.showToast(it.message ?: "Gagal memuat foto", isError = true) }
                }
            }
        }
    }

    // Zero-permission OpenDocument for Chat PDF / Word / Excel / General Documents
    val chatDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val cb = pendingChatAttachCallback
        pendingChatAttachCallback = null
        if (uri != null && cb != null) {
            scope.launch(Dispatchers.IO) {
                val res = runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileName = queryDisplayName(context, uri, "dokumen_majelis.pdf")
                    if (mime.startsWith("image/")) {
                        val (outBytes, b64) = compressImageUriToBase64(context, uri, maxDim = 1024, quality = 78)
                        ChatAttachmentDraft(
                            fileName = fileName,
                            mimeType = "image/jpeg",
                            sizeBytes = outBytes.size.toLong(),
                            dataUrl = "data:image/jpeg;base64,$b64"
                        )
                    } else {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        if (bytes.isEmpty()) throw IllegalArgumentException("File kosong atau tidak terbaca.")
                        if (bytes.size > 2_500_000) {
                            throw IllegalArgumentException("Ukuran file maksimal 2,5 MB agar sinkronisasi multi-user tetap cepat.")
                        }
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        ChatAttachmentDraft(
                            fileName = fileName,
                            mimeType = mime,
                            sizeBytes = bytes.size.toLong(),
                            dataUrl = "data:$mime;base64,$b64"
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    res.onSuccess { draft -> cb(draft) }
                        .onFailure { viewModel.showToast(it.message ?: "Gagal membaca file lampiran", isError = true) }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.toastFlow.collect { msg ->
            activeToast = msg
        }
    }

    LaunchedEffect(activeToast?.id) {
        if (activeToast != null) {
            delay(3200L)
            activeToast = null
        }
    }

    val handleGoogleSignOut: () -> Unit = {
        showNotifDialog = false
        showEditProfileDialog = false
        signOutGoogleAndFirebase(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = {
                viewModel.logout()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val activeFirebaseUser = firebaseUser
        if (activeFirebaseUser == null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 840.dp
                LoginScreen(
                    db = db,
                    syncState = syncState,
                    loginError = uiState.loginError,
                    isWideScreen = isWide,
                    firebaseUser = null,
                    onGoogleSignInSuccess = {
                        viewModel.showToast("Akun Google berhasil terhubung!")
                    },
                    onGoogleSignInError = { err ->
                        viewModel.showToast(err, isError = true)
                    },
                    onGoogleSignOut = handleGoogleSignOut,
                    onLogin = { u, p -> viewModel.login(u, p) }
                )
            }
        } else {
            key(activeFirebaseUser.uid) {
                val notifVm: FirestoreNotificationViewModel = viewModel(
                    key = activeFirebaseUser.uid,
                    factory = viewModelFactory {
                        initializer {
                            FirestoreNotificationViewModel(
                                repository = FirestoreNotificationRepository(context.applicationContext),
                                currentUserId = activeFirebaseUser.uid
                            )
                        }
                    }
                )
                val notificationsState by notifVm.notificationsState.collectAsStateWithLifecycle()
                val unreadCount = remember(notificationsState) {
                    (notificationsState as? NotificationUiState.Success)?.items?.count { !it.read } ?: 0
                }

                LaunchedEffect(activeFirebaseUser.uid, viewModel) {
                    viewModel.incomingChatAlerts.collect { newMessages ->
                        val curMmqaUserId = viewModel.uiState.value.currentUser?.id
                        newMessages
                            .filter { it.senderId != curMmqaUserId }
                            .takeLast(3)
                            .forEach { msg ->
                                val title = "💬 ${msg.senderName}"
                                val body = when {
                                    msg.text.isNotBlank() && msg.attachmentName.isNotBlank() ->
                                        "${msg.text} (📎 ${msg.attachmentName})"
                                    msg.attachmentName.isNotBlank() -> "📎 Mengirim file: ${msg.attachmentName}"
                                    else -> msg.text
                                }.take(240)
                                MmqaLocalNotifier.showNotification(context, title, body)
                                notifVm.pushNotification(
                                    title = title,
                                    body = body,
                                    category = "chat",
                                    channelId = msg.channelId
                                )
                            }
                    }
                }

                // Sync user's cloud profile whenever logged in or display name changes
                LaunchedEffect(activeFirebaseUser.uid, uiState.currentUser?.id, uiState.currentUser?.name) {
                    val mmqaUser = uiState.currentUser
                    if (mmqaUser != null) {
                        notifVm.syncCloudProfile(
                            displayName = mmqaUser.name,
                            mmqaRole = mmqaUser.role,
                            mmqaUsername = mmqaUser.username,
                            notificationsEnabled = hasNotificationPermission,
                            lastSyncedSource = if (supabaseStatus.configured) "Cloud" else "Lokal"
                        )
                    }
                }

                if (uiState.currentUser == null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWide = maxWidth >= 840.dp
                        LoginScreen(
                            db = db,
                            syncState = syncState,
                            loginError = uiState.loginError,
                            isWideScreen = isWide,
                            firebaseUser = activeFirebaseUser,
                            onGoogleSignInSuccess = { viewModel.showToast("Akun Google aktif!") },
                            onGoogleSignInError = { err -> viewModel.showToast(err, isError = true) },
                            onGoogleSignOut = handleGoogleSignOut,
                            onLogin = { u, p -> viewModel.login(u, p) }
                        )
                    }
                } else {
                    MmqaAppShell(
                        db = db,
                        uiState = uiState,
                        syncState = syncState,
                        viewModel = viewModel,
                        firebaseUser = activeFirebaseUser,
                        unreadNotificationCount = unreadCount,
                        onOpenNotifications = { showNotifDialog = true },
                        onOpenEditProfile = {
                            draftAvatarBase64 = uiState.currentUser?.avatarBase64 ?: ""
                            showEditProfileDialog = true
                        },
                        onGoogleSignOut = handleGoogleSignOut,
                        onExportCsv = { filename, csvContent ->
                            pendingCsvContent = csvContent
                            csvLauncher.launch(filename)
                        },
                        onPickPdfTemplate = { callback ->
                            pendingPdfCallback = callback
                            pdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        onPickChatDocument = { callback ->
                            pendingChatAttachCallback = callback
                            chatDocumentPicker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-powerpoint",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "text/*",
                                    "application/zip",
                                    "*/*"
                                )
                            )
                        },
                        onPickChatPhoto = { callback ->
                            pendingChatAttachCallback = callback
                            chatPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSaveChatAttachment = { fileName, _, dataUrl ->
                            val pureB64 = dataUrl.substringAfter("base64,", dataUrl).trim()
                            runCatching { Base64.decode(pureB64, Base64.DEFAULT) }
                                .onSuccess { bytes ->
                                    pendingSaveBytes = bytes
                                    saveAttachmentLauncher.launch(fileName.ifBlank { "lampiran_mmqa" })
                                }
                                .onFailure {
                                    viewModel.showToast("Lampiran tidak valid untuk diunduh.", isError = true)
                                }
                        }
                    )
                }

                if (showNotifDialog) {
                    NotificationInboxDialog(
                        notificationsState = notificationsState,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onMarkRead = { id -> notifVm.markRead(id) },
                        onDelete = { id -> notifVm.deleteNotification(id) },
                        onDismiss = { showNotifDialog = false }
                    )
                }
            }
        }

        // Floating Toast Banner
        AnimatedVisibility(
            visible = activeToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            activeToast?.let { toast ->
                Surface(
                    color = if (toast.isError) Rose700 else Emerald900,
                    border = BorderStroke(1.dp, if (toast.isError) Rose200 else Amber400.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = toast.text,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        val loggedInUser = uiState.currentUser
        if (showEditProfileDialog && loggedInUser != null) {
            EditProfileDialog(
                user = loggedInUser,
                ruleSummary = viewModel.getPasswordRuleSummary(),
                draftAvatarBase64 = draftAvatarBase64,
                onPickAvatarPhoto = {
                    profilePhotoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearAvatar = { draftAvatarBase64 = "" },
                onSaveProfile = { newName, newAvatar ->
                    viewModel.updateOwnProfile(newName, newAvatar)
                },
                onChangePassword = { oldPwd, newPwd, confirmPwd ->
                    viewModel.changeOwnPassword(oldPwd, newPwd, confirmPwd)
                },
                onDismiss = { showEditProfileDialog = false }
            )
        }
    }
}
