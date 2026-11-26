package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

private val ROLE_OPTIONS = listOf(
    "all" to "Semua Peran",
    "dewan_harian" to "Dewan Harian",
    "muallim" to "Mu'allim"
)

private fun roleDisplayLabel(role: String): String = when (role) {
    "dewan_harian" -> "Dewan Harian"
    "muallim" -> "Mu'allim"
    else -> "Semua Peran"
}

private fun menuLabelOf(menuId: String): String = when (menuId) {
    "absensi" -> "Absensi"
    "materi" -> "Materi"
    "ujian" -> "Ujian"
    "keuangan" -> "Keuangan"
    "surat" -> "Surat"
    "inventaris" -> "Inventaris"
    "induk" -> "Data Induk"
    "personalia" -> "Struktur Personalia"
    "chat" -> "Grup Chat"
    else -> menuId
}

@Composable
fun AdminScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean
) {
    if (uiState.currentUser?.role != "dewan_harian") {
        SectionCard {
            Text(
                text = "⛔ Akses ditolak. Menu ini khusus Dewan Harian / Admin MMQA.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Rose700
            )
        }
        return
    }

    var editingUser by remember { mutableStateOf<UserAccount?>(null) }
    var showUserModal by remember { mutableStateOf(false) }
    var accessTargetUser by remember { mutableStateOf<UserAccount?>(null) }
    var anakSubMenuFilter by remember { mutableStateOf("ALL") }

    val cfg = db.adminConfig

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubmenuTabsRow(
            items = listOf(
                "menus" to "1. Menu Utama",
                "submenus" to "2. Submenu",
                "anaksub" to "3. Anak Sub",
                "permissions" to "4. Izin & Tarif",
                "users" to "5. Akun & Akses User",
                "pwdrule" to "6. Password Rule"
            ),
            selectedId = uiState.adminTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(adminTab = tabId) } }
        )

        when (uiState.adminTab) {
            // =================================================================
            // LEVEL 1: KELOLA AKSES MENU UTAMA
            // =================================================================
            "menus" -> {
                SectionCard {
                    Text("🎛️ Kelola Akses Menu Utama (Level 1)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Text("Atur label, urutan, peran yang diizinkan, serta aktif/nonaktif setiap menu.", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cfg.menus.sortedBy { it.order }.forEach { m ->
                            var labelDraft by remember(m.id, m.label) { mutableStateOf(m.label) }
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("${m.order}.", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Slate500)
                                        Text(m.icon, fontSize = 18.sp)
                                        OutlinedTextField(
                                            value = labelDraft,
                                            onValueChange = { labelDraft = it },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            trailingIcon = {
                                                if (labelDraft.trim() != m.label && labelDraft.isNotBlank()) {
                                                    IconButton(onClick = { viewModel.updateAdminMenuLabel(m.id, labelDraft) }) {
                                                        Icon(Icons.Default.Check, contentDescription = "Simpan Label", tint = Emerald800)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CompactDropdownSelector(
                                            label = "",
                                            selectedText = roleDisplayLabel(m.roles),
                                            options = if (m.id == "admin") listOf("dewan_harian" to "Dewan Harian") else ROLE_OPTIONS,
                                            onSelect = { role -> viewModel.updateAdminMenuRole(m.id, role) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.moveAdminMenuOrder(m.id, -1) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Naik", tint = Slate700, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveAdminMenuOrder(m.id, 1) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Turun", tint = Slate700, modifier = Modifier.size(18.dp))
                                        }
                                        Button(
                                            onClick = { viewModel.toggleAdminMenuEnabled(m.id) },
                                            enabled = m.id != "admin",
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (m.enabled) Emerald700 else Rose600
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (m.enabled) "AKTIF" else "OFF", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // LEVEL 2: KELOLA AKSES SUBMENU
            // =================================================================
            "submenus" -> {
                val groups = listOf(
                    Triple("absensi", "Submenu Absensi", cfg.submenus.absensi),
                    Triple("ujian", "Submenu Ujian & Penilaian", cfg.submenus.ujian),
                    Triple("keuangan", "Submenu Keuangan", cfg.submenus.keuangan),
                    Triple("surat", "Submenu Surat Menyurat", cfg.submenus.surat),
                    Triple("induk", "Submenu Data Induk", cfg.submenus.induk)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groups.forEach { (groupKey, title, items) ->
                        SectionCard {
                            Text("📑 $title (Level 2)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text("Atur label, peran akses, serta status aktif tiap tab submenu.", fontSize = 11.sp, color = Slate500)
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items.forEach { sub ->
                                    var draft by remember(groupKey, sub.id, sub.label) { mutableStateOf(sub.label) }
                                    Surface(
                                        color = Slate50,
                                        border = BorderStroke(1.dp, Slate200),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = draft,
                                                onValueChange = { draft = it },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                trailingIcon = {
                                                    if (draft.trim() != sub.label && draft.isNotBlank()) {
                                                        IconButton(onClick = { viewModel.updateSubmenu(groupKey, sub.id, label = draft) }) {
                                                            Icon(Icons.Default.Check, contentDescription = "Simpan Submenu", tint = Emerald800)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                CompactDropdownSelector(
                                                    label = "",
                                                    selectedText = roleDisplayLabel(sub.roles),
                                                    options = ROLE_OPTIONS,
                                                    onSelect = { role -> viewModel.updateSubmenu(groupKey, sub.id, role = role) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Button(
                                                    onClick = { viewModel.updateSubmenu(groupKey, sub.id, toggleEnabled = true) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (sub.enabled) Emerald700 else Rose600
                                                    ),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(if (sub.enabled) "AKTIF" else "OFF", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // LEVEL 3: KELOLA AKSES ANAK SUB (FITUR & AKSI GRANULAR)
            // =================================================================
            "anaksub" -> {
                val menuKeys = listOf("ALL", "absensi", "materi", "ujian", "keuangan", "surat", "inventaris", "induk", "personalia", "chat")
                val filteredAnakSub = if (anakSubMenuFilter == "ALL") {
                    cfg.anakSub
                } else {
                    cfg.anakSub.filter { it.menuId == anakSubMenuFilter }
                }

                SectionCard {
                    Text("🧩 Kelola Akses Anak Sub (Level 3)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Text(
                        text = "Kontrol penuh hingga tombol/fitur di dalam setiap submenu (input nilai, kirim lampiran chat, hapus data, ekspor CSV, dll).",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        menuKeys.forEach { mk ->
                            val active = anakSubMenuFilter == mk
                            Surface(
                                color = if (active) Emerald900 else Slate100,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable { anakSubMenuFilter = mk }
                            ) {
                                Text(
                                    text = if (mk == "ALL") "Semua (${cfg.anakSub.size})" else menuLabelOf(mk),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Amber300 else Slate700,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredAnakSub.forEach { anak ->
                            var draftLabel by remember(anak.id, anak.label) { mutableStateOf(anak.label) }
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StatusPill(menuLabelOf(anak.menuId), Emerald100, Emerald900)
                                        StatusPill("Sub: ${anak.submenuId}", Amber100, Amber900)
                                    }
                                    OutlinedTextField(
                                        value = draftLabel,
                                        onValueChange = { draftLabel = it },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        trailingIcon = {
                                            if (draftLabel.trim() != anak.label && draftLabel.isNotBlank()) {
                                                IconButton(onClick = { viewModel.updateAnakSub(anak.id, label = draftLabel) }) {
                                                    Icon(Icons.Default.Check, contentDescription = "Simpan Anak Sub", tint = Emerald800)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CompactDropdownSelector(
                                            label = "",
                                            selectedText = roleDisplayLabel(anak.roles),
                                            options = ROLE_OPTIONS,
                                            onSelect = { role -> viewModel.updateAnakSub(anak.id, role = role) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = { viewModel.updateAnakSub(anak.id, toggleEnabled = true) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (anak.enabled) Emerald700 else Rose600
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("toggle_anaksub_${anak.id}")
                                        ) {
                                            Text(if (anak.enabled) "AKTIF" else "OFF", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // IZIN GLOBAL & TARIF SYAHRIAH
            // =================================================================
            "permissions" -> {
                val p = cfg.permissions
                var syahriahInput by remember(p.syahriahNominal) { mutableStateOf(p.syahriahNominal.toString()) }
                SectionCard {
                    Text("🔐 Pengaturan Tarif Syahriah & Izin Global", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionToggleRow("Mu'allim boleh tambah/edit Materi Perfirqoh", p.muallimCanManageMateri) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanManageMateri = v) }
                        }
                        PermissionToggleRow("Mu'allim boleh kelola Materi Ujian Perfirqoh", p.muallimCanManageUjianMateri) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanManageUjianMateri = v) }
                        }
                        PermissionToggleRow("Mu'allim boleh ubah Format & Bobot Rubrik", p.muallimCanManageRubrik) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanManageRubrik = v) }
                        }
                        PermissionToggleRow("Mu'allim boleh input Keuangan & Syahriah", p.muallimCanInputKeuangan) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanInputKeuangan = v) }
                        }
                        PermissionToggleRow("Mu'allim boleh menerbitkan Surat & Template", p.muallimCanManageSurat) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanManageSurat = v) }
                        }
                        PermissionToggleRow("Mu'allim boleh tambah/edit Inventaris", p.muallimCanManageInventaris) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanManageInventaris = v) }
                        }
                        PermissionToggleRow("Mu'allim boleh tambah/edit Data Induk", p.muallimCanManageDataInduk) { v ->
                            viewModel.updateAdminPermissions { it.copy(muallimCanManageDataInduk = v) }
                        }
                        PermissionToggleRow("Izinkan aksi Hapus Data (Global)", p.allowDeleteRecords) { v ->
                            viewModel.updateAdminPermissions { it.copy(allowDeleteRecords = v) }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Slate200)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = syahriahInput,
                            onValueChange = { syahriahInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Nominal Syahriah Bulanan (Rp)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val nom = syahriahInput.toLongOrNull() ?: 150000L
                                viewModel.updateAdminPermissions { it.copy(syahriahNominal = nom) }
                                viewModel.showToast("Tarif Syahriah diperbarui!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // =================================================================
            // KELOLA AKUN & HAK AKSES KHUSUS PER USER (MENU / SUBMENU / ANAK SUB)
            // =================================================================
            "users" -> {
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("👥 Kelola Akun & Akses Pengguna", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text("Tambah akun baru atau atur hak akses Menu, Submenu & Anak Sub per pengguna.", fontSize = 11.sp, color = Slate500)
                        }
                        Button(
                            onClick = {
                                editingUser = null
                                showUserModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("admin_add_user_button")
                        ) { Text("+ Akun", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        db.users.forEach { u ->
                            val isLocked = u.lockedUntil != null && System.currentTimeMillis() < u.lockedUntil
                            val restrictionCount = u.deniedMenus.size + u.deniedSubmenus.size + u.deniedAnakSub.size
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        UserAvatarImage(
                                            name = u.name,
                                            avatarBase64 = u.avatarBase64,
                                            size = 42.dp,
                                            borderColor = Emerald700
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = u.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Slate900,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                StatusPill(
                                                    text = if (u.role == "dewan_harian") "DEWAN" else "MU'ALLIM",
                                                    bgColor = if (u.role == "dewan_harian") Emerald100 else Amber100,
                                                    textColor = if (u.role == "dewan_harian") Emerald900 else Amber900
                                                )
                                            }
                                            Text(
                                                text = "@${u.username} • ${u.jabatan}",
                                                fontSize = 11.sp,
                                                color = Slate500,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isLocked) {
                                            StatusPill("🔒 Terkunci", Rose100, Rose800)
                                        }
                                        if (restrictionCount > 0) {
                                            StatusPill("$restrictionCount Akses Dibatasi", Amber100, Amber900)
                                        } else {
                                            StatusPill("Akses Sesuai Peran", Emerald50, Emerald800)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { accessTargetUser = u },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("user_access_btn_${u.username}")
                                        ) {
                                            Text("🔐 Hak Akses", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald900)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TextButton(onClick = {
                                            editingUser = u
                                            showUserModal = true
                                        }) { Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700) }
                                        if (u.username != "admin") {
                                            TextButton(onClick = { viewModel.deleteAdminUser(u.id) }) {
                                                Text("Hapus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // KEBIJAKAN PASSWORD RULE
            // =================================================================
            else -> {
                val r = cfg.passwordRule
                var minLen by remember(r.minLength) { mutableStateOf(r.minLength.toString()) }
                var maxAtt by remember(r.maxLoginAttempts) { mutableStateOf(r.maxLoginAttempts.toString()) }
                var reqUpper by remember(r.requireUppercase) { mutableStateOf(r.requireUppercase) }
                var reqNum by remember(r.requireNumber) { mutableStateOf(r.requireNumber) }
                var reqSpec by remember(r.requireSpecial) { mutableStateOf(r.requireSpecial) }

                SectionCard {
                    Text("🔑 Kebijakan Keamanan Password (Password Rule)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Text("Aturan ini berlaku otomatis untuk pembuatan akun baru dan ubah password mandiri.", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minLen,
                            onValueChange = { minLen = it.filter { c -> c.isDigit() } },
                            label = { Text("Min. Panjang Karakter", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxAtt,
                            onValueChange = { maxAtt = it.filter { c -> c.isDigit() } },
                            label = { Text("Batas Gagal Login", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionToggleRow("Wajib Huruf Kapital (A-Z)", reqUpper) { reqUpper = it }
                        PermissionToggleRow("Wajib Mengandung Angka (0-9)", reqNum) { reqNum = it }
                        PermissionToggleRow("Wajib Karakter Spesial (!@#\$%)", reqSpec) { reqSpec = it }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.savePasswordRule(
                                PasswordRule(
                                    minLength = (minLen.toIntOrNull() ?: 3).coerceIn(3, 32),
                                    requireUppercase = reqUpper,
                                    requireNumber = reqNum,
                                    requireSpecial = reqSpec,
                                    maxLoginAttempts = (maxAtt.toIntOrNull() ?: 5).coerceIn(0, 20),
                                    expireDays = 0
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💾 Simpan Kebijakan Password Rule", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showUserModal) {
        AdminUserFormModal(
            editingUser = editingUser,
            db = db,
            viewModel = viewModel,
            onDismiss = { showUserModal = false }
        )
    }

    accessTargetUser?.let { target ->
        UserAccessOverrideDialog(
            user = target,
            cfg = cfg,
            onSave = { deniedMenus, deniedSubmenus, deniedAnakSub ->
                viewModel.updateUserAccessOverrides(target.id, deniedMenus, deniedSubmenus, deniedAnakSub)
                accessTargetUser = null
            },
            onDismiss = { accessTargetUser = null }
        )
    }
}

@Composable
private fun UserAccessOverrideDialog(
    user: UserAccount,
    cfg: AdminConfig,
    onSave: (deniedMenus: List<String>, deniedSubmenus: List<String>, deniedAnakSub: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var activeLevelTab by remember { mutableStateOf("menus") }
    val deniedMenus = remember(user.id) { mutableStateListOf<String>().apply { addAll(user.deniedMenus) } }
    val deniedSubmenus = remember(user.id) { mutableStateListOf<String>().apply { addAll(user.deniedSubmenus) } }
    val deniedAnakSub = remember(user.id) { mutableStateListOf<String>().apply { addAll(user.deniedAnakSub) } }

    MmqaModalDialog(title = "🔐 Akses Khusus: ${user.name}", onDismiss = onDismiss) {
        Text(
            text = "Nonaktifkan menu, submenu, atau anak sub tertentu khusus untuk akun @${user.username}.",
            fontSize = 11.sp,
            color = Slate500
        )
        Spacer(modifier = Modifier.height(10.dp))
        SubmenuTabsRow(
            items = listOf("menus" to "Menu Utama", "submenus" to "Submenu", "anaksub" to "Anak Sub"),
            selectedId = activeLevelTab,
            onSelect = { activeLevelTab = it }
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (activeLevelTab) {
            "menus" -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cfg.menus.sortedBy { it.order }.forEach { m ->
                        val allowed = m.id !in deniedMenus
                        val locked = m.id == "admin" && user.username == "admin"
                        PermissionToggleRow(
                            label = "${m.icon} ${m.label}",
                            checked = allowed
                        ) { checked ->
                            if (!locked) {
                                if (checked) deniedMenus.remove(m.id) else if (m.id !in deniedMenus) deniedMenus.add(m.id)
                            }
                        }
                    }
                }
            }
            "submenus" -> {
                val allSubs = listOf(
                    "absensi" to cfg.submenus.absensi,
                    "ujian" to cfg.submenus.ujian,
                    "keuangan" to cfg.submenus.keuangan,
                    "surat" to cfg.submenus.surat,
                    "induk" to cfg.submenus.induk
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allSubs.forEach { (grp, list) ->
                        list.forEach { sub ->
                            val key = "$grp:${sub.id}"
                            val allowed = key !in deniedSubmenus
                            PermissionToggleRow(
                                label = "[${menuLabelOf(grp)}] ${sub.label}",
                                checked = allowed
                            ) { checked ->
                                if (checked) deniedSubmenus.remove(key) else if (key !in deniedSubmenus) deniedSubmenus.add(key)
                            }
                        }
                    }
                }
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cfg.anakSub.forEach { anak ->
                        val allowed = anak.id !in deniedAnakSub
                        PermissionToggleRow(
                            label = "[${menuLabelOf(anak.menuId)} / ${anak.submenuId}] ${anak.label}",
                            checked = allowed
                        ) { checked ->
                            if (checked) deniedAnakSub.remove(anak.id) else if (anak.id !in deniedAnakSub) deniedAnakSub.add(anak.id)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Batal") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSave(deniedMenus.toList(), deniedSubmenus.toList(), deniedAnakSub.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simpan Hak Akses", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Emerald700, checkedThumbColor = Color.White)
            )
        }
    }
}

@Composable
private fun AdminUserFormModal(
    editingUser: UserAccount?,
    db: MmqaDatabase,
    viewModel: MmqaViewModel,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(editingUser?.username ?: "") }
    var password by remember { mutableStateOf(editingUser?.password ?: "") }
    var name by remember { mutableStateOf(editingUser?.name ?: "") }
    var jabatan by remember { mutableStateOf(editingUser?.jabatan ?: "") }
    var role by remember { mutableStateOf(editingUser?.role ?: "muallim") }
    var firqohId by remember { mutableStateOf(editingUser?.firqohId ?: "") }

    MmqaModalDialog(
        title = if (editingUser != null) "Edit Akun Pengguna" else "Tambah Akun Pengguna Baru",
        onDismiss = onDismiss
    ) {
        Surface(color = Amber50, border = BorderStroke(1.dp, Amber200), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Aturan Password Aktif: ${viewModel.getPasswordRuleSummary()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Amber900,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = jabatan, onValueChange = { jabatan = it }, label = { Text("Jabatan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactDropdownSelector(
                label = "Peran Sistem",
                selectedText = if (role == "dewan_harian") "Dewan Harian (Admin)" else "Mu'allim Firqoh",
                options = listOf("muallim" to "Mu'allim Firqoh", "dewan_harian" to "Dewan Harian (Admin)"),
                onSelect = { role = it },
                modifier = Modifier.weight(1f)
            )
            CompactDropdownSelector(
                label = "Firqoh (Opsional)",
                selectedText = if (firqohId.isBlank()) "Semua" else viewModel.getFirqohName(firqohId),
                options = listOf("" to "-- Semua --") + db.firqoh.map { it.id to it.nama },
                onSelect = { firqohId = it },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Batal") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (username.isNotBlank() && name.isNotBlank()) {
                        if (viewModel.saveAdminUser(editingUser?.id, username, password, name, jabatan, role, firqohId)) {
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
            ) { Text("Simpan Akun", fontWeight = FontWeight.ExtraBold) }
        }
    }
}
