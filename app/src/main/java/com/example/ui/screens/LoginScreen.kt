package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.components.JuknisPdfViewerDialog
import com.example.ui.components.LiquidGlassTopBarContainer
import com.example.ui.components.MmqaLogoImage
import com.example.ui.components.StatusPill
import com.example.ui.components.buildJuknisDocHtml
import com.example.ui.components.printHtmlDocument
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    db: MmqaDatabase,
    syncState: SyncBannerState,
    loginError: String?,
    isWideScreen: Boolean,
    firebaseUser: FirebaseUser?,
    onGoogleSignInSuccess: () -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onGoogleSignOut: () -> Unit,
    onLogin: (String, String) -> Unit,
    biometricQuickLoginAvailable: Boolean = false,
    savedBiometricUsername: String = "",
    onBiometricQuickLogin: () -> Unit = {},
    onSavePublicPdf: (fileName: String, mimeType: String, dataUrl: String) -> Unit = { _, _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var viewingJuknisDoc by remember { mutableStateOf<JuknisDocumentItem?>(null) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var loginSectionY by remember { mutableIntStateOf(0) }
    var infoSectionY by remember { mutableIntStateOf(0) }
    var jadwalSectionY by remember { mutableIntStateOf(0) }
    var juknisSectionY by remember { mutableIntStateOf(0) }

    val activeQuickTab by remember {
        derivedStateOf {
            val y = scrollState.value + 160
            when {
                juknisSectionY > 0 && y >= juknisSectionY -> "juknis"
                jadwalSectionY > 0 && y >= jadwalSectionY -> "jadwal"
                infoSectionY > 0 && y >= infoSectionY -> "info"
                else -> "login"
            }
        }
    }

    val heroGradient = Brush.verticalGradient(listOf(Emerald950, Emerald900, Slate900))

    val publicPortalContent: @Composable () -> Unit = {
        LoginPublicPortalSections(
            db = db,
            onInfoPositioned = { infoSectionY = it },
            onJadwalPositioned = { jadwalSectionY = it },
            onJuknisPositioned = { juknisSectionY = it },
            onOpenJuknis = { viewingJuknisDoc = it },
            onSavePublicPdf = onSavePublicPdf
        )
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(heroGradient)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp)
            ) {
                LoginBrandingPanel(db = db)
            }
            Surface(
                color = Slate50,
                shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LoginInteractiveQuickJumpBar(
                        activeTab = activeQuickTab,
                        onSelect = { tab ->
                            val targetY = when (tab) {
                                "info" -> infoSectionY
                                "jadwal" -> jadwalSectionY
                                "juknis" -> juknisSectionY
                                else -> loginSectionY
                            }
                            scope.launch { scrollState.animateScrollTo(targetY.coerceAtLeast(0)) }
                        }
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 28.dp, vertical = 22.dp)
                    ) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { loginSectionY = it.positionInParent().y.toInt() }
                        ) {
                            LoginFormCard(
                                username = username,
                                password = password,
                                showPassword = showPassword,
                                syncState = syncState,
                                loginError = loginError,
                                firebaseUser = firebaseUser,
                                biometricQuickLoginAvailable = biometricQuickLoginAvailable,
                                savedBiometricUsername = savedBiometricUsername,
                                onBiometricQuickLogin = onBiometricQuickLogin,
                                onGoogleSignInSuccess = onGoogleSignInSuccess,
                                onGoogleSignInError = onGoogleSignInError,
                                onGoogleSignOut = onGoogleSignOut,
                                onUsernameChange = { username = it },
                                onPasswordChange = { password = it },
                                onTogglePwd = { showPassword = !showPassword },
                                onSubmit = { onLogin(username, password) },
                                modifier = Modifier.widthIn(max = 520.dp)
                            )
                        }
                        publicPortalContent()
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(heroGradient)
        ) {
            // Sticky Interactive Liquid-Glass Quick-Jump Navigation Bar
            LiquidGlassTopBarContainer(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    LoginInteractiveQuickJumpBar(
                        activeTab = activeQuickTab,
                        onSelect = { tab ->
                            val targetY = when (tab) {
                                "info" -> infoSectionY
                                "jadwal" -> jadwalSectionY
                                "juknis" -> juknisSectionY
                                else -> loginSectionY
                            }
                            scope.launch { scrollState.animateScrollTo(targetY.coerceAtLeast(0)) }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    LoginBrandingPanel(db = db)
                }
                Surface(
                    color = Slate50,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 22.dp)
                            .navigationBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { loginSectionY = it.positionInParent().y.toInt() + 260 }
                        ) {
                            LoginFormCard(
                                username = username,
                                password = password,
                                showPassword = showPassword,
                                syncState = syncState,
                                loginError = loginError,
                                firebaseUser = firebaseUser,
                                biometricQuickLoginAvailable = biometricQuickLoginAvailable,
                                savedBiometricUsername = savedBiometricUsername,
                                onBiometricQuickLogin = onBiometricQuickLogin,
                                onGoogleSignInSuccess = onGoogleSignInSuccess,
                                onGoogleSignInError = onGoogleSignInError,
                                onGoogleSignOut = onGoogleSignOut,
                                onUsernameChange = { username = it },
                                onPasswordChange = { password = it },
                                onTogglePwd = { showPassword = !showPassword },
                                onSubmit = { onLogin(username, password) }
                            )
                        }

                        HorizontalDivider(color = Slate200)
                        publicPortalContent()
                    }
                }
            }
        }
    }

    viewingJuknisDoc?.let { doc ->
        JuknisPdfViewerDialog(
            doc = doc,
            onSaveUploadedPdf = onSavePublicPdf,
            onDismiss = { viewingJuknisDoc = null }
        )
    }
}

