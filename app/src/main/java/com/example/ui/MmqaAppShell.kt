package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.update.AppUpdateState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatAttachmentDraft
import com.example.data.MenuConfigItem
import com.example.data.MmqaDatabase
import com.example.data.SyncBannerState
import com.example.data.UserAccount
import com.example.ui.components.LiquidGlassBottomBarContainer
import com.example.ui.components.LiquidGlassSidebarContainer
import com.example.ui.components.LiquidGlassTopBarContainer
import com.example.ui.components.MmqaLogoImage
import com.example.ui.components.UserAvatarImage
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun MmqaAppShell(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    syncState: SyncBannerState,
    isOnline: Boolean,
    pendingMutationCount: Int,
    biometricLockEnabled: Boolean,
    viewModel: MmqaViewModel,
    firebaseUser: FirebaseUser?,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onLockApp: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onExportCsv: (filename: String, csvContent: String) -> Unit,
    onPickPdfTemplate: ((fileName: String, dataUrl: String) -> Unit) -> Unit,
    onPickChatDocument: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onPickChatPhoto: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onSaveChatAttachment: (fileName: String, mimeType: String, dataUrl: String) -> Unit,
    onExportBackupJson: (filename: String) -> Unit = {},
    onImportBackupJson: () -> Unit = {}
) {
    val currentUser = uiState.currentUser ?: return
    val accessibleMenus = viewModel.getAccessibleMenus()
    val effectiveMenu = if (accessibleMenus.any { it.id == uiState.activeMenu }) {
        uiState.activeMenu
    } else {
        accessibleMenus.firstOrNull()?.id ?: "dashboard"
    }

    if (effectiveMenu != "dashboard") {
        BackHandler {
            viewModel.selectMenu("dashboard")
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val hasAvailableUpdate = appUpdateState is AppUpdateState.UpdateAvailable ||
        appUpdateState is AppUpdateState.ReadyToInstall ||
        appUpdateState is AppUpdateState.NeedsInstallPermission

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate50)
            ) {
                MmqaSidebarContent(
                    currentUser = currentUser,
                    firebaseUser = firebaseUser,
                    menus = accessibleMenus,
                    activeMenu = effectiveMenu,
                    isOnline = isOnline,
                    pendingMutationCount = pendingMutationCount,
                    biometricLockEnabled = biometricLockEnabled,
                    hasAvailableUpdate = hasAvailableUpdate,
                    onSelectMenu = { viewModel.selectMenu(it) },
                    onOpenEditProfile = onOpenEditProfile,
                    onCheckAppUpdate = { viewModel.openUpdateDialogIfAvailable() },
                    onLockApp = onLockApp,
                    onLogout = { viewModel.logout() },
                    onGoogleSignOut = onGoogleSignOut,
                    modifier = Modifier
                        .width(284.dp)
                        .fillMaxHeight()
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    MmqaTopHeaderBar(
                        currentUser = currentUser,
                        activeMenu = effectiveMenu,
                        syncState = syncState,
                        isOnline = isOnline,
                        pendingMutationCount = pendingMutationCount,
                        hasPending = db.pendingSync,
                        unreadNotificationCount = unreadNotificationCount,
                        showMenuButton = false,
                        onOpenDrawer = {},
                        onOpenNotifications = onOpenNotifications,
                        onSyncNow = { viewModel.syncNow(manual = true) },
                        onOpenEditProfile = onOpenEditProfile,
                        onLogout = { viewModel.logout() }
                    )
                    MmqaMainContent(
                        activeMenu = effectiveMenu,
                        db = db,
                        uiState = uiState,
                        viewModel = viewModel,
                        isWideScreen = true,
                        onExportCsv = onExportCsv,
                        onPickPdfTemplate = onPickPdfTemplate,
                        onPickChatDocument = onPickChatDocument,
                        onPickChatPhoto = onPickChatPhoto,
                        onSaveChatAttachment = onSaveChatAttachment,
                        onExportBackupJson = onExportBackupJson,
                        onImportBackupJson = onImportBackupJson,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = Color.Transparent,
                        modifier = Modifier.width(292.dp)
                    ) {
                        MmqaSidebarContent(
                            currentUser = currentUser,
                            firebaseUser = firebaseUser,
                            menus = accessibleMenus,
                            activeMenu = effectiveMenu,
                            isOnline = isOnline,
                            pendingMutationCount = pendingMutationCount,
                            biometricLockEnabled = biometricLockEnabled,
                            hasAvailableUpdate = hasAvailableUpdate,
                            onSelectMenu = { menuId ->
                                viewModel.selectMenu(menuId)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onOpenEditProfile = {
                                coroutineScope.launch { drawerState.close() }
                                onOpenEditProfile()
                            },
                            onCheckAppUpdate = {
                                coroutineScope.launch { drawerState.close() }
                                viewModel.openUpdateDialogIfAvailable()
                            },
                            onLockApp = {
                                coroutineScope.launch { drawerState.close() }
                                onLockApp()
                            },
                            onLogout = {
                                coroutineScope.launch { drawerState.close() }
                                viewModel.logout()
                            },
                            onGoogleSignOut = {
                                coroutineScope.launch { drawerState.close() }
                                onGoogleSignOut()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                Scaffold(
                    containerColor = Slate50,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        MmqaTopHeaderBar(
                            currentUser = currentUser,
                            activeMenu = effectiveMenu,
                            syncState = syncState,
                            isOnline = isOnline,
                            pendingMutationCount = pendingMutationCount,
                            hasPending = db.pendingSync,
                            unreadNotificationCount = unreadNotificationCount,
                            showMenuButton = true,
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            onOpenNotifications = onOpenNotifications,
                            onSyncNow = { viewModel.syncNow(manual = true) },
                            onOpenEditProfile = onOpenEditProfile,
                            onLogout = { viewModel.logout() }
                        )
                    },
                    bottomBar = {
                        MmqaBottomNavBar(
                            menus = accessibleMenus.take(4),
                            activeMenu = effectiveMenu,
                            onSelectMenu = { viewModel.selectMenu(it) },
                            onOpenMore = { coroutineScope.launch { drawerState.open() } }
                        )
                    }
                ) { innerPadding ->
                    MmqaMainContent(
                        activeMenu = effectiveMenu,
                        db = db,
                        uiState = uiState,
                        viewModel = viewModel,
                        isWideScreen = false,
                        onExportCsv = onExportCsv,
                        onPickPdfTemplate = onPickPdfTemplate,
                        onPickChatDocument = onPickChatDocument,
                        onPickChatPhoto = onPickChatPhoto,
                        onSaveChatAttachment = onSaveChatAttachment,
                        onExportBackupJson = onExportBackupJson,
                        onImportBackupJson = onImportBackupJson,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun MmqaSidebarContent(
    currentUser: UserAccount,
    firebaseUser: FirebaseUser?,
    menus: List<MenuConfigItem>,
    activeMenu: String,
    isOnline: Boolean,
    pendingMutationCount: Int,
    biometricLockEnabled: Boolean,
    hasAvailableUpdate: Boolean,
    onSelectMenu: (String) -> Unit,
    onOpenEditProfile: () -> Unit,
    onCheckAppUpdate: () -> Unit,
    onLockApp: () -> Unit,
    onLogout: () -> Unit,
    onGoogleSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassSidebarContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
        // Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            MmqaLogoImage(size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MMQA AL RIFAI 02",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Amber300,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Sistem Terpadu Majelis",
                    fontSize = 11.sp,
                    color = Emerald100
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Active User Profile Card (Tap to edit name, photo & password)
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(onClick = onOpenEditProfile)
                .testTag("sidebar_edit_profile_card")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UserAvatarImage(
                        name = currentUser.name,
                        avatarBase64 = currentUser.avatarBase64,
                        size = 46.dp,
                        borderColor = Amber300
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (currentUser.role) {
                                "dewan_harian" -> "DEWAN HARIAN"
                                "wali_santri" -> "WALI SANTRI"
                                else -> "MU’ALLIM FIRQOH"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Amber300
                        )
                        Text(
                            text = currentUser.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUser.jabatan,
                            fontSize = 11.sp,
                            color = Emerald100.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profil Saya",
                                tint = Amber300,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ketuk untuk ubah nama, foto profil & password",
                    fontSize = 10.sp,
                    color = Emerald100.copy(alpha = 0.75f)
                )
            }
        }

        // Scrollable Menu List
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            menus.forEach { menu ->
                val active = menu.id == activeMenu
                Surface(
                    color = if (active) Amber400 else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 46.dp)
                        .clickable { onSelectMenu(menu.id) }
                        .testTag("nav_menu_${menu.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = menu.icon, fontSize = 16.sp)
                        Text(
                            text = menu.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) Emerald950 else Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Bottom Actions (Offline Vault badge, Biometric Lock & Multi-user logout)
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isOnline) "🛡️ Enkripsi AES-256 • Online" else "📴 Mode Offline Lokal",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) Emerald100 else Amber300
                    )
                    if (pendingMutationCount > 0) {
                        Text(
                            text = "$pendingMutationCount antrean",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Amber300
                        )
                    }
                }
            }
            Surface(
                color = if (hasAvailableUpdate) Amber400 else Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (hasAvailableUpdate) Amber300 else Color.White.copy(alpha = 0.22f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(onClick = onCheckAppUpdate)
                    .testTag("sidebar_check_update_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Cek Pembaruan APK",
                        tint = if (hasAvailableUpdate) Emerald950 else Amber300,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasAvailableUpdate) {
                            "Update APK Tersedia!"
                        } else {
                            "Cek Pembaruan (v${BuildConfig.VERSION_NAME})"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasAvailableUpdate) Emerald950 else Color.White
                    )
                }
            }
            if (biometricLockEnabled) {
                Surface(
                    color = Amber400.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Amber300.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 42.dp)
                        .clickable(onClick = onLockApp)
                        .testTag("sidebar_lock_app_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(9.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Kunci Aplikasi", tint = Amber300, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kunci Aplikasi (Biometrik)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Amber300)
                    }
                }
            }
            Surface(
                color = Rose600.copy(alpha = 0.28f),
                border = BorderStroke(1.dp, Rose200.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable(onClick = onLogout)
                    .testTag("sidebar_logout_button")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "🚪 Ganti Akun MMQA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
            if (firebaseUser != null) {
                TextButton(
                    onClick = onGoogleSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Keluar Sesi Google (${firebaseUser.email?.substringBefore("@") ?: "Akun"})",
                        fontSize = 10.sp,
                        color = Emerald100.copy(alpha = 0.8f)
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun MmqaTopHeaderBar(
    currentUser: UserAccount,
    activeMenu: String,
    syncState: SyncBannerState,
    isOnline: Boolean,
    pendingMutationCount: Int,
    hasPending: Boolean,
    unreadNotificationCount: Int,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val (title, subtitle) = when (activeMenu) {
        "dashboard" -> "Dashboard Kehadiran" to "Rekapan kehadiran, santri bermasalah & keuangan"
        "absensi" -> "Absensi Bertingkat" to "Presensi santri, barcode scanner & pengurus harian"
        "jadwal" -> "Jadwal Kegiatan" to "MMQA harian, ujian, absensi & event khusus"
        "juknis" -> "Juknis & Materi Rapat" to "Dokumen PDF resmi & acuan aturan pelanggaran"
        "materi" -> "Materi Perfirqoh" to "Pengaturan kitab, bab pembelajaran & target hafalan"
        "ujian" -> "Ujian & Penilaian" to "Peserta dari Data Induk, nilai rubrik berbobot & rekapan"
        "keuangan" -> "Keuangan & Syahriah" to "Buku kas majelis & kontrol syahriah santri otomatis"
        "surat" -> "Surat Menyurat & Arsip" to "Pembuatan surat template PDF/teks & arsip Kode A/B/C"
        "inventaris" -> "Inventaris Modern" to "Pendataan sarana majelis, valuasi aset & kondisi"
        "induk" -> "Data Induk Terpadu" to "Master data santri, mu’allim, dewan harian & firqoh"
        "personalia" -> "Struktur Personalia MMQA" to "Susunan kepengurusan, divisi & jabatan majelis"
        "chat" -> "Grup Chat Majelis" to "Komunikasi real-time dewan harian & mu’allim"
        "admin" -> "Kontrol Penuh Admin" to "Kelola menu, submenu, anak sub, akun & keamanan"
        else -> "MMQA Al Rifai 02" to "Sistem Manajemen Majelis"
    }

    val syncDotColor = when {
        !isOnline || syncState is SyncBannerState.Offline || hasPending || pendingMutationCount > 0 -> Amber500
        syncState is SyncBannerState.Error -> Rose600
        syncState is SyncBannerState.Syncing -> Amber400
        else -> Emerald700
    }

    LiquidGlassTopBarContainer(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.72f))
                            .testTag("open_drawer_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu Navigasi", tint = Emerald950)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onSyncNow)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Emerald950,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(syncDotColor)
                        )
                        if (!isOnline || pendingMutationCount > 0) {
                            Surface(
                                color = Amber100,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    if (!isOnline) {
                                        Icon(Icons.Default.CloudOff, contentDescription = "Offline", tint = Amber950, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = if (!isOnline) "Offline" else "Sync $pendingMutationCount",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Amber950
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Slate700,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Notification Bell
                Box {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (unreadNotificationCount > 0) Amber100.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.72f))
                            .testTag("topbar_notifications_button")
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notifikasi Majelis",
                            tint = if (unreadNotificationCount > 0) Amber900 else Emerald900,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (unreadNotificationCount > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Rose600)
                        ) {
                            Text(
                                text = if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Profile Avatar Button (opens Edit Name, Photo, Password & Biometrics)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenEditProfile)
                        .testTag("topbar_profile_button")
                ) {
                    UserAvatarImage(
                        name = currentUser.name,
                        avatarBase64 = currentUser.avatarBase64,
                        size = 40.dp,
                        borderColor = Emerald700
                    )
                }

                if (!showMenuButton) {
                    Surface(
                        color = Rose50,
                        border = BorderStroke(1.dp, Rose200),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 42.dp)
                            .clickable(onClick = onLogout)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text("Keluar", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Rose700)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MmqaBottomNavBar(
    menus: List<MenuConfigItem>,
    activeMenu: String,
    onSelectMenu: (String) -> Unit,
    onOpenMore: () -> Unit
) {
    LiquidGlassBottomBarContainer(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp, horizontal = 6.dp)
            ) {
                menus.forEach { m ->
                    val active = m.id == activeMenu
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectMenu(m.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Emerald900.copy(alpha = 0.14f) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(text = m.icon, fontSize = 16.sp)
                        }
                        Text(
                            text = m.label.split(" ").firstOrNull() ?: m.label,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                            color = if (active) Emerald950 else Slate700,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenMore)
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(text = "☰", fontSize = 16.sp, color = Emerald950)
                    }
                    Text(
                        text = "Menu",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MmqaMainContent(
    activeMenu: String,
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean,
    onExportCsv: (filename: String, csvContent: String) -> Unit,
    onPickPdfTemplate: ((fileName: String, dataUrl: String) -> Unit) -> Unit,
    onPickChatDocument: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onPickChatPhoto: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onSaveChatAttachment: (fileName: String, mimeType: String, dataUrl: String) -> Unit,
    onExportBackupJson: (filename: String) -> Unit = {},
    onImportBackupJson: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Full-screen viewport for Chat (no outer verticalScroll or padding)
    if (activeMenu == "chat") {
        Box(modifier = modifier.fillMaxSize()) {
            ChatScreen(
                db = db,
                uiState = uiState,
                viewModel = viewModel,
                isWideScreen = isWideScreen,
                onPickDocument = onPickChatDocument,
                onPickPhoto = onPickChatPhoto,
                onSaveAttachment = onSaveChatAttachment
            )
        }
        return
    }

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 24.dp else 14.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 1200.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            when (activeMenu) {
                "dashboard" -> DashboardScreen(db, uiState, viewModel, isWideScreen)
                "absensi" -> AbsensiScreen(db, uiState, viewModel, isWideScreen)
                "jadwal" -> JadwalScreen(db, uiState, viewModel, isWideScreen)
                "juknis" -> JuknisScreen(
                    db = db,
                    uiState = uiState,
                    viewModel = viewModel,
                    isWideScreen = isWideScreen,
                    onPickPdfFile = onPickPdfTemplate,
                    onSaveUploadedPdf = onSaveChatAttachment
                )
                "materi" -> MateriScreen(db, uiState, viewModel, isWideScreen)
                "ujian" -> UjianScreen(db, uiState, viewModel, onExportCsv)
                "keuangan" -> KeuanganScreen(db, uiState, viewModel, isWideScreen, onExportCsv)
                "surat" -> SuratScreen(db, uiState, viewModel, isWideScreen, onPickPdfTemplate)
                "inventaris" -> InventarisScreen(db, uiState, viewModel, isWideScreen, onExportCsv)
                "induk" -> DataIndukScreen(db, uiState, viewModel)
                "personalia" -> PersonaliaScreen(db, uiState, viewModel, isWideScreen)
                "admin" -> AdminScreen(
                    db = db,
                    uiState = uiState,
                    viewModel = viewModel,
                    isWideScreen = isWideScreen,
                    onExportBackupJson = onExportBackupJson,
                    onImportBackupJson = onImportBackupJson
                )
                else -> DashboardScreen(db, uiState, viewModel, isWideScreen)
            }
        }
    }
}
