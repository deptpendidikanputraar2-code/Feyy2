package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

// =============================================================================
// MODUL MATERI PERFIRQOH
// =============================================================================
@Composable
fun MateriScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean
) {
    val canManage = viewModel.canAccessAnakSub("materi_manage")
    val allowDelete = db.adminConfig.permissions.allowDeleteRecords && viewModel.canAccessAnakSub("materi_delete")
    val list = if (uiState.materiFirqoh == "ALL") db.materi else db.materi.filter { it.firqohId == uiState.materiFirqoh }

    var editingMateri by remember { mutableStateOf<MateriItem?>(null) }
    var showMateriModal by remember { mutableStateOf(false) }
    var deletingMateri by remember { mutableStateOf<MateriItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val options = listOf("ALL" to "Semua Firqoh") + db.firqoh.map { it.id to it.nama }
            CompactDropdownSelector(
                label = "",
                selectedText = options.firstOrNull { it.first == uiState.materiFirqoh }?.second ?: "Semua Firqoh",
                options = options,
                onSelect = { fid -> viewModel.updateState { it.copy(materiFirqoh = fid) } },
                modifier = Modifier.weight(1f)
            )
            if (canManage) {
                Button(
                    onClick = {
                        editingMateri = null
                        showMateriModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text("+ Tambah Materi", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            list.chunked(if (isWideScreen) 2 else 1).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { m ->
                        SectionCard(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StatusPill(viewModel.getFirqohName(m.firqohId), Emerald100, Emerald900)
                                Text(
                                    text = "${m.progress}% Selesai",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Emerald800
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = m.kitab, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text(text = "Bab: ${m.bab}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber900)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Target: ${m.target}", fontSize = 12.sp, color = Slate700)
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { (m.progress / 100f).coerceIn(0f, 1f) },
                                color = Emerald700,
                                trackColor = Slate100,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                            if (canManage || allowDelete) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    if (canManage) {
                                        TextButton(onClick = {
                                            editingMateri = m
                                            showMateriModal = true
                                        }) {
                                            Text("✏️ Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        }
                                    }
                                    if (allowDelete) {
                                        TextButton(onClick = { deletingMateri = m }) {
                                            Text("🗑️ Hapus", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rose700)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isWideScreen && rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showMateriModal) {
        var firqohId by remember { mutableStateOf(editingMateri?.firqohId ?: db.firqoh.firstOrNull()?.id ?: "FRQ-1") }
        var kitab by remember { mutableStateOf(editingMateri?.kitab ?: "") }
        var bab by remember { mutableStateOf(editingMateri?.bab ?: "") }
        var target by remember { mutableStateOf(editingMateri?.target ?: "") }
        var progress by remember { mutableStateOf((editingMateri?.progress ?: 0).toString()) }

        MmqaModalDialog(
            title = if (editingMateri != null) "Edit Materi Perfirqoh" else "Tambah Materi Perfirqoh Baru",
            onDismiss = { showMateriModal = false }
        ) {
            CompactDropdownSelector(
                label = "Firqoh",
                selectedText = viewModel.getFirqohName(firqohId),
                options = db.firqoh.map { it.id to it.nama },
                onSelect = { firqohId = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(value = kitab, onValueChange = { kitab = it }, label = { Text("Nama Kitab / Mapel") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = bab, onValueChange = { bab = it }, label = { Text("Bab / Materi Pokok") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target Capaian Santri") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = progress,
                onValueChange = { progress = it.filter { ch -> ch.isDigit() } },
                label = { Text("Progres Pencapaian (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showMateriModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (kitab.isNotBlank() && bab.isNotBlank()) {
                            viewModel.saveMateri(editingMateri?.id, firqohId, kitab, bab, target, progress.toIntOrNull() ?: 0)
                            showMateriModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Simpan Materi", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    deletingMateri?.let { item ->
        ConfirmActionDialog(
            title = "Hapus Materi",
            message = "Yakin ingin menghapus materi \"${item.kitab}\"?",
            onDismiss = { deletingMateri = null },
            onConfirm = { viewModel.deleteMateri(item.id) }
        )
    }
}

// =============================================================================
// MODUL DATA INDUK TERPADU
// =============================================================================
@Composable
fun DataIndukScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel
) {
    val canManage = viewModel.canAccessAnakSub("induk_kelola")
    val allowDelete = db.adminConfig.permissions.allowDeleteRecords && viewModel.canAccessAnakSub("induk_hapus")
    val enabledSubmenus = viewModel.getAccessibleSubmenus("induk")
    if (enabledSubmenus.isEmpty()) {
        SectionCard {
            Text(
                text = "🔒 Tidak ada submenu Data Induk yang diizinkan untuk akun Anda.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500
            )
        }
        return
    }
    val activeTab = if (enabledSubmenus.any { it.id == uiState.indukTab }) {
        uiState.indukTab
    } else {
        enabledSubmenus.first().id
    }

    var formMode by remember { mutableStateOf<Pair<String, String?>?>(null) } // (tipe, id)
    var deletingItem by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubmenuTabsRow(
            items = enabledSubmenus.map { it.id to it.label },
            selectedId = activeTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(indukTab = tabId) } }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.indukSearch,
                onValueChange = { q -> viewModel.updateState { it.copy(indukSearch = q) } },
                placeholder = { Text("🔍 Cari nama / nomor...", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            if (canManage) {
                Button(
                    onClick = { formMode = activeTab to null },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("+ Tambah", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        val q = uiState.indukSearch.trim().lowercase()
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (activeTab) {
                    "santri" -> {
                        val list = db.santri.filter { q.isEmpty() || it.nama.lowercase().contains(q) || it.nis.contains(q) }
                        list.forEach { s ->
                            IndukItemCard(
                                code = "NIS ${s.nis}",
                                title = s.nama,
                                line1 = "${viewModel.getFirqohName(s.firqohId)} • Angkatan ${s.angkatan}",
                                line2 = "Wali: ${s.wali} (${s.hpWali})",
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "santri" to s.id },
                                onDelete = { deletingItem = Triple("santri", s.id, s.nama) }
                            )
                        }
                    }
                    "muallim" -> {
                        val list = db.muallim.filter { q.isEmpty() || it.nama.lowercase().contains(q) || it.niy.lowercase().contains(q) }
                        list.forEach { m ->
                            IndukItemCard(
                                code = "NIY ${m.niy}",
                                title = m.nama,
                                line1 = viewModel.getFirqohName(m.firqohId),
                                line2 = "Bidang: ${m.bidang} • HP: ${m.hp}",
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "muallim" to m.id },
                                onDelete = { deletingItem = Triple("muallim", m.id, m.nama) }
                            )
                        }
                    }
                    "dewan" -> {
                        val list = db.dewanHarian.filter { q.isEmpty() || it.nama.lowercase().contains(q) }
                        list.forEach { d ->
                            IndukItemCard(
                                code = d.kode,
                                title = d.nama,
                                line1 = d.jabatan,
                                line2 = "HP: ${d.hp}",
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "dewan" to d.id },
                                onDelete = { deletingItem = Triple("dewan", d.id, d.nama) }
                            )
                        }
                    }
                    else -> {
                        val list = db.firqoh.filter { q.isEmpty() || it.nama.lowercase().contains(q) }
                        list.forEach { f ->
                            IndukItemCard(
                                code = f.kode,
                                title = f.nama,
                                line1 = "${f.tingkat} • ${f.jadwal}",
                                line2 = "Ruangan: ${f.ruangan}",
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "firqoh" to f.id },
                                onDelete = { deletingItem = Triple("firqoh", f.id, f.nama) }
                            )
                        }
                    }
                }
            }
        }
    }

    formMode?.let { (tipe, editId) ->
        IndukFormModal(
            tipe = tipe,
            editId = editId,
            db = db,
            viewModel = viewModel,
            onDismiss = { formMode = null }
        )
    }

    deletingItem?.let { (tipe, id, nama) ->
        ConfirmActionDialog(
            title = "Hapus Data Induk",
            message = "Yakin ingin menghapus data \"$nama\"?",
            onDismiss = { deletingItem = null },
            onConfirm = { viewModel.deleteIndukItem(tipe, id) }
        )
    }
}

@Composable
private fun IndukItemCard(
    code: String,
    title: String,
    line1: String,
    line2: String,
    canManage: Boolean,
    allowDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = code, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = Emerald800)
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                Text(text = line1, fontSize = 11.sp, color = Slate700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = line2, fontSize = 11.sp, color = Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (canManage || allowDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canManage) {
                        TextButton(onClick = onEdit) {
                            Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                    if (allowDelete) {
                        TextButton(onClick = onDelete) {
                            Text("Hapus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndukFormModal(
    tipe: String,
    editId: String?,
    db: MmqaDatabase,
    viewModel: MmqaViewModel,
    onDismiss: () -> Unit
) {
    when (tipe) {
        "santri" -> {
            val s = editId?.let { id -> db.santri.firstOrNull { it.id == id } }
            var nis by remember { mutableStateOf(s?.nis ?: "20260${db.santri.size + 1}") }
            var angkatan by remember { mutableStateOf(s?.angkatan ?: "2026") }
            var nama by remember { mutableStateOf(s?.nama ?: "") }
            var firqohId by remember { mutableStateOf(s?.firqohId ?: db.firqoh.firstOrNull()?.id ?: "FRQ-1") }
            var wali by remember { mutableStateOf(s?.wali ?: "") }
            var hpWali by remember { mutableStateOf(s?.hpWali ?: "") }

            MmqaModalDialog(title = if (s != null) "Edit Data Santri" else "Tambah Santri Baru", onDismiss = onDismiss) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nis, onValueChange = { nis = it }, label = { Text("NIS") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = angkatan, onValueChange = { angkatan = it }, label = { Text("Angkatan") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Lengkap Santri") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                CompactDropdownSelector(
                    label = "Firqoh",
                    selectedText = viewModel.getFirqohName(firqohId),
                    options = db.firqoh.map { it.id to it.nama },
                    onSelect = { firqohId = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = wali, onValueChange = { wali = it }, label = { Text("Nama Wali") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = hpWali, onValueChange = { hpWali = it }, label = { Text("HP Wali") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && nis.isNotBlank()) {
                                viewModel.saveSantri(editId, nis, angkatan, nama, firqohId, wali, hpWali)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                    ) { Text("Simpan", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        "muallim" -> {
            val m = editId?.let { id -> db.muallim.firstOrNull { it.id == id } }
            var niy by remember { mutableStateOf(m?.niy ?: "M-20260${db.muallim.size + 1}") }
            var nama by remember { mutableStateOf(m?.nama ?: "") }
            var firqohId by remember { mutableStateOf(m?.firqohId ?: db.firqoh.firstOrNull()?.id ?: "FRQ-1") }
            var bidang by remember { mutableStateOf(m?.bidang ?: "") }
            var hp by remember { mutableStateOf(m?.hp ?: "") }

            MmqaModalDialog(title = if (m != null) "Edit Data Mu'allim" else "Tambah Mu'allim Baru", onDismiss = onDismiss) {
                OutlinedTextField(value = niy, onValueChange = { niy = it }, label = { Text("NIY") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Lengkap Mu'allim") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                CompactDropdownSelector(
                    label = "Firqoh Ampuan",
                    selectedText = viewModel.getFirqohName(firqohId),
                    options = db.firqoh.map { it.id to it.nama },
                    onSelect = { firqohId = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = bidang, onValueChange = { bidang = it }, label = { Text("Bidang Keilmuan") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = hp, onValueChange = { hp = it }, label = { Text("No. HP") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && niy.isNotBlank()) {
                                viewModel.saveMuallim(editId, niy, nama, firqohId, bidang, hp)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                    ) { Text("Simpan", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        "dewan" -> {
            val d = editId?.let { id -> db.dewanHarian.firstOrNull { it.id == id } }
            var kode by remember { mutableStateOf(d?.kode ?: "DH-0${db.dewanHarian.size + 1}") }
            var nama by remember { mutableStateOf(d?.nama ?: "") }
            var jabatan by remember { mutableStateOf(d?.jabatan ?: "") }
            var hp by remember { mutableStateOf(d?.hp ?: "") }

            MmqaModalDialog(title = if (d != null) "Edit Dewan Harian" else "Tambah Dewan Harian", onDismiss = onDismiss) {
                OutlinedTextField(value = kode, onValueChange = { kode = it }, label = { Text("Kode Pengurus") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Lengkap") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = jabatan, onValueChange = { jabatan = it }, label = { Text("Jabatan Dewan Harian") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = hp, onValueChange = { hp = it }, label = { Text("No. HP") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && kode.isNotBlank()) {
                                viewModel.saveDewanHarian(editId, kode, nama, jabatan, hp)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                    ) { Text("Simpan", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        else -> {
            val f = editId?.let { id -> db.firqoh.firstOrNull { it.id == id } }
            var kode by remember { mutableStateOf(f?.kode ?: "F-0${db.firqoh.size + 1}") }
            var nama by remember { mutableStateOf(f?.nama ?: "") }
            var tingkat by remember { mutableStateOf(f?.tingkat ?: "") }
            var jadwal by remember { mutableStateOf(f?.jadwal ?: "") }
            var ruangan by remember { mutableStateOf(f?.ruangan ?: "") }

            MmqaModalDialog(title = if (f != null) "Edit Data Firqoh" else "Tambah Firqoh Baru", onDismiss = onDismiss) {
                OutlinedTextField(value = kode, onValueChange = { kode = it }, label = { Text("Kode Firqoh") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Firqoh") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = tingkat, onValueChange = { tingkat = it }, label = { Text("Tingkat") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = jadwal, onValueChange = { jadwal = it }, label = { Text("Jadwal") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = ruangan, onValueChange = { ruangan = it }, label = { Text("Ruangan") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && kode.isNotBlank()) {
                                viewModel.saveFirqoh(editId, kode, nama, tingkat, jadwal, ruangan)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                    ) { Text("Simpan", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}
