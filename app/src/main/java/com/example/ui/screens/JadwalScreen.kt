package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.JadwalItem
import com.example.data.MmqaDatabase
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Calendar

private val JADWAL_CATEGORIES = listOf(
    "ALL" to "Semua Agenda",
    "MMQA Harian" to "MMQA Harian",
    "Ujian" to "Ujian",
    "Absensi" to "Absensi",
    "Event Khusus" to "Event Khusus"
)

@Composable
fun JadwalScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val canManage = viewModel.canAccessAnakSub("jadwal_manage")
    val canDelete = viewModel.canAccessAnakSub("jadwal_delete") && db.adminConfig.permissions.allowDeleteRecords
    val canExport = viewModel.canAccessAnakSub("jadwal_export")

    var selectedCategory by remember { mutableStateOf("ALL") }
    var firqohFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<JadwalItem?>(null) }
    var showFormModal by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<JadwalItem?>(null) }

    val firqohOptions = remember(db.firqoh, db.santri) {
        viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = false)
    }

    val filteredJadwal = remember(db.jadwal, selectedCategory, firqohFilter, search) {
        val q = search.trim().lowercase()
        db.jadwal.filter { j ->
            val catMatch = selectedCategory == "ALL" || j.kategori.equals(selectedCategory, ignoreCase = true)
            val firqohMatch = firqohFilter == "ALL" || viewModel.matchesFirqohFilter(j.firqohId, firqohFilter)
            val queryMatch = q.isEmpty() ||
                j.judul.lowercase().contains(q) ||
                j.hari.lowercase().contains(q) ||
                j.tanggal.lowercase().contains(q) ||
                j.tempat.lowercase().contains(q) ||
                j.penanggungJawab.lowercase().contains(q) ||
                j.keterangan.lowercase().contains(q)
            catMatch && firqohMatch && queryMatch
        }
    }

    val kategoriLabel = JADWAL_CATEGORIES.firstOrNull { it.first == selectedCategory }?.second ?: selectedCategory

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🗓️ Jadwal Resmi Kegiatan MMQA",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                    Text(
                        text = "MMQA harian, ujian, presensi, dan event khusus lengkap dengan hari, tanggal, jam & tempat.",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
                if (canManage) {
                    Button(
                        onClick = {
                            editingItem = null
                            showFormModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("jadwal_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Tambah Jadwal", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Jadwal", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                JADWAL_CATEGORIES.forEach { (key, label) ->
                    val active = selectedCategory == key
                    Surface(
                        color = if (active) Emerald900 else Slate100,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 38.dp)
                            .clickable { selectedCategory = key }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (active) Amber300 else Slate700
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactDropdownSelector(
                    label = "",
                    selectedText = firqohOptions.firstOrNull { it.first == firqohFilter }?.second
                        ?: viewModel.getFirqohFilterDisplayLabel(firqohFilter),
                    options = firqohOptions,
                    onSelect = { firqohFilter = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("🔍 Cari agenda, tempat, hari, PJ...", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (canExport) {
                Spacer(modifier = Modifier.height(10.dp))
                WaPdfActionRow(
                    onShareWa = {
                        val msg = MmqaReportFormatters.buildJadwalWa(db, kategoriLabel, filteredJadwal)
                        shareTextToWhatsApp(context, msg, "Kirim Jadwal ke WA")
                    },
                    onDownloadPdf = {
                        val html = MmqaReportFormatters.buildJadwalPdfHtml(db, kategoriLabel, filteredJadwal)
                        printHtmlDocument(context, "Jadwal_Kegiatan_MMQA", html)
                    },
                    waTestTag = "jadwal_wa_btn",
                    pdfTestTag = "jadwal_pdf_btn"
                )
            }
        }

        if (filteredJadwal.isEmpty()) {
            SectionCard {
                Text(
                    text = "Belum ada agenda jadwal pada filter ini.",
                    fontSize = 12.sp,
                    color = Slate500,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredJadwal.chunked(if (isWideScreen) 2 else 1).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            JadwalAgendaCard(
                                item = item,
                                firqohLabel = viewModel.getFirqohName(item.firqohId),
                                canManage = canManage,
                                canDelete = canDelete,
                                onEdit = {
                                    editingItem = item
                                    showFormModal = true
                                },
                                onDelete = { deletingItem = item },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isWideScreen && rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showFormModal) {
        JadwalFormModal(
            editingItem = editingItem,
            firqohOptions = firqohOptions,
            onSave = { kategori, judul, hari, tanggal, jamMulai, jamSelesai, tempat, firqohId, pj, ket, isPublic ->
                viewModel.saveJadwal(
                    id = editingItem?.id,
                    kategori = kategori,
                    judul = judul,
                    hari = hari,
                    tanggal = tanggal,
                    jamMulai = jamMulai,
                    jamSelesai = jamSelesai,
                    tempat = tempat,
                    firqohId = firqohId,
                    penanggungJawab = pj,
                    keterangan = ket,
                    isPublicOnLogin = isPublic
                )
                showFormModal = false
            },
            onDismiss = { showFormModal = false }
        )
    }

    deletingItem?.let { item ->
        ConfirmActionDialog(
            title = "Hapus Agenda Jadwal",
            message = "Hapus jadwal \"${item.judul}\"?",
            onDismiss = { deletingItem = null },
            onConfirm = { viewModel.deleteJadwal(item.id) }
        )
    }
}

@Composable
private fun JadwalAgendaCard(
    item: JadwalItem,
    firqohLabel: String,
    canManage: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (catBg, catTxt) = when (item.kategori) {
        "Ujian" -> Rose100 to Rose800
        "Absensi" -> Sky100 to Sky900
        "Event Khusus" -> Amber100 to Amber950
        else -> Emerald100 to Emerald900
    }
    SectionCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusPill(item.kategori, catBg, catTxt)
            StatusPill(firqohLabel, Slate100, Slate700)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.judul,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Slate900
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = Slate50,
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Emerald800, modifier = Modifier.size(15.dp))
                    val tgl = if (item.tanggal.isNotBlank()) "${item.hari}, ${item.tanggal}" else "${item.hari} (Rutin)"
                    Text(tgl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Amber900, modifier = Modifier.size(15.dp))
                    Text("${item.jamMulai} – ${item.jamSelesai} WIB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Rose700, modifier = Modifier.size(15.dp))
                    Text(item.tempat, fontSize = 11.sp, color = Slate700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Sky900, modifier = Modifier.size(15.dp))
                    Text("PJ: ${item.penanggungJawab}", fontSize = 11.sp, color = Slate700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (item.keterangan.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.keterangan,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Slate700
            )
        }
        if (item.isPublicOnLogin) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("🌐 Tampil di Halaman Login Publik", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        }
        if (canManage || canDelete) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (canManage) {
                    TextButton(onClick = onEdit) {
                        Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    }
                }
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("Hapus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                    }
                }
            }
        }
    }
}

@Composable
private fun JadwalFormModal(
    editingItem: JadwalItem?,
    firqohOptions: List<Pair<String, String>>,
    onSave: (
        kategori: String,
        judul: String,
        hari: String,
        tanggal: String,
        jamMulai: String,
        jamSelesai: String,
        tempat: String,
        firqohId: String,
        pj: String,
        ket: String,
        isPublicOnLogin: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var kategori by remember { mutableStateOf(editingItem?.kategori ?: "MMQA Harian") }
    var judul by remember { mutableStateOf(editingItem?.judul ?: "") }
    var hari by remember { mutableStateOf(editingItem?.hari ?: "Sabtu – Kamis") }
    var tanggal by remember { mutableStateOf(editingItem?.tanggal ?: "") }
    var jamMulai by remember { mutableStateOf(editingItem?.jamMulai ?: "07:00") }
    var jamSelesai by remember { mutableStateOf(editingItem?.jamSelesai ?: "08:30") }
    var tempat by remember { mutableStateOf(editingItem?.tempat ?: "") }
    var firqohId by remember { mutableStateOf(editingItem?.firqohId ?: "ALL") }
    var pj by remember { mutableStateOf(editingItem?.penanggungJawab ?: "") }
    var ket by remember { mutableStateOf(editingItem?.keterangan ?: "") }
    var isPublicOnLogin by remember { mutableStateOf(editingItem?.isPublicOnLogin ?: true) }

    MmqaModalDialog(
        title = if (editingItem != null) "✏️ Edit Jadwal Kegiatan" else "🗓️ Tambah Jadwal Baru",
        onDismiss = onDismiss
    ) {
        CompactDropdownSelector(
            label = "Kategori Jadwal",
            selectedText = kategori,
            options = JADWAL_CATEGORIES.drop(1),
            onSelect = { kategori = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = judul,
            onValueChange = { judul = it },
            label = { Text("Nama Agenda / Kegiatan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = hari,
                onValueChange = { hari = it },
                label = { Text("Hari", fontSize = 11.sp) },
                placeholder = { Text("Sabtu – Kamis / Jumat", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Tanggal (Opsional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate300),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                tanggal = String.format("%04d-%02d-%02d", y, m + 1, d)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(tanggal.ifBlank { "Rutin / Pilih" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal", tint = Emerald800, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = jamMulai,
                onValueChange = { jamMulai = it },
                label = { Text("Jam Mulai", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = jamSelesai,
                onValueChange = { jamSelesai = it },
                label = { Text("Jam Selesai", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = tempat,
            onValueChange = { tempat = it },
            label = { Text("Tempat / Lokasi") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        CompactDropdownSelector(
            label = "Sasaran Firqoh / Grup",
            selectedText = firqohOptions.firstOrNull { it.first == firqohId }?.second ?: "Semua Firqoh",
            options = firqohOptions,
            onSelect = { firqohId = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = pj,
            onValueChange = { pj = it },
            label = { Text("Penanggung Jawab (PJ)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = ket,
            onValueChange = { ket = it },
            label = { Text("Keterangan Detail (Seragam, Kitab, Catatan)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(color = Slate50, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Tampilkan di Halaman Login (Publik)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1f))
                Switch(checked = isPublicOnLogin, onCheckedChange = { isPublicOnLogin = it })
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Batal") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (judul.isNotBlank()) {
                        onSave(kategori, judul, hari, tanggal, jamMulai, jamSelesai, tempat, firqohId, pj, ket, isPublicOnLogin)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simpan Jadwal", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
