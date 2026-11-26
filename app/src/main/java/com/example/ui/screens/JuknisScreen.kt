package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.JuknisDocumentItem
import com.example.data.MmqaConstants
import com.example.data.MmqaDatabase
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Calendar

private val JUKNIS_CATEGORIES = listOf(
    "ALL" to "Semua",
    "Juknis" to "Juknis",
    "Materi Rapat" to "Materi Rapat",
    "Peraturan & Tata Tertib" to "Peraturan & Tata Tertib",
    "Pengumuman Penting" to "Pengumuman Penting"
)

@Composable
fun JuknisScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean,
    onPickPdfFile: ((fileName: String, dataUrl: String) -> Unit) -> Unit,
    onSaveUploadedPdf: (fileName: String, mimeType: String, dataUrl: String) -> Unit
) {
    val context = LocalContext.current
    val canUpload = viewModel.canAccessAnakSub("juknis_upload")
    val canDelete = viewModel.canAccessAnakSub("juknis_delete") && db.adminConfig.permissions.allowDeleteRecords
    val canDownload = viewModel.canAccessAnakSub("juknis_download")

    var selectedCategory by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var viewingDoc by remember { mutableStateOf<JuknisDocumentItem?>(null) }
    var editingDoc by remember { mutableStateOf<JuknisDocumentItem?>(null) }
    var showFormModal by remember { mutableStateOf(false) }
    var deletingDoc by remember { mutableStateOf<JuknisDocumentItem?>(null) }

    val activeAcuan = remember(db.juknisDocs) { viewModel.getActiveJuknisAcuan() }
    val filteredDocs = remember(db.juknisDocs, selectedCategory, search) {
        val q = search.trim().lowercase()
        db.juknisDocs.filter { doc ->
            (selectedCategory == "ALL" || doc.kategori.equals(selectedCategory, ignoreCase = true)) &&
                (q.isEmpty() ||
                    doc.judul.lowercase().contains(q) ||
                    doc.nomorDokumen.lowercase().contains(q) ||
                    doc.deskripsi.lowercase().contains(q) ||
                    doc.kategori.lowercase().contains(q))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Active Acuan Peraturan Dashboard Banner
        SectionCard(
            containerColor = Amber50,
            borderColor = Amber200
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    StatusPill("⚖️ ACUAN PERATURAN DASHBOARD AKTIF", Amber100, Amber950, Amber300)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeAcuan.judul,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                    Text(
                        text = "No: ${activeAcuan.nomorDokumen.ifBlank { "-" }} • Sinkron otomatis dengan Rekap Santri Bermasalah di Dashboard",
                        fontSize = 11.sp,
                        color = Amber900
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Amber200),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• Alpha ≥ ${activeAcuan.batasAlphaSp1}x : ${activeAcuan.konsekuensiSp1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    Text(
                        text = "• Alpha ≥ ${activeAcuan.batasAlphaSp2}x : ${activeAcuan.konsekuensiSp2}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                    Text(
                        text = "• Alpha ≥ ${activeAcuan.batasAlphaSp3}x : ${activeAcuan.konsekuensiSp3}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Rose800
                    )
                    Text(
                        text = "• Izin ≥ ${activeAcuan.batasIzinPeringatan}x : ${activeAcuan.konsekuensiIzin}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Amber900
                    )
                }
            }
        }

        // Toolbar & Category Filter
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📘 Juknis & Materi Rapat (PDF)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                    Text(
                        text = "Lihat langsung atau unduh PDF resmi majelis • ${filteredDocs.size} dokumen",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
                if (canUpload) {
                    Button(
                        onClick = {
                            editingDoc = null
                            showFormModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("juknis_add_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Upload Juknis", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload Juknis", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
                JUKNIS_CATEGORIES.forEach { (key, label) ->
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

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("🔍 Cari judul juknis, nomor dokumen, materi rapat...", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Document List
        if (filteredDocs.isEmpty()) {
            SectionCard {
                Text(
                    text = "Belum ada dokumen Juknis atau Materi Rapat pada kategori ini.",
                    fontSize = 12.sp,
                    color = Slate500,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredDocs.chunked(if (isWideScreen) 2 else 1).forEach { rowDocs ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowDocs.forEach { doc ->
                            JuknisDocCard(
                                doc = doc,
                                canUpload = canUpload,
                                canDelete = canDelete,
                                canDownload = canDownload,
                                onView = { viewingDoc = doc },
                                onDownload = {
                                    if (doc.pdfDataUrl.isNotBlank()) {
                                        onSaveUploadedPdf(
                                            doc.pdfName.ifBlank { "${doc.judul.take(28)}.pdf" },
                                            "application/pdf",
                                            doc.pdfDataUrl
                                        )
                                    } else {
                                        val html = buildJuknisDocHtml(doc)
                                        printHtmlDocument(context, "Juknis_${doc.id}", html)
                                    }
                                },
                                onSetAsAcuan = { viewModel.setActiveJuknisAcuan(doc.id) },
                                onEdit = {
                                    editingDoc = doc
                                    showFormModal = true
                                },
                                onDelete = { deletingDoc = doc },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isWideScreen && rowDocs.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    viewingDoc?.let { doc ->
        JuknisPdfViewerDialog(
            doc = doc,
            onSaveUploadedPdf = { name, mime, url ->
                if (canDownload) {
                    onSaveUploadedPdf(name, mime, url)
                } else {
                    viewModel.showToast("Izin unduh Juknis dibatasi oleh Admin.", isError = true)
                }
            },
            onDismiss = { viewingDoc = null }
        )
    }

    if (showFormModal) {
        JuknisFormModal(
            editingDoc = editingDoc,
            onPickPdfFile = onPickPdfFile,
            onSave = { judul, kategori, nomor, tanggal, deskripsi, pdfName, pdfDataUrl, isPublic, isAcuan, sp1, sp2, sp3, izinLimit, kSp1, kSp2, kSp3, kIzin ->
                viewModel.saveJuknisDoc(
                    id = editingDoc?.id,
                    judul = judul,
                    kategori = kategori,
                    nomorDokumen = nomor,
                    tanggal = tanggal,
                    deskripsi = deskripsi,
                    pdfName = pdfName,
                    pdfDataUrl = pdfDataUrl,
                    isPublicOnLogin = isPublic,
                    isAcuanAturan = isAcuan,
                    batasAlphaSp1 = sp1,
                    batasAlphaSp2 = sp2,
                    batasAlphaSp3 = sp3,
                    batasIzinPeringatan = izinLimit,
                    konsekuensiSp1 = kSp1,
                    konsekuensiSp2 = kSp2,
                    konsekuensiSp3 = kSp3,
                    konsekuensiIzin = kIzin
                )
                showFormModal = false
            },
            onDismiss = { showFormModal = false }
        )
    }

    deletingDoc?.let { doc ->
        ConfirmActionDialog(
            title = "Hapus Dokumen Juknis",
            message = "Yakin ingin menghapus dokumen \"${doc.judul}\"? Data akan disinkronkan ke seluruh perangkat.",
            onDismiss = { deletingDoc = null },
            onConfirm = { viewModel.deleteJuknisDoc(doc.id) }
        )
    }
}

@Composable
private fun JuknisDocCard(
    doc: JuknisDocumentItem,
    canUpload: Boolean,
    canDelete: Boolean,
    canDownload: Boolean,
    onView: () -> Unit,
    onDownload: () -> Unit,
    onSetAsAcuan: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        containerColor = if (doc.isAcuanAturan) Emerald50.copy(alpha = 0.55f) else Color.White,
        borderColor = if (doc.isAcuanAturan) Emerald700.copy(alpha = 0.45f) else Slate200,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusPill(doc.kategori, Emerald100, Emerald900)
            if (doc.isAcuanAturan) {
                StatusPill("⚖️ Acuan Aktif", Amber100, Amber950, Amber300)
            }
            if (doc.pdfDataUrl.isNotBlank()) {
                StatusPill("PDF Lampiran", Rose50, Rose800, Rose200)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = doc.judul,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Slate900,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "No: ${doc.nomorDokumen.ifBlank { "-" }} • ${doc.tanggal} • ${doc.uploadedBy}",
            fontSize = 11.sp,
            color = Slate500
        )
        if (doc.deskripsi.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = doc.deskripsi,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Slate700,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (doc.isPublicOnLogin) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "🌐 Tampil di Halaman Login Publik",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Emerald800
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onView,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .testTag("juknis_view_${doc.id}")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Lihat Langsung", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lihat PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (canDownload) {
                OutlinedButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Rose200),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Rose50, contentColor = Rose800),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .testTag("juknis_download_${doc.id}")
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Unduh PDF", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (canUpload || canDelete) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (canUpload && !doc.isAcuanAturan) {
                    TextButton(onClick = onSetAsAcuan) {
                        Text("⚖️ Jadikan Acuan", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Amber900)
                    }
                }
                if (canUpload) {
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
private fun JuknisFormModal(
    editingDoc: JuknisDocumentItem?,
    onPickPdfFile: ((fileName: String, dataUrl: String) -> Unit) -> Unit,
    onSave: (
        judul: String,
        kategori: String,
        nomor: String,
        tanggal: String,
        deskripsi: String,
        pdfName: String,
        pdfDataUrl: String,
        isPublicOnLogin: Boolean,
        isAcuanAturan: Boolean,
        sp1: Int,
        sp2: Int,
        sp3: Int,
        izinLimit: Int,
        kSp1: String,
        kSp2: String,
        kSp3: String,
        kIzin: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var judul by remember { mutableStateOf(editingDoc?.judul ?: "") }
    var kategori by remember { mutableStateOf(editingDoc?.kategori ?: "Juknis") }
    var nomor by remember { mutableStateOf(editingDoc?.nomorDokumen ?: "") }
    var tanggal by remember { mutableStateOf(editingDoc?.tanggal ?: MmqaConstants.todayStr()) }
    var deskripsi by remember { mutableStateOf(editingDoc?.deskripsi ?: "") }
    var pdfName by remember { mutableStateOf(editingDoc?.pdfName ?: "") }
    var pdfDataUrl by remember { mutableStateOf(editingDoc?.pdfDataUrl ?: "") }
    var isPublicOnLogin by remember { mutableStateOf(editingDoc?.isPublicOnLogin ?: true) }
    var isAcuanAturan by remember { mutableStateOf(editingDoc?.isAcuanAturan ?: false) }

    var sp1 by remember { mutableStateOf((editingDoc?.batasAlphaSp1 ?: 2).toString()) }
    var sp2 by remember { mutableStateOf((editingDoc?.batasAlphaSp2 ?: 4).toString()) }
    var sp3 by remember { mutableStateOf((editingDoc?.batasAlphaSp3 ?: 6).toString()) }
    var izinLim by remember { mutableStateOf((editingDoc?.batasIzinPeringatan ?: 5).toString()) }
    var kSp1 by remember { mutableStateOf(editingDoc?.konsekuensiSp1 ?: "Teguran Lisan & Pembinaan Mu'allim") }
    var kSp2 by remember { mutableStateOf(editingDoc?.konsekuensiSp2 ?: "Surat Peringatan (SP1) & Panggilan Wali") }
    var kSp3 by remember { mutableStateOf(editingDoc?.konsekuensiSp3 ?: "Sidang Dewan Harian & Takzir Berat") }
    var kIzin by remember { mutableStateOf(editingDoc?.konsekuensiIzin ?: "Evaluasi Kehadiran & Konfirmasi Wali Santri") }

    MmqaModalDialog(
        title = if (editingDoc != null) "✏️ Edit Juknis / Materi Rapat" else "📤 Upload Juknis / Materi Rapat PDF",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = judul,
            onValueChange = { judul = it },
            label = { Text("Judul Dokumen / Juknis") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        CompactDropdownSelector(
            label = "Kategori Dokumen",
            selectedText = kategori,
            options = JUKNIS_CATEGORIES.drop(1),
            onSelect = { kategori = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = nomor,
                onValueChange = { nomor = it },
                label = { Text("No. Dokumen", fontSize = 11.sp) },
                placeholder = { Text("001/JUKNIS/MMQA/2026", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Column(modifier = Modifier.weight(0.9f)) {
                Text("Tanggal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
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
                        Text(tanggal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal", tint = Emerald800, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = deskripsi,
            onValueChange = { deskripsi = it },
            label = { Text("Ringkasan Isi / Poin Penting Rapat") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Upload PDF Picker Button
        Surface(
            color = if (pdfDataUrl.isNotBlank()) Emerald50 else Slate50,
            border = BorderStroke(1.dp, if (pdfDataUrl.isNotBlank()) Emerald700.copy(alpha = 0.4f) else Slate300),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Rose700, modifier = Modifier.size(22.dp))
                    Column {
                        Text(
                            text = if (pdfName.isNotBlank()) pdfName else "Belum ada file PDF dipilih",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (pdfDataUrl.isNotBlank()) "PDF siap dilihat langsung & diunduh" else "Opsional • Maks 3,5 MB",
                            fontSize = 10.sp,
                            color = Slate500
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        onPickPdfFile { name, url ->
                            pdfName = name
                            pdfDataUrl = url
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Pilih PDF", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pilih PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Switches
        Surface(color = Slate50, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tampilkan di Halaman Login (Publik)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1f))
                    Switch(checked = isPublicOnLogin, onCheckedChange = { isPublicOnLogin = it })
                }
                HorizontalDivider(color = Slate200)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jadikan Acuan Peraturan Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber950)
                        Text("Batas Alpha/Izin & konsekuensi otomatis sinkron ke Dashboard", fontSize = 10.sp, color = Slate500)
                    }
                    Switch(checked = isAcuanAturan, onCheckedChange = { isAcuanAturan = it })
                }
            }
        }

        if (isAcuanAturan) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(color = Amber50, border = BorderStroke(1.dp, Amber200), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚖️ Batas Pelanggaran & Konsekuensi (Sinkron Dashboard)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Amber950)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = sp1,
                            onValueChange = { sp1 = it.filter { c -> c.isDigit() } },
                            label = { Text("Alpha SP1", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sp2,
                            onValueChange = { sp2 = it.filter { c -> c.isDigit() } },
                            label = { Text("Alpha SP2", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sp3,
                            onValueChange = { sp3 = it.filter { c -> c.isDigit() } },
                            label = { Text("Alpha Berat", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = izinLim,
                            onValueChange = { izinLim = it.filter { c -> c.isDigit() } },
                            label = { Text("Batas Izin", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(value = kSp1, onValueChange = { kSp1 = it }, label = { Text("Konsekuensi Alpha SP1", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = kSp2, onValueChange = { kSp2 = it }, label = { Text("Konsekuensi Alpha SP2", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = kSp3, onValueChange = { kSp3 = it }, label = { Text("Konsekuensi Alpha Berat (SP3)", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = kIzin, onValueChange = { kIzin = it }, label = { Text("Konsekuensi Melebihi Batas Izin", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Batal") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (judul.isNotBlank()) {
                        onSave(
                            judul, kategori, nomor, tanggal, deskripsi, pdfName, pdfDataUrl,
                            isPublicOnLogin, isAcuanAturan,
                            sp1.toIntOrNull() ?: 2,
                            sp2.toIntOrNull() ?: 4,
                            sp3.toIntOrNull() ?: 6,
                            izinLim.toIntOrNull() ?: 5,
                            kSp1, kSp2, kSp3, kIzin
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simpan Dokumen", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
