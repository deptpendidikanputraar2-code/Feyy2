package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BiometricCapability
import com.example.data.ChatAttachmentDraft
import com.example.data.FirestoreNotificationRepository
import com.example.ui.MmqaAppShell
import com.example.ui.MmqaViewModel
import com.example.ui.ToastMessage
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutGoogleAndFirebase
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.LiquidGlassSidebarContainer
import com.example.ui.components.NfcCardReaderHelper
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

class MainActivity : FragmentActivity() {

    private var nfcStateReceiverRegistered = false

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                when (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)) {
                    NfcAdapter.STATE_ON -> NfcCardReaderHelper.enableExclusiveReaderMode(this@MainActivity, forceRearm = true)
                    NfcAdapter.STATE_OFF, NfcAdapter.STATE_TURNING_OFF -> NfcCardReaderHelper.onAdapterDisabled()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MmqaAr2Theme {
                val vm: MmqaViewModel = viewModel()
                MmqaRootApp(activity = this, viewModel = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!nfcStateReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    nfcStateReceiver,
                    IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                nfcStateReceiverRegistered = true
            }
        }
        NfcCardReaderHelper.enableExclusiveReaderMode(this, forceRearm = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            NfcCardReaderHelper.enableExclusiveReaderMode(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        if (tag != null) {
            NfcCardReaderHelper.onTagDiscovered(tag)
        }
    }

    override fun onPause() {
        NfcCardReaderHelper.disableReaderMode(this)
        if (nfcStateReceiverRegistered) {
            runCatching { unregisterReceiver(nfcStateReceiver) }
            nfcStateReceiverRegistered = false
        }
        super.onPause()
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
private fun MmqaRootApp(activity: FragmentActivity, viewModel: MmqaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val firebaseUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val db by viewModel.database.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val supabaseStatus by viewModel.supabaseStatus.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnlineFlow.collectAsStateWithLifecycle()
    val pendingMutationCount by viewModel.pendingMutationCount.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val biometricCapability = remember { viewModel.getBiometricCapability() }

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
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var pendingPdfCallback by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    var pendingChatAttachCallback by remember { mutableStateOf<((ChatAttachmentDraft) -> Unit)?>(null) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val content = pendingBackupJson
        pendingBackupJson = null
        if (uri != null && content != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                            writer.write(content)
                        }
                    }
                }.onSuccess {
                    viewModel.showToast("Backup Database JSON berhasil disimpan!")
                }.onFailure {
                    viewModel.showToast("Gagal mengekspor backup JSON: ${it.message}", isError = true)
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val res = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("File backup tidak terbaca.")
                    bytes.toString(Charsets.UTF_8)
                }
                withContext(Dispatchers.Main) {
                    res.onSuccess { rawJson ->
                        viewModel.importDatabaseBackupJson(rawJson)
                    }.onFailure {
                        viewModel.showToast(it.message ?: "Gagal membuka file backup JSON", isError = true)
                    }
                }
            }
        }
    }

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

    val saveBase64Attachment: (String, String, String) -> Unit = { fileName, _, dataUrl ->
        val pureB64 = dataUrl.substringAfter("base64,", dataUrl).trim()
        runCatching { Base64.decode(pureB64, Base64.DEFAULT) }
            .onSuccess { bytes ->
                pendingSaveBytes = bytes
                saveAttachmentLauncher.launch(fileName.ifBlank { "dokumen_mmqa.pdf" })
            }
            .onFailure {
                viewModel.showToast("Dokumen tidak valid untuk diunduh.", isError = true)
            }
    }

    val triggerBiometricQuickLogin: () -> Unit = {
        viewModel.getSecurityManager().authenticate(
            activity = activity,
            title = "Login Cepat Biometrik MMQA AR2",
            subtitle = "Verifikasi untuk masuk ke akun @${securitySettings.savedUsername}",
            onSuccess = {
                viewModel.loginWithBiometricSavedUser()
            },
            onError = { err ->
                viewModel.showToast(err, isError = true)
            }
        )
    }

    val triggerBiometricUnlock: () -> Unit = {
        viewModel.getSecurityManager().authenticate(
            activity = activity,
            title = "Buka Kunci MMQA AR2",
            subtitle = "Data lokal terenkripsi AES-256 — verifikasi biometrik Anda",
            onSuccess = {
                viewModel.unlockAppWithBiometric()
            },
            onError = { err ->
                viewModel.showToast(err, isError = true)
            }
        )
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

    val showLockOverlay = isAppLocked && securitySettings.biometricLockEnabled

    LaunchedEffect(showLockOverlay) {
        if (showLockOverlay) {
            delay(250L)
            triggerBiometricUnlock()
        }
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
                    onLogin = { u, p -> viewModel.login(u, p) },
                    biometricQuickLoginAvailable = false,
                    savedBiometricUsername = securitySettings.savedUsername,
                    onBiometricQuickLogin = triggerBiometricQuickLogin,
                    onSavePublicPdf = saveBase64Attachment
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

                // Real-time HP notifications for offline-to-online auto-sync & database updates
                LaunchedEffect(activeFirebaseUser.uid, viewModel) {
                    viewModel.realtimeAlerts.collect { alert ->
                        MmqaLocalNotifier.showNotification(context, alert.title, alert.body, alert.notifId)
                        notifVm.pushNotification(
                            title = alert.title,
                            body = alert.body,
                            category = alert.category,
                            channelId = ""
                        )
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
                    val bioQuickReady = securitySettings.biometricQuickLoginEnabled &&
                        securitySettings.savedUsername.isNotBlank() &&
                        biometricCapability == BiometricCapability.AVAILABLE
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
                            onLogin = { u, p -> viewModel.login(u, p) },
                            biometricQuickLoginAvailable = bioQuickReady,
                            savedBiometricUsername = securitySettings.savedUsername,
                            onBiometricQuickLogin = triggerBiometricQuickLogin,
                            onSavePublicPdf = saveBase64Attachment
                        )
                    }
                } else {
                    MmqaAppShell(
                        db = db,
                        uiState = uiState,
                        syncState = syncState,
                        isOnline = isOnline,
                        pendingMutationCount = pendingMutationCount,
                        biometricLockEnabled = securitySettings.biometricLockEnabled,
                        viewModel = viewModel,
                        firebaseUser = activeFirebaseUser,
                        unreadNotificationCount = unreadCount,
                        onOpenNotifications = { showNotifDialog = true },
                        onOpenEditProfile = {
                            draftAvatarBase64 = uiState.currentUser?.avatarBase64 ?: ""
                            showEditProfileDialog = true
                        },
                        onLockApp = { viewModel.lockAppNow() },
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
                        onSaveChatAttachment = saveBase64Attachment,
                        onExportBackupJson = { filename ->
                            pendingBackupJson = viewModel.exportDatabaseBackupJson()
                            exportJsonLauncher.launch(filename)
                        },
                        onImportBackupJson = {
                            importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }
                    )
                }

                if (showNotifDialog && !showLockOverlay) {
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

        // Biometric App Lock Shield Overlay
        if (showLockOverlay) {
            BackHandler(enabled = true) { /* Prevent bypassing biometric lock */ }
            BiometricLockOverlay(
                onUnlockClick = triggerBiometricUnlock
            )
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
        if (showEditProfileDialog && loggedInUser != null && !showLockOverlay) {
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
                onDismiss = { showEditProfileDialog = false },
                securitySettings = securitySettings,
                biometricCapability = biometricCapability,
                onToggleBiometricLock = { enabled ->
                    viewModel.setBiometricLockEnabled(enabled)
                },
                onToggleBiometricQuickLogin = { enabled ->
                    viewModel.setBiometricQuickLoginEnabled(enabled)
                },
                onLockNow = {
                    showEditProfileDialog = false
                    viewModel.lockAppNow()
                }
            )
        }
    }
}

@Composable
private fun BiometricLockOverlay(
    onUnlockClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF011B13), Emerald950, Color(0xFF022B1E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSidebarContainer(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(2.dp, Amber400),
                    shadowElevation = 6.dp
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mmqa_logo),
                        contentDescription = "Logo MMQA Al Rifa'i 02",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                    )
                }

                Surface(
                    color = Emerald700.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = "Enkripsi AES-256",
                            tint = Amber400,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "AES-256-GCM • Kunci Biometrik Aktif",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Aplikasi Terkunci",
                        tint = Amber400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "MMQA AR2 Terkunci",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    "Seluruh database lokal dan antrean sinkronisasi dilindungi enkripsi Android Keystore. Verifikasi sidik jari, wajah, atau kunci layar untuk melanjutkan.",
                    color = Emerald100.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onUnlockClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber500,
                        contentColor = Emerald950
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Buka Kunci dengan Biometrik",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
