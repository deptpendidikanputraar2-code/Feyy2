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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    viewModel: MmqaViewModel,
    firebaseUser: FirebaseUser?,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onExportCsv: (filename: String, csvContent: String) -> Unit,
    onPickPdfTemplate: ((fileName: String, dataUrl: String) -> Unit) -> Unit,
    onPickChatDocument: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onPickChatPhoto: ((ChatAttachmentDraft) -> Unit) -> Unit,
    onSaveChatAttachment: (fileName: String, mimeType: String, dataUrl: String) -> Unit
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
                    onSelectMenu = { viewModel.selectMenu(it) },
                    onOpenEditProfile = onOpenEditProfile,
                    onLogout = { viewModel.logout() },
                    onGoogleSignOut = onGoogleSignOut,
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    MmqaTopHeaderBar(
                        currentUser = currentUser,
                        activeMenu = effectiveMenu,
                        syncState = syncState,
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
                        drawerContainerColor = Emerald950,
                        modifier = Modifier.width(288.dp)
                    ) {
                        MmqaSidebarContent(
                            currentUser = currentUser,
                            firebaseUser = firebaseUser,
                            menus = accessibleMenus,
                            activeMenu = effectiveMenu,
                            onSelectMenu = { menuId ->
                                viewModel.selectMenu(menuId)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onOpenEditProfile = {
                                coroutineScope.launch { drawerState.close() }
                                onOpenEditProfile()
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
    onSelectMenu: (String) -> Unit,
    onOpenEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onGoogleSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(Emerald950, Emerald900, Slate900)))
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
                            text = if (currentUser.role == "dewan_harian") "DEWAN HARIAN" else "MU’ALLIM FIRQOH",
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

        // Bottom Actions (Clean multi-user logout only — no developer setup buttons)
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                color = Rose600.copy(alpha = 0.25f),
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

@Composable
private fun MmqaTopHeaderBar(
    currentUser: UserAccount,
    activeMenu: String,
    syncState: SyncBannerState,
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
        "dashboard" -> "Dashboard Kehadiran" to "Rekapan kehadiran santri perfirqoh, mu’allim & dewan harian"
        "absensi" -> "Absensi Bertingkat" to "Presensi santri perfirqoh & pengurus harian"
        "materi" -> "Materi Perfirqoh" to "Pengaturan kitab, bab pembelajaran & target hafalan"
        "ujian" -> "Ujian & Penilaian" to "Peserta dari Data Induk, nilai rubrik berbobot & rekapan"
        "keuangan" -> "Keuangan & Syahriah" to "Buku kas majelis & kontrol syahriah santri otomatis"
        "surat" -> "Surat Menyurat & Arsip" to "Pembuatan surat template PDF/teks & arsip Kode A/B/C"
        "inventaris" -> "Inventaris Modern" to "Pendataan sarana majelis, valuasi aset & kondisi"
        "induk" -> "Data Induk Terpadu" to "Master data santri, mu’allim, dewan harian & firqoh"
        "personalia" -> "Struktur Personalia MMQA" to "Susunan kepengurusan, divisi & jabatan majelis"
        "chat" -> "Grup Chat Majelis" to "Komunikasi real-time dewan harian & mu’allim"
        "admin" -> "Kontrol Penuh Admin" to "Kelola menu, submenu, anak sub, akun & password rule"
        else -> "MMQA Al Rifai 02" to "Sistem Manajemen Majelis"
    }

    val syncDotColor = when {
        syncState is SyncBannerState.Offline || hasPending -> Amber500
        syncState is SyncBannerState.Error -> Rose600
        syncState is SyncBannerState.Syncing -> Amber400
        else -> Emerald700
    }

    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
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
                            .background(Slate100)
                            .testTag("open_drawer_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu Navigasi", tint = Slate900)
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
                            color = Slate900,
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
                    }
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Slate500,
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
                            .background(if (unreadNotificationCount > 0) Amber100 else Slate100)
                            .testTag("topbar_notifications_button")
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notifikasi Majelis",
                            tint = if (unreadNotificationCount > 0) Amber900 else Slate700,
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

                // Profile Avatar Button (opens Edit Name, Photo & Password)
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
            HorizontalDivider(color = Slate200)
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
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = Slate200)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            ) {
                menus.forEach { m ->
                    val active = m.id == activeMenu
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectMenu(m.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Emerald100 else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(text = m.icon, fontSize = 16.sp)
                        }
                        Text(
                            text = m.label.split(" ").firstOrNull() ?: m.label,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (active) Emerald900 else Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenMore)
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(text = "☰", fontSize = 16.sp, color = Slate700)
                    }
                    Text(
                        text = "Menu",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate500,
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
    modifier: Modifier = Modifier
) {
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
                "materi" -> MateriScreen(db, uiState, viewModel, isWideScreen)
                "ujian" -> UjianScreen(db, uiState, viewModel, onExportCsv)
                "keuangan" -> KeuanganScreen(db, uiState, viewModel, isWideScreen, onExportCsv)
                "surat" -> SuratScreen(db, uiState, viewModel, isWideScreen, onPickPdfTemplate)
                "inventaris" -> InventarisScreen(db, uiState, viewModel, isWideScreen, onExportCsv)
                "induk" -> DataIndukScreen(db, uiState, viewModel)
                "personalia" -> PersonaliaScreen(db, uiState, viewModel, isWideScreen)
                "chat" -> ChatScreen(
                    db = db,
                    uiState = uiState,
                    viewModel = viewModel,
                    isWideScreen = isWideScreen,
                    onPickDocument = onPickChatDocument,
                    onPickPhoto = onPickChatPhoto,
                    onSaveAttachment = onSaveChatAttachment
                )
                "admin" -> AdminScreen(db, uiState, viewModel, isWideScreen)
                else -> DashboardScreen(db, uiState, viewModel, isWideScreen)
            }
        }
    }
}