@Composable
private fun LoginInteractiveQuickJumpBar(
    activeTab: String,
    onSelect: (String) -> Unit
) {
    val items = listOf(
        "login" to "🔐 Masuk",
        "info" to "📌 Info Penting",
        "jadwal" to "🗓️ Jadwal",
        "juknis" to "📘 Juknis PDF"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        items.forEach { (id, label) ->
            val active = activeTab == id
            Surface(
                color = if (active) Emerald900 else Color.White.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, if (active) Emerald800 else Slate300),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 38.dp)
                    .clickable { onSelect(id) }
                    .testTag("login_jump_$id")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (active) Amber300 else Emerald950
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginPublicPortalSections(
    db: MmqaDatabase,
    onInfoPositioned: (Int) -> Unit,
    onJadwalPositioned: (Int) -> Unit,
    onJuknisPositioned: (Int) -> Unit,
    onOpenJuknis: (JuknisDocumentItem) -> Unit,
    onSavePublicPdf: (fileName: String, mimeType: String, dataUrl: String) -> Unit
) {
    val context = LocalContext.current
    val activeRule = remember(db.juknisDocs) { JuknisRuleEngine.getActiveAcuan(db.juknisDocs) }
    val pinnedInfos = remember(db.chatMessages) { db.chatMessages.filter { it.pinned }.take(4) }
    val publicJadwal = remember(db.jadwal) { db.jadwal.filter { it.isPublicOnLogin } }
    val publicJuknis = remember(db.juknisDocs) { db.juknisDocs.filter { it.isPublicOnLogin } }
    var jadwalCat by remember { mutableStateOf("ALL") }

    // 1. INFO PENTING MAJELIS
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onInfoPositioned(it.positionInParent().y.toInt() + 260) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("📌 Info Penting Majelis", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
            StatusPill("Publik", Emerald100, Emerald900)
        }

        // Active Rules Highlight from Juknis
        Surface(
            color = Amber50,
            border = BorderStroke(1.dp, Amber200),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("⚖️ Ketentuan Disiplin Kehadiran (${activeRule.nomorDokumen})", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Amber950)
                Text("• Alpha ≥ ${activeRule.batasAlphaSp1}x → ${activeRule.konsekuensiSp1}", fontSize = 11.sp, color = Slate900)
                Text("• Alpha ≥ ${activeRule.batasAlphaSp2}x → ${activeRule.konsekuensiSp2}", fontSize = 11.sp, color = Slate900)
                Text("• Alpha ≥ ${activeRule.batasAlphaSp3}x → ${activeRule.konsekuensiSp3}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose800)
                Text("• Izin ≥ ${activeRule.batasIzinPeringatan}x → ${activeRule.konsekuensiIzin}", fontSize = 11.sp, color = Amber900)
            }
        }

        pinnedInfos.forEach { msg ->
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Emerald100),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("📢 ${msg.senderName}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald900)
                        Text(msg.time, fontSize = 10.sp, color = Slate500)
                    }
                    Text(msg.text, fontSize = 12.sp, lineHeight = 17.sp, color = Slate700)
                }
            }
        }
    }

    // 2. JADWAL KEGIATAN MMQA
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onJadwalPositioned(it.positionInParent().y.toInt() + 260) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("🗓️ Jadwal Kegiatan MMQA", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
            StatusPill("${publicJadwal.size} Agenda", Sky100, Sky900)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf("ALL" to "Semua", "MMQA Harian" to "Harian", "Ujian" to "Ujian", "Absensi" to "Absensi", "Event Khusus" to "Event").forEach { (k, lbl) ->
                val act = jadwalCat == k
                Surface(
                    color = if (act) Emerald900 else Color.White,
                    border = BorderStroke(1.dp, if (act) Emerald900 else Slate200),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { jadwalCat = k }
                ) {
                    Text(
                        text = lbl,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (act) Amber300 else Slate700,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        val filteredJdw = publicJadwal.filter { jadwalCat == "ALL" || it.kategori.equals(jadwalCat, true) }
        filteredJdw.forEach { j ->
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        StatusPill(j.kategori, Emerald100, Emerald900)
                        Text("${j.jamMulai}–${j.jamSelesai} WIB", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Amber900)
                    }
                    Text(j.judul, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Emerald800, modifier = Modifier.size(14.dp))
                        val tgl = if (j.tanggal.isNotBlank()) "${j.hari}, ${j.tanggal}" else "${j.hari} (Rutin)"
                        Text(tgl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Rose700, modifier = Modifier.size(14.dp))
                        Text("${j.tempat} • PJ: ${j.penanggungJawab}", fontSize = 11.sp, color = Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (j.keterangan.isNotBlank()) {
                        Text(j.keterangan, fontSize = 11.sp, color = Slate700, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // 3. JUKNIS & MATERI RAPAT PDF
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onJuknisPositioned(it.positionInParent().y.toInt() + 260) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("📘 Juknis & Materi Rapat (PDF)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
            StatusPill("${publicJuknis.size} Dokumen", Rose50, Rose800, Rose200)
        }
        publicJuknis.forEach { doc ->
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, if (doc.isAcuanAturan) Amber300 else Slate200),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(doc.kategori, Emerald100, Emerald900)
                        if (doc.isAcuanAturan) StatusPill("⚖️ Acuan Resmi", Amber100, Amber950)
                    }
                    Text(doc.judul, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Text("No: ${doc.nomorDokumen.ifBlank { "-" }} • ${doc.tanggal}", fontSize = 10.sp, color = Slate500)
                    if (doc.deskripsi.isNotBlank()) {
                        Text(doc.deskripsi, fontSize = 11.sp, color = Slate700, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onOpenJuknis(doc) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Lihat PDF", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Lihat PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        OutlinedButton(
                            onClick = {
                                if (doc.pdfDataUrl.isNotBlank()) {
                                    onSavePublicPdf(doc.pdfName.ifBlank { "${doc.judul.take(28)}.pdf" }, "application/pdf", doc.pdfDataUrl)
                                } else {
                                    printHtmlDocument(context, "Juknis_${doc.id}", buildJuknisDocHtml(doc))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Rose200),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Rose50, contentColor = Rose800),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Unduh PDF", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Unduh PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginBrandingPanel(db: MmqaDatabase) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MmqaLogoImage(size = 64.dp)
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = Amber400.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Amber300.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "SISTEM TERPADU MULTI-USER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber300,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "MMQA AL RIFAI 02",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Majelis Musyawarah Qira'atil Kutub & Al-Qur'an",
                    fontSize = 12.sp,
                    color = Emerald100.copy(alpha = 0.9f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Manajemen Akademik, Absensi Barcode & Kesekretariatan Dalam Satu Genggaman.",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "Dilengkapi enkripsi AES-256, biometrik, offline-first auto-sync, jadwal kegiatan & akses langsung Juknis PDF resmi majelis.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Emerald100.copy(alpha = 0.85f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LoginStatBox("TOTAL SANTRI", db.santri.size.toString(), Amber300, Modifier.weight(1f))
            LoginStatBox("MU'ALLIM", db.muallim.size.toString(), Color.White, Modifier.weight(1f))
            LoginStatBox("FIRQOH", db.firqoh.size.toString(), Emerald100, Modifier.weight(1f))
        }

        Text(
            text = "© 2026 MMQA Al Rifai 02 • AR2 Digital • Gulir ke bawah untuk Info, Jadwal & Juknis PDF ↓",
            fontSize = 11.sp,
            color = Amber300.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun LoginStatBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Emerald100)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = valueColor)
        }
    }
}

@Composable
private fun LoginFormCard(
    username: String,
    password: String,
    showPassword: Boolean,
    syncState: SyncBannerState,
    loginError: String?,
    firebaseUser: FirebaseUser?,
    biometricQuickLoginAvailable: Boolean,
    savedBiometricUsername: String,
    onBiometricQuickLogin: () -> Unit,
    onGoogleSignInSuccess: () -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onGoogleSignOut: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePwd: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusBg, statusTxt) = when (syncState) {
        is SyncBannerState.Offline -> Triple("○ Offline", Amber100, Amber900)
        is SyncBannerState.Syncing -> Triple("⏳ Sinkron...", Amber100, Amber900)
        else -> Triple("● Online", Emerald100, Emerald900)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill("Portal Masuk Pengguna", Emerald100, Emerald900)
                StatusPill(statusLabel, statusBg, statusTxt)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Assalamu'alaikum Wr. Wb.",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Slate900
            )
            Text(
                text = if (firebaseUser == null) {
                    "Masuk dengan akun Google Anda untuk mengaktifkan sesi dan notifikasi otomatis."
                } else {
                    "Silakan masuk menggunakan username dan kata sandi akun MMQA Anda."
                },
                fontSize = 12.sp,
                color = Slate500
            )
        }

        if (firebaseUser == null) {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Emerald100),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔐 Verifikasi Akun Google",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Emerald950
                    )
                    Text(
                        text = "Masuk dengan Google agar notifikasi chat majelis dan sinkronisasi data antar perangkat berjalan otomatis.",
                        fontSize = 12.sp,
                        color = Slate700
                    )
                    GoogleSignInButton(
                        onAuthSuccess = onGoogleSignInSuccess,
                        onAuthError = onGoogleSignInError,
                        label = "Sign in with Google"
                    )
                    if (!loginError.isNullOrBlank()) {
                        Surface(
                            color = Rose50,
                            border = BorderStroke(1.dp, Rose200),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = loginError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Rose800,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                color = Emerald50,
                border = BorderStroke(1.dp, Emerald100),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Akun Google: ${firebaseUser.displayName ?: "Terhubung"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald950
                        )
                        Text(
                            text = firebaseUser.email ?: firebaseUser.uid,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Emerald800
                        )
                    }
                    TextButton(onClick = onGoogleSignOut) {
                        Text("Ganti", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username", fontSize = 12.sp) },
                placeholder = { Text("Masukkan username Anda...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_input")
            )

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kata Sandi / Password", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Text(
                        text = if (showPassword) "Sembunyikan" else "Tampilkan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald800,
                        modifier = Modifier
                            .clickable(onClick = onTogglePwd)
                            .padding(4.dp)
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = { Text("Masukkan password...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )
            }

            if (!loginError.isNullOrBlank()) {
                Surface(
                    color = Rose50,
                    border = BorderStroke(1.dp, Rose200),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = loginError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Rose800,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_button")
            ) {
                Text(
                    text = "Masuk ke Aplikasi MMQA AR2 →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (biometricQuickLoginAvailable && savedBiometricUsername.isNotBlank()) {
                OutlinedButton(
                    onClick = onBiometricQuickLogin,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, Emerald700),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Emerald50, contentColor = Emerald900),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_biometric_quick_btn")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Login Biometrik", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Masuk Cepat Biometrik (@$savedBiometricUsername)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Text(
                text = "Hubungi Admin Dewan Harian apabila Anda belum memiliki akun atau lupa kata sandi.",
                fontSize = 11.sp,
                color = Slate500
            )
        }
    }
}
