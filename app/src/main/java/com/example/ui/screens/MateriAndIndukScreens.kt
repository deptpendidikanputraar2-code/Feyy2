package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val list = db.materi.filter { viewModel.matchesFirqohFilter(it.firqohId, uiState.materiFirqoh) }

    var editingMateri by remember { mutableStateOf<MateriItem?>(null) }
    var showMateriModal by remember { mutableStateOf(false) }
    var deletingMateri by remember { mutableStateOf<MateriItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val options = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = false)
            CompactDropdownSelector(
                label = "",
                selectedText = options.firstOrNull { it.first == uiState.materiFirqoh }?.second ?: viewModel.getFirqohFilterDisplayLabel(uiState.materiFirqoh),
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
        val materiFirqohOptions = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = false)
        val defaultFirqohSelection = editingMateri?.firqohId
            ?: uiState.materiFirqoh.takeIf { it.isNotBlank() }
            ?: db.firqoh.firstOrNull()?.id
            ?: "ALL"
        var firqohId by remember(editingMateri, uiState.materiFirqoh) { mutableStateOf(defaultFirqohSelection) }
        var kitab by remember(editingMateri) { mutableStateOf(editingMateri?.kitab ?: "") }
        var bab by remember(editingMateri) { mutableStateOf(editingMateri?.bab ?: "") }
        var target by remember(editingMateri) { mutableStateOf(editingMateri?.target ?: "") }
        var progress by remember(editingMateri) { mutableStateOf((editingMateri?.progress ?: 0).toString()) }

        MmqaModalDialog(
            title = if (editingMateri != null) "Edit Materi Perfirqoh" else "Tambah Materi Perfirqoh Baru",
            onDismiss = { showMateriModal = false }
        ) {
            CompactDropdownSelector(
                label = "Firqoh / Grup Firqoh",
                selectedText = materiFirqohOptions.firstOrNull { it.first == firqohId }?.second
                    ?: viewModel.getFirqohName(firqohId),
                options = materiFirqohOptions,
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
    val context = LocalContext.current
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
        val santriFirqohOptions = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = true)
        val santriFilterLabel = viewModel.getFirqohFilterDisplayLabel(uiState.santriIndukFirqoh)
        val firqohMadingOptions = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = false)
        val firqohMadingLabel = viewModel.getFirqohFilterDisplayLabel(uiState.firqohIndukFilter)

        if (activeTab == "santri") {
            CompactDropdownSelector(
                label = "Filter Firqoh Data Santri",
                selectedText = santriFirqohOptions.firstOrNull { it.first == uiState.santriIndukFirqoh }?.second ?: santriFilterLabel,
                options = santriFirqohOptions,
                onSelect = { fid -> viewModel.updateState { it.copy(santriIndukFirqoh = fid) } },
                modifier = Modifier.fillMaxWidth()
            )
        } else if (activeTab == "firqoh") {
            CompactDropdownSelector(
                label = "Pilih Firqoh (Semua / Grup / Firqoh Tertentu) untuk PDF Mading",
                selectedText = firqohMadingOptions.firstOrNull { it.first == uiState.firqohIndukFilter }?.second ?: firqohMadingLabel,
                options = firqohMadingOptions,
                onSelect = { fid -> viewModel.updateState { it.copy(firqohIndukFilter = fid) } },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (activeTab) {
                    "santri" -> {
                        val list = db.santri.filter {
                            viewModel.matchesFirqohFilter(it.firqohId, uiState.santriIndukFirqoh) &&
                                (q.isEmpty() ||
                                    it.nama.lowercase().contains(q) ||
                                    it.nis.lowercase().contains(q) ||
                                    it.idSantri.lowercase().contains(q) ||
                                    it.uidNfc.lowercase().contains(q))
                        }
                        WaPdfActionRow(
                            onShareWa = {
                                val msg = MmqaReportFormatters.buildDataSantriWa(db, santriFilterLabel, list)
                                shareTextToWhatsApp(context, msg, "Kirim Data Santri ke WA")
                            },
                            onDownloadPdf = {
                                val html = MmqaReportFormatters.buildDataSantriPdfHtml(db, santriFilterLabel, list)
                                printHtmlDocument(context, "Data_Santri_MMQA_AR2", html)
                            },
                            waTestTag = "induk_santri_wa_btn",
                            pdfTestTag = "induk_santri_pdf_btn"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        list.forEach { s ->
                            val badgeExtras = listOfNotNull(
                                s.idSantri.takeIf { it.isNotBlank() }?.let { "Barcode: $it" },
                                s.uidNfc.takeIf { it.isNotBlank() }?.let { "NFC: $it" }
                            ).joinToString(" • ")
                            IndukItemCard(
                                code = "NIS ${s.nis}" + (if (s.uidNfc.isNotBlank()) " • 📡 NFC" else ""),
                                title = s.nama,
                                line1 = "${viewModel.getFirqohName(s.firqohId)} • Angkatan ${s.angkatan}",
                                line2 = "Wali: ${s.wali} (${s.hpWali})" + (if (badgeExtras.isNotBlank()) " • $badgeExtras" else ""),
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "santri" to s.id },
                                onDelete = { deletingItem = Triple("santri", s.id, s.nama) }
                            )
                        }
                    }
                    "muallim" -> {
                        val list = db.muallim.filter {
                            q.isEmpty() ||
                                it.nama.lowercase().contains(q) ||
                                it.niy.lowercase().contains(q) ||
                                it.idSantri.lowercase().contains(q) ||
                                it.uidNfc.lowercase().contains(q)
                        }
                        WaPdfActionRow(
                            onShareWa = {
                                val msg = MmqaReportFormatters.buildDataMuallimWa(db, list)
                                shareTextToWhatsApp(context, msg, "Kirim Data Mu'allim ke WA")
                            },
                            onDownloadPdf = {
                                val html = MmqaReportFormatters.buildDataMuallimPdfHtml(db, list)
                                printHtmlDocument(context, "Data_Muallim_MMQA_AR2", html)
                            },
                            waTestTag = "induk_muallim_wa_btn",
                            pdfTestTag = "induk_muallim_pdf_btn"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        list.forEach { m ->
                            val badgeExtras = listOfNotNull(
                                m.idSantri.takeIf { it.isNotBlank() }?.let { "Barcode: $it" },
                                m.uidNfc.takeIf { it.isNotBlank() }?.let { "NFC: $it" }
                            ).joinToString(" • ")
                            IndukItemCard(
                                code = "NIY ${m.niy}" + (if (m.uidNfc.isNotBlank()) " • 📡 NFC" else ""),
                                title = m.nama,
                                line1 = viewModel.getFirqohName(m.firqohId),
                                line2 = "Bidang: ${m.bidang} • HP: ${m.hp}" + (if (badgeExtras.isNotBlank()) " • $badgeExtras" else ""),
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "muallim" to m.id },
                                onDelete = { deletingItem = Triple("muallim", m.id, m.nama) }
                            )
                        }
                    }
                    "dewan" -> {
                        val list = db.dewanHarian.filter {
                            q.isEmpty() ||
                                it.nama.lowercase().contains(q) ||
                                it.kode.lowercase().contains(q) ||
                                it.idSantri.lowercase().contains(q) ||
                                it.uidNfc.lowercase().contains(q)
                        }
                        WaPdfActionRow(
                            onShareWa = {
                                val msg = MmqaReportFormatters.buildDataDewanWa(list)
                                shareTextToWhatsApp(context, msg, "Kirim Data Dewan Harian ke WA")
                            },
                            onDownloadPdf = {
                                val html = MmqaReportFormatters.buildDataDewanPdfHtml(list)
                                printHtmlDocument(context, "Data_Dewan_Harian_MMQA_AR2", html)
                            },
                            waTestTag = "induk_dewan_wa_btn",
                            pdfTestTag = "induk_dewan_pdf_btn"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        list.forEach { d ->
                            val badgeExtras = listOfNotNull(
                                d.idSantri.takeIf { it.isNotBlank() }?.let { "Barcode: $it" },
                                d.uidNfc.takeIf { it.isNotBlank() }?.let { "NFC: $it" }
                            ).joinToString(" • ")
                            IndukItemCard(
                                code = d.kode + (if (d.uidNfc.isNotBlank()) " • 📡 NFC" else ""),
                                title = d.nama,
                                line1 = d.jabatan,
                                line2 = "HP: ${d.hp}" + (if (badgeExtras.isNotBlank()) " • $badgeExtras" else ""),
                                canManage = canManage,
                                allowDelete = allowDelete,
                                onEdit = { formMode = "dewan" to d.id },
                                onDelete = { deletingItem = Triple("dewan", d.id, d.nama) }
                            )
                        }
                    }
                    else -> {
                        val list = db.firqoh.filter { f ->
                            viewModel.matchesFirqohFilter(f.id, uiState.firqohIndukFilter) &&
                                (q.isEmpty() ||
                                    f.nama.lowercase().contains(q) ||
                                    f.kode.lowercase().contains(q) ||
                                    f.tingkat.lowercase().contains(q) ||
                                    f.ruangan.lowercase().contains(q))
                        }
                        WaPdfActionRow(
                            onShareWa = {
                                val msg = MmqaReportFormatters.buildFirqohMadingWa(db, firqohMadingLabel, list)
                                shareTextToWhatsApp(context, msg, "Kirim Info Mading Firqoh ke WA")
                            },
                            onDownloadPdf = {
                                val html = MmqaReportFormatters.buildFirqohMadingPdfHtml(db, firqohMadingLabel, list)
                                val safeTag = firqohMadingLabel.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Semua" }
                                printHtmlDocument(context, "Mading_Firqoh_${safeTag}_MMQA_AR2", html)
                            },
                            waLabel = "Kirim Info WA",
                            pdfLabel = "Unduh PDF Mading",
                            waTestTag = "induk_firqoh_wa_btn",
                            pdfTestTag = "induk_firqoh_pdf_btn"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        list.forEach { f ->
                            val santriCount = db.santri.count { it.firqohId == f.id }
                            val muallimNames = db.muallim.filter { it.firqohId == f.id }.joinToString(", ") { it.nama }.ifBlank { "-" }
                            IndukItemCard(
                                code = f.kode,
                                title = f.nama,
                                line1 = "${f.tingkat} • ${f.jadwal}",
                                line2 = "Ruangan: ${f.ruangan} • $santriCount Santri • Mu'allim: $muallimNames",
                                canManage = canManage,
                                allowDelete = allowDelete,
                                extraAction = {
                                    OutlinedButton(
                                        onClick = {
                                            val html = MmqaReportFormatters.buildFirqohMadingPdfHtml(db, f.nama, listOf(f))
                                            val safeName = f.nama.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { f.kode }
                                            printHtmlDocument(context, "Mading_${safeName}_MMQA_AR2", html)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Rose200),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Rose50,
                                            contentColor = Rose800
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 36.dp)
                                            .testTag("induk_firqoh_single_pdf_${f.id}")
                                    ) {
                                        Text("PDF Mading", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                },
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
    extraAction: (@Composable () -> Unit)? = null,
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
            if (extraAction != null || canManage || allowDelete) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    extraAction?.invoke()
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
            var idSantriBarcode by remember { mutableStateOf(s?.idSantri ?: "") }
            var uidNfc by remember { mutableStateOf(s?.uidNfc ?: "") }

            MmqaModalDialog(title = if (s != null) "Detail & Edit Santri" else "Tambah Santri Baru", onDismiss = onDismiss) {
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
                Spacer(modifier = Modifier.height(10.dp))
                NfcAndBarcodeLinkSection(
                    tipe = "santri",
                    editId = editId,
                    displayName = nama.ifBlank { "Santri Baru" },
                    idSantriBarcode = idSantriBarcode,
                    onIdSantriBarcodeChange = { idSantriBarcode = it },
                    uidNfc = uidNfc,
                    onUidNfcChange = { uidNfc = it },
                    previewCard = s?.let {
                        MmqaBarcodeEngine.buildCardForSantri(
                            db,
                            it.copy(idSantri = idSantriBarcode.trim(), uidNfc = uidNfc.trim())
                        )
                    },
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && nis.isNotBlank()) {
                                viewModel.saveSantri(
                                    id = editId,
                                    nis = nis,
                                    angkatan = angkatan,
                                    nama = nama,
                                    firqohId = firqohId,
                                    wali = wali,
                                    hpWali = hpWali,
                                    idSantri = idSantriBarcode,
                                    uidNfc = uidNfc
                                )
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
            var idSantriBarcode by remember { mutableStateOf(m?.idSantri ?: "") }
            var uidNfc by remember { mutableStateOf(m?.uidNfc ?: "") }

            MmqaModalDialog(title = if (m != null) "Detail & Edit Mu'allim" else "Tambah Mu'allim Baru", onDismiss = onDismiss) {
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
                Spacer(modifier = Modifier.height(10.dp))
                NfcAndBarcodeLinkSection(
                    tipe = "muallim",
                    editId = editId,
                    displayName = nama.ifBlank { "Mu'allim Baru" },
                    idSantriBarcode = idSantriBarcode,
                    onIdSantriBarcodeChange = { idSantriBarcode = it },
                    uidNfc = uidNfc,
                    onUidNfcChange = { uidNfc = it },
                    previewCard = m?.let {
                        MmqaBarcodeEngine.buildCardForMuallim(
                            db,
                            it.copy(idSantri = idSantriBarcode.trim(), uidNfc = uidNfc.trim())
                        )
                    },
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && niy.isNotBlank()) {
                                viewModel.saveMuallim(
                                    id = editId,
                                    niy = niy,
                                    nama = nama,
                                    firqohId = firqohId,
                                    bidang = bidang,
                                    hp = hp,
                                    idSantri = idSantriBarcode,
                                    uidNfc = uidNfc
                                )
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
            var idSantriBarcode by remember { mutableStateOf(d?.idSantri ?: "") }
            var uidNfc by remember { mutableStateOf(d?.uidNfc ?: "") }

            MmqaModalDialog(title = if (d != null) "Detail & Edit Dewan Harian" else "Tambah Dewan Harian", onDismiss = onDismiss) {
                OutlinedTextField(value = kode, onValueChange = { kode = it }, label = { Text("Kode Pengurus") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Lengkap") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = jabatan, onValueChange = { jabatan = it }, label = { Text("Jabatan Dewan Harian") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = hp, onValueChange = { hp = it }, label = { Text("No. HP") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                NfcAndBarcodeLinkSection(
                    tipe = "dewan",
                    editId = editId,
                    displayName = nama.ifBlank { "Dewan Harian" },
                    idSantriBarcode = idSantriBarcode,
                    onIdSantriBarcodeChange = { idSantriBarcode = it },
                    uidNfc = uidNfc,
                    onUidNfcChange = { uidNfc = it },
                    previewCard = d?.let {
                        MmqaBarcodeEngine.buildCardForDewan(
                            db,
                            it.copy(idSantri = idSantriBarcode.trim(), uidNfc = uidNfc.trim())
                        )
                    },
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nama.isNotBlank() && kode.isNotBlank()) {
                                viewModel.saveDewanHarian(
                                    id = editId,
                                    kode = kode,
                                    nama = nama,
                                    jabatan = jabatan,
                                    hp = hp,
                                    idSantri = idSantriBarcode,
                                    uidNfc = uidNfc
                                )
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

@Composable
private fun NfcAndBarcodeLinkSection(
    tipe: String,
    editId: String?,
    displayName: String,
    idSantriBarcode: String,
    onIdSantriBarcodeChange: (String) -> Unit,
    uidNfc: String,
    onUidNfcChange: (String) -> Unit,
    previewCard: MmqaBarcodeCard?,
    viewModel: MmqaViewModel
) {
    val context = LocalContext.current
    var isNfcLinkMode by remember { mutableStateOf(false) }
    val nfcStatus = remember(context, isNfcLinkMode) { NfcCardReaderHelper.getNfcStatus(context) }
    var showBarcodeCapture by remember { mutableStateOf(false) }
    var showCardPreview by remember { mutableStateOf(false) }
    var linkFeedbackMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGen?.release() } }
    }

    LaunchedEffect(isNfcLinkMode, editId) {
        if (isNfcLinkMode) {
            NfcCardReaderHelper.enableExclusiveReaderModeForContext(context)
            NfcCardReaderHelper.events.collect { tagEvent ->
                val uid = tagEvent.uidHex
                val dupOwner = viewModel.findDuplicateNfcOwner(uid, tipe, editId)
                if (dupOwner != null) {
                    NfcCardReaderHelper.triggerHapticFeedback(context, isSuccess = false)
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_ERROR, 260) }
                    linkFeedbackMsg = false to "Kartu NFC ($uid) sudah tertaut ke $dupOwner!"
                    viewModel.showToast("Kartu NFC ($uid) sudah dipakai oleh $dupOwner!", isError = true)
                } else {
                    NfcCardReaderHelper.triggerHapticFeedback(context, isSuccess = true)
                    runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 200) }
                    onUidNfcChange(uid)
                    if (editId != null) {
                        val saved = viewModel.linkNfcToIndukRecord(tipe, editId, uid)
                        if (saved) {
                            linkFeedbackMsg = true to "UID NFC $uid (${tagEvent.techLabel}) berhasil tersimpan ke database!"
                        }
                    } else {
                        linkFeedbackMsg = true to "UID NFC $uid terdeteksi! Klik Simpan untuk menyimpan data."
                    }
                    isNfcLinkMode = false
                }
            }
        }
    }

    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🪪 Identitas Kartu Barcode & NFC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Emerald900,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { NfcCardReaderHelper.openOtherNfcServicesSettings(context) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                    ) {
                        Text("Layanan NFC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Sky700)
                    }
                    if (previewCard != null) {
                        TextButton(
                            onClick = { showCardPreview = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Lihat Kartu QR", tint = Emerald800, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kartu QR", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Emerald800)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = idSantriBarcode,
                onValueChange = onIdSantriBarcodeChange,
                label = { Text("id_santri (Kode Bahan Barcode)", fontSize = 11.sp) },
                placeholder = { Text("Kosong = otomatis MMQA|...", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("induk_id_santri_input")
            )

            OutlinedTextField(
                value = uidNfc,
                onValueChange = { onUidNfcChange(it.uppercase().replace(":", "").replace(" ", "")) },
                label = { Text("uid_nfc (UID Hex MIFARE Classic 1k)", fontSize = 11.sp) },
                placeholder = { Text("Contoh: F722FF90", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("induk_uid_nfc_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        isNfcLinkMode = !isNfcLinkMode
                        if (isNfcLinkMode) {
                            linkFeedbackMsg = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNfcLinkMode) Amber500 else Sky700,
                        contentColor = if (isNfcLinkMode) Amber950 else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("tautkan_nfc_button")
                ) {
                    Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isNfcLinkMode) "Menunggu Kartu..." else "Tautkan Kartu NFC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        isNfcLinkMode = false
                        showBarcodeCapture = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald800,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("scan_barcode_link_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scan Barcode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isNfcLinkMode) {
                Surface(
                    color = Amber100,
                    border = BorderStroke(1.dp, Amber400),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (nfcStatus.first) {
                            "📡 Mode Tautkan NFC AKTIF: Tempelkan kartu NXP MIFARE Classic 1k ke bagian belakang HP untuk mengisi uid_nfc otomatis."
                        } else {
                            "⚠️ ${nfcStatus.second}. Anda tetap dapat mengetik UID NFC Hex secara manual."
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber950,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            linkFeedbackMsg?.let { (isOk, msg) ->
                Surface(
                    color = if (isOk) Emerald50 else Rose50,
                    border = BorderStroke(1.dp, if (isOk) Emerald700.copy(alpha = 0.4f) else Rose200),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isOk) "✅ $msg" else "⚠️ $msg",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOk) Emerald900 else Rose800,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    if (showBarcodeCapture) {
        SingleBarcodeCaptureDialog(
            targetName = displayName,
            onBarcodeCaptured = { scannedCode ->
                onIdSantriBarcodeChange(scannedCode)
                if (editId != null) {
                    val saved = viewModel.linkBarcodeToIndukRecord(tipe, editId, scannedCode)
                    if (saved) {
                        linkFeedbackMsg = true to "Barcode '$scannedCode' tersimpan ke id_santri & siap dijadikan bahan barcode baru!"
                    }
                } else {
                    linkFeedbackMsg = true to "Barcode '$scannedCode' terdeteksi untuk id_santri! Klik Simpan untuk menyimpan."
                }
            },
            onDismiss = { showBarcodeCapture = false }
        )
    }

    if (showCardPreview && previewCard != null) {
        BarcodeCardPreviewDialog(
            card = previewCard,
            onDismiss = { showCardPreview = false }
        )
    }
}

