package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.example.data.MmqaDatabase
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.BarcodeCardPreviewDialog
import com.example.ui.components.BarcodeDirectoryDialog
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.CompactDropdownSelector
import com.example.ui.components.MmqaBarcodeCard
import com.example.ui.components.MmqaBarcodeEngine
import com.example.ui.components.MmqaModalDialog
import com.example.ui.components.MmqaReportFormatters
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusPill
import com.example.ui.components.SubmenuTabsRow
import com.example.ui.components.WaPdfActionRow
import com.example.ui.components.printHtmlDocument
import com.example.ui.components.shareTextToWhatsApp
import com.example.ui.theme.*
import java.util.Calendar

@Composable
fun AbsensiScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val enabledSubmenus = viewModel.getAccessibleSubmenus("absensi")
    if (enabledSubmenus.isEmpty()) {
        SectionCard {
            Text(
                text = "🔒 Tidak ada submenu Absensi yang diizinkan untuk akun Anda.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500
            )
        }
        return
    }
    val activeTab = if (enabledSubmenus.any { it.id == uiState.absensiTab }) {
        uiState.absensiTab
    } else {
        enabledSubmenus.first().id
    }

    val canEditCurrentTab = if (activeTab == "santri") {
        viewModel.canAccessAnakSub("absensi_santri_edit")
    } else {
        viewModel.canAccessAnakSub("absensi_pengurus_edit")
    }
    val canMarkAllPresent = canEditCurrentTab && (activeTab != "santri" || viewModel.canAccessAnakSub("absensi_santri_mark_all"))
    val canScanBarcode = viewModel.canAccessAnakSub("absensi_barcode_scan") && canEditCurrentTab

    var showScannerDialog by remember { mutableStateOf(false) }
    var showBarcodeDirectory by remember { mutableStateOf(false) }
    var previewBarcodeCard by remember { mutableStateOf<MmqaBarcodeCard?>(null) }
    var showCustomEventModal by remember { mutableStateOf(false) }

    val sessionOptions = remember(db.jadwal, db.absensi, uiState.absensiSesi) {
        viewModel.buildAbsensiSessionOptions()
    }
    val isCustomOrEventSession = uiState.absensiSesi !in listOf("Pagi", "Sore", "Malam")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubmenuTabsRow(
            items = enabledSubmenus.map { it.id to it.label },
            selectedId = activeTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(absensiTab = tabId) } }
        )

        SectionCard {
            // Controls: Tanggal, Sesi, Firqoh / Role Notice, Mark All Present
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    // Date Picker Button
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tanggal Presensi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate300),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable {
                                    val parts = uiState.absensiTanggal.split("-")
                                    val cal = Calendar.getInstance()
                                    val y = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                                    val m = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
                                    val d = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                                    DatePickerDialog(context, { _, year, month, day ->
                                        val formatted = String.format("%04d-%02d-%02d", year, month + 1, day)
                                        viewModel.updateState { it.copy(absensiTanggal = formatted) }
                                    }, y, m, d).show()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = uiState.absensiTanggal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal", tint = Emerald800, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    CompactDropdownSelector(
                        label = "Sesi / Event Kehadiran",
                        selectedText = sessionOptions.firstOrNull { it.first == uiState.absensiSesi }?.second
                            ?: if (isCustomOrEventSession) "🎯 ${uiState.absensiSesi}" else "Sesi ${uiState.absensiSesi}",
                        options = sessionOptions,
                        onSelect = { sesi -> viewModel.updateState { it.copy(absensiSesi = sesi) } },
                        modifier = Modifier.weight(1.25f)
                    )
                }

                // Event Kehadiran Khusus Banner & Quick Trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCustomOrEventSession) {
                        Surface(
                            color = Sky100,
                            border = BorderStroke(1.dp, Sky700.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = Sky900, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Mode Event Aktif: ${uiState.absensiSesi}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Sky900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Bisa pilih sesi harian, event dari Jadwal, atau buat Event Kehadiran Khusus untuk scan QR.",
                            fontSize = 10.sp,
                            color = Slate500,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = { showCustomEventModal = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("absensi_add_custom_event_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Buat Event Kehadiran", tint = Emerald900, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Event Khusus", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald900)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    if (activeTab == "santri") {
                        val firqohOptions = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = true)
                        CompactDropdownSelector(
                            label = "Pilih Firqoh",
                            selectedText = firqohOptions.firstOrNull { it.first == uiState.absensiFirqoh }?.second ?: viewModel.getFirqohName(uiState.absensiFirqoh),
                            options = firqohOptions,
                            onSelect = { fid -> viewModel.updateState { it.copy(absensiFirqoh = fid) } },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Surface(
                            color = Amber50,
                            border = BorderStroke(1.dp, Amber200),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (canEditCurrentTab) {
                                    "👑 Akses Aktif: Dapat mengisi presensi pengurus"
                                } else {
                                    "ℹ️ Mode baca saja: Tidak memiliki izin mengubah presensi ini"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber900,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (canMarkAllPresent) {
                        Button(
                            onClick = { viewModel.markAllPresent(activeTab) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("absensi_mark_all_present")
                        ) {
                            Text("✅ Semua Hadir", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Barcode Scanner & Live Barcode Directory Action Bar
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (canScanBarcode) {
                        Button(
                            onClick = { showScannerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("absensi_scan_barcode_btn")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Barcode", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    OutlinedButton(
                        onClick = { showBarcodeDirectory = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Emerald50, contentColor = Emerald900),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("absensi_barcode_directory_btn")
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = "Kartu Barcode", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kartu Barcode", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val rows: List<AbsensiRowUi> = when (activeTab) {
                "santri" -> db.santri.filter { viewModel.matchesFirqohFilter(it.firqohId, uiState.absensiFirqoh) }.map { s ->
                    val rec = viewModel.getAbsensiRecord("santri", s.id)
                    AbsensiRowUi("santri", s.id, s.firqohId, s.nama, "NIS: ${s.nis} • ${viewModel.getFirqohName(s.firqohId)} • Wali: ${s.wali}", rec?.status ?: "", rec?.catatan ?: "")
                }
                "muallim" -> db.muallim.map { m ->
                    val rec = viewModel.getAbsensiRecord("muallim", m.id)
                    AbsensiRowUi("muallim", m.id, m.firqohId, m.nama, "NIY: ${m.niy} • ${viewModel.getFirqohName(m.firqohId)}", rec?.status ?: "", rec?.catatan ?: "")
                }
                else -> db.dewanHarian.map { d ->
                    val rec = viewModel.getAbsensiRecord("dewan_harian", d.id)
                    AbsensiRowUi("dewan_harian", d.id, "", d.nama, "Kode: ${d.kode} • ${d.jabatan}", rec?.status ?: "", rec?.catatan ?: "")
                }
            }

            if (rows.isNotEmpty()) {
                val sesiDisplay = if (isCustomOrEventSession) uiState.absensiSesi else "Sesi ${uiState.absensiSesi}"
                val kategoriDisplay = when (activeTab) {
                    "santri" -> "Santri (${viewModel.getFirqohFilterDisplayLabel(uiState.absensiFirqoh)})"
                    "muallim" -> "Mu'allim Firqoh"
                    else -> "Dewan Harian MMQA"
                }
                val hadirCnt = rows.count { it.status == "Hadir" }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rekap $sesiDisplay", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                        StatusPill("Hadir: $hadirCnt / ${rows.size}", Emerald100, Emerald900)
                    }
                    WaPdfActionRow(
                        onShareWa = {
                            val waText = MmqaReportFormatters.buildSessionAbsensiWa(
                                tanggal = uiState.absensiTanggal,
                                sesiLabel = sesiDisplay,
                                kategoriLabel = kategoriDisplay,
                                rows = rows.map { Triple(it.nama, it.subInfo, it.status) }
                            )
                            shareTextToWhatsApp(context, waText, "Bagikan Rekap $sesiDisplay")
                        },
                        onDownloadPdf = {
                            val html = MmqaReportFormatters.buildSessionAbsensiPdfHtml(
                                tanggal = uiState.absensiTanggal,
                                sesiLabel = sesiDisplay,
                                kategoriLabel = kategoriDisplay,
                                rows = rows.map { Triple(it.nama, it.subInfo, it.status) }
                            )
                            printHtmlDocument(context, "Absensi_${uiState.absensiTanggal}_${uiState.absensiSesi.take(20)}", html)
                        },
                        waLabel = "Rekap WA",
                        pdfLabel = "Rekap PDF",
                        waTestTag = "absensi_session_wa_btn",
                        pdfTestTag = "absensi_session_pdf_btn"
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (rows.isEmpty()) {
                Text(
                    text = "Tidak ada data anggota pada kategori ini.",
                    fontSize = 12.sp,
                    color = Slate500,
                    modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        AbsensiItemCard(
                            row = row,
                            canEdit = canEditCurrentTab,
                            isWideScreen = isWideScreen,
                            onStatusSelect = { status ->
                                viewModel.setAbsensiStatus(row.tipe, row.targetId, row.firqohId, status)
                            },
                            onCatatanCommit = { catatan ->
                                viewModel.setAbsensiCatatan(row.tipe, row.targetId, row.firqohId, catatan)
                            },
                            onShowBarcode = {
                                val card = when (row.tipe) {
                                    "santri" -> db.santri.firstOrNull { it.id == row.targetId }
                                        ?.let { MmqaBarcodeEngine.buildCardForSantri(db, it) }
                                    "muallim" -> db.muallim.firstOrNull { it.id == row.targetId }
                                        ?.let { MmqaBarcodeEngine.buildCardForMuallim(db, it) }
                                    else -> db.dewanHarian.firstOrNull { it.id == row.targetId }
                                        ?.let { MmqaBarcodeEngine.buildCardForDewan(db, it) }
                                }
                                if (card != null) previewBarcodeCard = card
                            }
                        )
                    }
                }
            }
        }
    }

    if (showScannerDialog) {
        BarcodeScannerDialog(
            db = db,
            activeTanggal = uiState.absensiTanggal,
            activeSesi = uiState.absensiSesi,
            onScannedAttendance = { card, status ->
                viewModel.recordBarcodeAttendance(
                    tipe = card.tipe,
                    targetId = card.targetId,
                    firqohId = card.firqohId,
                    nama = card.nama,
                    status = status
                )
            },
            onDismiss = { showScannerDialog = false }
        )
    }

    if (showBarcodeDirectory) {
        BarcodeDirectoryDialog(
            db = db,
            viewModel = viewModel,
            initialTab = activeTab,
            initialFirqohFilter = uiState.absensiFirqoh,
            onDismiss = { showBarcodeDirectory = false }
        )
    }

    previewBarcodeCard?.let { card ->
        BarcodeCardPreviewDialog(
            card = card,
            onDismiss = { previewBarcodeCard = null }
        )
    }

    if (showCustomEventModal) {
        var eventName by remember { mutableStateOf("") }
        MmqaModalDialog(title = "🎯 Buat Event Kehadiran Khusus (QR)", onDismiss = { showCustomEventModal = false }) {
            Text(
                text = "Buat nama event kehadiran tertentu (misal: Istighotsah Kubro, Rapat Wali Santri, Ujian Munaqosyah, Wisuda Tahfidz). Setelah aktif, seluruh scan QR dan presensi manual akan dicatat khusus untuk event ini.",
                fontSize = 11.sp,
                color = Slate700
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Nama Event Kehadiran") },
                placeholder = { Text("Contoh: Istighotsah Kubro / Rapat Wali Santri") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("absensi_custom_event_input")
            )
            if (db.jadwal.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Atau pilih langsung dari Menu Jadwal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    db.jadwal.take(6).forEach { j ->
                        Surface(
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateState {
                                        it.copy(
                                            absensiSesi = "Event: ${j.judul}",
                                            absensiTanggal = j.tanggal.ifBlank { it.absensiTanggal }
                                        )
                                    }
                                    showCustomEventModal = false
                                    viewModel.showToast("🎯 Event aktif: ${j.judul}. Siap scan QR!")
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("[${j.kategori}] ${j.judul}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Emerald900)
                                Text("${j.hari}, ${j.tanggal} • ${j.jamMulai}-${j.jamSelesai} • ${j.tempat}", fontSize = 10.sp, color = Slate500)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showCustomEventModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val clean = eventName.trim()
                        if (clean.isNotBlank()) {
                            val finalSesi = if (clean.startsWith("Event:", ignoreCase = true)) clean else "Event: $clean"
                            viewModel.updateState { it.copy(absensiSesi = finalSesi) }
                            showCustomEventModal = false
                            viewModel.showToast("🎯 Mode presensi QR aktif untuk $finalSesi")
                        }
                    },
                    enabled = eventName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("absensi_save_custom_event_btn")
                ) {
                    Text("Aktifkan Event & Scan QR", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
    }
}

private data class AbsensiRowUi(
    val tipe: String,
    val targetId: String,
    val firqohId: String,
    val nama: String,
    val subInfo: String,
    val status: String,
    val catatan: String
)

@Composable
private fun AbsensiItemCard(
    row: AbsensiRowUi,
    canEdit: Boolean,
    isWideScreen: Boolean,
    onStatusSelect: (String) -> Unit,
    onCatatanCommit: (String) -> Unit,
    onShowBarcode: () -> Unit
) {
    var noteDraft by remember(row.targetId, row.catatan, row.status) { mutableStateOf(row.catatan) }
    val statusSpecs = listOf(
        Triple("Hadir", Emerald700, Emerald50),
        Triple("Izin", Amber500, Amber50),
        Triple("Sakit", Sky700, Sky100),
        Triple("Alpha", Rose600, Rose50)
    )

    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.nama, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                    Text(
                        text = row.subInfo,
                        fontSize = 11.sp,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Emerald50,
                    border = BorderStroke(1.dp, Emerald100),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onShowBarcode)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Kartu Barcode ${row.nama}",
                            tint = Emerald900,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isWideScreen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        statusSpecs.forEach { (key, activeBg, idleBg) ->
                            AbsensiStatusChip(
                                label = key,
                                selected = row.status == key,
                                enabled = canEdit,
                                activeColor = activeBg,
                                idleBg = idleBg,
                                onClick = { onStatusSelect(key) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = {
                            noteDraft = it
                            onCatatanCommit(it)
                        },
                        enabled = canEdit,
                        placeholder = { Text("Catatan...", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    statusSpecs.forEach { (key, activeBg, idleBg) ->
                        AbsensiStatusChip(
                            label = key,
                            selected = row.status == key,
                            enabled = canEdit,
                            activeColor = activeBg,
                            idleBg = idleBg,
                            onClick = { onStatusSelect(key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = {
                        noteDraft = it
                        onCatatanCommit(it)
                    },
                    enabled = canEdit,
                    placeholder = { Text("Catatan...", fontSize = 11.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AbsensiStatusChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    activeColor: Color,
    idleBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) activeColor else idleBg.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (selected) activeColor else Slate200),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) Color.White else Slate700
            )
        }
    }
}
