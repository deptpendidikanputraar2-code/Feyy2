package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SuratScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean,
    onPickPdfTemplate: ((fileName: String, dataUrl: String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val canPublishSurat = viewModel.canAccessAnakSub("surat_terbitkan")
    val canPrintPdf = viewModel.canAccessAnakSub("surat_cetak_pdf")
    val allowDeleteArsip = db.adminConfig.permissions.allowDeleteRecords && viewModel.canAccessAnakSub("surat_arsip_hapus")
    val canManageTemplates = viewModel.canAccessAnakSub("surat_template_kelola")

    val enabledSubmenus = viewModel.getAccessibleSubmenus("surat")
    if (enabledSubmenus.isEmpty()) {
        SectionCard {
            Text(
                text = "🔒 Tidak ada submenu Surat yang diizinkan untuk akun Anda.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500
            )
        }
        return
    }
    val activeTab = if (enabledSubmenus.any { it.id == uiState.suratTab }) {
        uiState.suratTab
    } else {
        enabledSubmenus.first().id
    }

    var previewArsip by remember { mutableStateOf<SuratArsipItem?>(null) }
    var editingTemplate by remember { mutableStateOf<SuratTemplateItem?>(null) }
    var showTemplateModal by remember { mutableStateOf(false) }
    var viewingTemplatePdf by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeSigPadTarget by remember { mutableStateOf<String?>(null) } // "ketua" | "sekretaris"
    var showFormatDetailEditor by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubmenuTabsRow(
            items = enabledSubmenus.map { it.id to it.label },
            selectedId = activeTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(suratTab = tabId) } }
        )

        when (activeTab) {
            "buat" -> {
                val selectedTpl = db.suratTemplates.firstOrNull { it.id == uiState.suratDraftTemplateId }
                val formCard: @Composable () -> Unit = {
                    SectionCard {
                        Text("✍️ Pembuatan Surat Resmi", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                        Text(
                            "Template bertindak sebagai pedoman format surat (Kop, Font, Margin, Kertas, Ketua & Sekretaris + TTD Manual).",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        CompactDropdownSelector(
                            label = "Kode Klasifikasi Surat",
                            selectedText = MmqaConstants.getKodeSuratLabel(uiState.suratDraftKode),
                            options = listOf(
                                "A" to "Kode A — Undangan",
                                "B" to "Kode B — Permohonan & Pemberitahuan",
                                "C" to "Kode C — Instruksi"
                            ),
                            onSelect = { kode -> viewModel.onChangeSuratDraftKode(kode) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val tplOptions = listOf("" to "-- Tanpa Template / Manual --") + db.suratTemplates.map {
                            it.id to "[Kode ${it.kodeSurat}] ${it.nama}${if (it.pdfName.isNotBlank()) " (PDF)" else ""}"
                        }
                        CompactDropdownSelector(
                            label = "Pilih Template Pedoman Format Surat",
                            selectedText = tplOptions.firstOrNull { it.first == uiState.suratDraftTemplateId }?.second ?: "-- Pilih Template --",
                            options = tplOptions,
                            onSelect = { tid -> viewModel.onSelectSuratTemplate(tid) }
                        )

                        // Active Template Guideline Card
                        if (selectedTpl != null || uiState.suratDraftPedoman.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Emerald50,
                                border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "📐 Pedoman Format Template Aktif",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Emerald900
                                        )
                                        StatusPill("${uiState.suratDraftUkuranKertas} • ${uiState.suratDraftMarginMm}mm", Emerald100, Emerald900)
                                    }
                                    Text(
                                        text = "Huruf: ${uiState.suratDraftFontStyle} • Kota: ${uiState.suratDraftKota}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald800
                                    )
                                    if (uiState.suratDraftPedoman.isNotBlank()) {
                                        Text(
                                            text = uiState.suratDraftPedoman,
                                            fontSize = 11.sp,
                                            color = Slate700,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    if (selectedTpl != null && selectedTpl.pdfDataUrl.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { viewingTemplatePdf = selectedTpl.pdfName.ifBlank { selectedTpl.nama } to selectedTpl.pdfDataUrl },
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Amber500),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Amber50, contentColor = Amber950),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Lihat PDF Pedoman: ${selectedTpl.pdfName}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }

                        // Collapsible Kop & Layout Blueprint Overrides
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFormatDetailEditor = !showFormatDetailEditor }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "⚙️ Detail Kop, Kertas, Font & Margin (Dari Template)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900
                                )
                                Icon(
                                    if (showFormatDetailEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Buka Pengaturan Format",
                                    tint = Emerald900
                                )
                            }
                        }

                        if (showFormatDetailEditor) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.suratDraftKop1,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftKop1 = v) } },
                                label = { Text("Kop Surat Baris 1", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = uiState.suratDraftKop2,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftKop2 = v) } },
                                label = { Text("Kop Surat Baris 2 (Judul Utama)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = uiState.suratDraftKop3,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftKop3 = v) } },
                                label = { Text("Kop Surat Baris 3 (Alamat / Subjudul)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactDropdownSelector(
                                    label = "Jenis Huruf",
                                    selectedText = uiState.suratDraftFontStyle,
                                    options = listOf(
                                        "Times New Roman (Resmi)" to "Times New Roman (Resmi)",
                                        "Sans-Serif (Modern)" to "Sans-Serif (Modern)"
                                    ),
                                    onSelect = { f -> viewModel.updateState { it.copy(suratDraftFontStyle = f) } },
                                    modifier = Modifier.weight(1.3f)
                                )
                                CompactDropdownSelector(
                                    label = "Ukuran Kertas",
                                    selectedText = uiState.suratDraftUkuranKertas,
                                    options = listOf("A4" to "A4", "F4 / Folio" to "F4 / Folio"),
                                    onSelect = { k -> viewModel.updateState { it.copy(suratDraftUkuranKertas = k) } },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = uiState.suratDraftKota,
                                    onValueChange = { v -> viewModel.updateState { it.copy(suratDraftKota = v) } },
                                    label = { Text("Kota Surat", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = uiState.suratDraftMarginMm.toString(),
                                    onValueChange = { v ->
                                        val m = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(10, 40) ?: 20
                                        viewModel.updateState { it.copy(suratDraftMarginMm = m) }
                                    },
                                    label = { Text("Margin (mm)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.suratDraftNomor,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftNomor = v) } },
                                label = { Text("Nomor Surat", fontSize = 11.sp) },
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.weight(1.3f)
                            )
                            OutlinedTextField(
                                value = uiState.suratDraftTanggal,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftTanggal = v) } },
                                label = { Text("Tanggal", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.suratDraftLampiran,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftLampiran = v) } },
                                label = { Text("Lampiran", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                            OutlinedTextField(
                                value = uiState.suratDraftPerihal,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftPerihal = v) } },
                                label = { Text("Perihal", fontSize = 11.sp) },
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.suratDraftTujuan,
                            onValueChange = { v -> viewModel.updateState { it.copy(suratDraftTujuan = v) } },
                            label = { Text("Tujuan / Kepada Yth.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.suratDraftIsi,
                            onValueChange = { v -> viewModel.updateState { it.copy(suratDraftIsi = v) } },
                            label = { Text("Isi Surat") },
                            minLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Slate200)
                        Spacer(modifier = Modifier.height(10.dp))

                        // KETUA SIGNATORY + MANUAL TTD
                        Text("🖋️ Penandatangan 1 (Ketua)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Emerald900)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.suratDraftPenandatangan,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftPenandatangan = v) } },
                                label = { Text("Nama Ketua", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = uiState.suratDraftJabatan,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftJabatan = v) } },
                                label = { Text("Jabatan Ketua", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SignatureActionCard(
                            label = "TTD Manual Ketua (Di APK)",
                            dataUrl = uiState.suratDraftTtdKetua,
                            onDrawClick = { activeSigPadTarget = "ketua" },
                            onClearClick = { viewModel.updateState { it.copy(suratDraftTtdKetua = "") } },
                            testTagPrefix = "surat_ttd_ketua"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // SEKRETARIS SIGNATORY + MANUAL TTD
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🖋️ Penandatangan 2 (Sekretaris)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Emerald900)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tampilkan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = uiState.suratDraftTampilkanSekretaris,
                                    onCheckedChange = { checked ->
                                        viewModel.updateState { it.copy(suratDraftTampilkanSekretaris = checked) }
                                    },
                                    modifier = Modifier.testTag("surat_toggle_sekretaris")
                                )
                            }
                        }

                        if (uiState.suratDraftTampilkanSekretaris) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = uiState.suratDraftSekretaris,
                                    onValueChange = { v -> viewModel.updateState { it.copy(suratDraftSekretaris = v) } },
                                    label = { Text("Nama Sekretaris", fontSize = 11.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("surat_sekretaris_name_input")
                                )
                                OutlinedTextField(
                                    value = uiState.suratDraftJabatanSekretaris,
                                    onValueChange = { v -> viewModel.updateState { it.copy(suratDraftJabatanSekretaris = v) } },
                                    label = { Text("Jabatan Sekretaris", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SignatureActionCard(
                                label = "TTD Manual Sekretaris (Di APK)",
                                dataUrl = uiState.suratDraftTtdSekretaris,
                                onDrawClick = { activeSigPadTarget = "sekretaris" },
                                onClearClick = { viewModel.updateState { it.copy(suratDraftTtdSekretaris = "") } },
                                testTagPrefix = "surat_ttd_sekretaris"
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        if (canPrintPdf || canPublishSurat) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                if (canPrintPdf) {
                                    OutlinedButton(
                                        onClick = {
                                            printHtmlDocument(
                                                context,
                                                "Surat_${uiState.suratDraftNomor}",
                                                buildPrintableSuratHtml(
                                                    kop1 = uiState.suratDraftKop1,
                                                    kop2 = uiState.suratDraftKop2,
                                                    kop3 = uiState.suratDraftKop3,
                                                    kota = uiState.suratDraftKota,
                                                    nomor = uiState.suratDraftNomor,
                                                    tanggal = uiState.suratDraftTanggal,
                                                    lampiran = uiState.suratDraftLampiran,
                                                    perihal = uiState.suratDraftPerihal,
                                                    tujuan = uiState.suratDraftTujuan,
                                                    isi = uiState.suratDraftIsi,
                                                    penandatangan = uiState.suratDraftPenandatangan,
                                                    jabatan = uiState.suratDraftJabatan,
                                                    sekretaris = uiState.suratDraftSekretaris,
                                                    jabatanSekretaris = uiState.suratDraftJabatanSekretaris,
                                                    tampilkanSekretaris = uiState.suratDraftTampilkanSekretaris,
                                                    ttdKetuaDataUrl = uiState.suratDraftTtdKetua,
                                                    ttdSekretarisDataUrl = uiState.suratDraftTtdSekretaris,
                                                    fontStyle = uiState.suratDraftFontStyle,
                                                    ukuranKertas = uiState.suratDraftUkuranKertas,
                                                    marginMm = uiState.suratDraftMarginMm
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("surat_cetak_pdf_btn")
                                    ) {
                                        Text("🖨️ Cetak / PDF", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                                    }
                                }
                                if (canPublishSurat) {
                                    Button(
                                        onClick = { viewModel.publishAndArchiveSurat() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("surat_publish_archive_btn")
                                    ) {
                                        Text("📁 Terbitkan & Arsipkan", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }

                val previewCard: @Composable () -> Unit = {
                    Surface(
                        color = Slate200.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Slate300),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📄 PRATINJAU SURAT RESMI:", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                                StatusPill("${uiState.suratDraftUkuranKertas} • ${uiState.suratDraftFontStyle.substringBefore(" ")}", Color.White, Emerald900)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SuratPaperPreview(
                                kop1 = uiState.suratDraftKop1,
                                kop2 = uiState.suratDraftKop2,
                                kop3 = uiState.suratDraftKop3,
                                kota = uiState.suratDraftKota,
                                nomor = uiState.suratDraftNomor,
                                tanggal = uiState.suratDraftTanggal,
                                lampiran = uiState.suratDraftLampiran,
                                perihal = uiState.suratDraftPerihal,
                                tujuan = uiState.suratDraftTujuan,
                                isi = uiState.suratDraftIsi,
                                penandatangan = uiState.suratDraftPenandatangan,
                                jabatan = uiState.suratDraftJabatan,
                                sekretaris = uiState.suratDraftSekretaris,
                                jabatanSekretaris = uiState.suratDraftJabatanSekretaris,
                                tampilkanSekretaris = uiState.suratDraftTampilkanSekretaris,
                                ttdKetuaDataUrl = uiState.suratDraftTtdKetua,
                                ttdSekretarisDataUrl = uiState.suratDraftTtdSekretaris,
                                fontStyle = uiState.suratDraftFontStyle
                            )
                        }
                    }
                }

                if (isWideScreen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) { formCard() }
                        Box(modifier = Modifier.weight(1f)) { previewCard() }
                    }
                } else {
                    formCard()
                    previewCard()
                }
            }

            "arsip" -> {
                val list = if (uiState.suratArsipKode == "ALL") db.suratArsip else db.suratArsip.filter { it.kodeSurat == uiState.suratArsipKode }
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "ALL" to "Semua Arsip (${db.suratArsip.size})",
                            "A" to "Kode A: Undangan (${db.suratArsip.count { it.kodeSurat == "A" }})",
                            "B" to "Kode B: Pemberitahuan (${db.suratArsip.count { it.kodeSurat == "B" }})",
                            "C" to "Kode C: Instruksi (${db.suratArsip.count { it.kodeSurat == "C" }})"
                        ).forEach { (k, label) ->
                            val active = uiState.suratArsipKode == k
                            Surface(
                                color = if (active) Emerald900 else Slate100,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable { viewModel.updateState { it.copy(suratArsipKode = k) } }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (active) Amber300 else Slate700,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { s ->
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusPill(MmqaConstants.getKodeSuratLabel(s.kodeSurat), Emerald900, Amber300)
                                        Text(s.tanggal, fontSize = 11.sp, color = Slate500)
                                        if (s.ttdKetuaDataUrl.isNotBlank() || s.ttdSekretarisDataUrl.isNotBlank()) {
                                            StatusPill("✍️ TTD Manual", Emerald100, Emerald900)
                                        }
                                    }
                                    Text(s.nomorSurat, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = Slate900)
                                    Text(s.perihal, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                                    val signerSummary = buildString {
                                        append("Ketua: ${s.penandatangan}")
                                        if (s.tampilkanSekretaris && s.sekretaris.isNotBlank()) {
                                            append(" • Sekretaris: ${s.sekretaris}")
                                        }
                                    }
                                    Text("Tujuan: ${s.tujuan}\n$signerSummary", fontSize = 11.sp, color = Slate700)
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(onClick = { previewArsip = s }, shape = RoundedCornerShape(10.dp)) {
                                            Text("👁️ Lihat & Cetak", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        }
                                        if (allowDeleteArsip) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            TextButton(onClick = { viewModel.deleteArsipSurat(s.id) }) {
                                                Text("🗑️ Hapus", fontSize = 11.sp, color = Rose700)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📐 Template & Pedoman Format Surat Detail", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text("Mengatur Kop, Kertas, Font, Margin, Ketua & Sekretaris, TTD default, kerangka isi, serta file PDF acuan.", fontSize = 11.sp, color = Slate500)
                        }
                        if (canManageTemplates) {
                            Button(
                                onClick = {
                                    editingTemplate = null
                                    showTemplateModal = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("surat_add_template_btn")
                            ) { Text("+ Template", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        db.suratTemplates.forEach { t ->
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusPill(MmqaConstants.getKodeSuratLabel(t.kodeSurat), Emerald100, Emerald900)
                                            StatusPill("${t.ukuranKertas} • ${t.marginMm}mm", Slate200, Slate900)
                                        }
                                        if (t.pdfName.isNotBlank()) StatusPill("PDF", Rose100, Rose800)
                                    }
                                    Text(t.nama, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Slate900)
                                    Text("Perihal: ${t.perihalDefault}", fontSize = 11.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "Kop: ${t.kopBaris2} • Font: ${t.fontStyle} • Kota: ${t.kotaDefault}",
                                        fontSize = 10.sp,
                                        color = Slate500
                                    )
                                    Text(
                                        text = "Penandatangan: ${t.penandatanganDefault}" +
                                            if (t.tampilkanSekretaris) " & ${t.sekretarisDefault} (Sekretaris)" else "",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald800
                                    )
                                    if (t.pedomanFormat.isNotBlank()) {
                                        Surface(
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Slate200),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "📋 Pedoman: ${t.pedomanFormat}",
                                                fontSize = 10.sp,
                                                color = Slate700,
                                                lineHeight = 15.sp,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                    if (t.pdfName.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("📎 PDF Pedoman: ${t.pdfName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber900, modifier = Modifier.weight(1f))
                                            if (t.pdfDataUrl.isNotBlank()) {
                                                TextButton(onClick = { viewingTemplatePdf = t.pdfName to t.pdfDataUrl }) {
                                                    Icon(Icons.Default.Description, contentDescription = null, tint = Emerald800, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Lihat PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald800)
                                                }
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = {
                                                viewModel.updateState { it.copy(suratTab = "buat") }
                                                viewModel.onSelectSuratTemplate(t.id)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Gunakan Template →", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                        if (canManageTemplates) {
                                            OutlinedButton(
                                                onClick = {
                                                    editingTemplate = t
                                                    showTemplateModal = true
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Template", tint = Emerald900, modifier = Modifier.size(15.dp))
                                            }
                                            if (db.adminConfig.permissions.allowDeleteRecords) {
                                                TextButton(onClick = { viewModel.deleteTemplateSurat(t.id) }) {
                                                    Text("🗑️", fontSize = 13.sp)
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
        }
    }

    // Manual Signature Pad Dialog for Draft Surat
    activeSigPadTarget?.let { target ->
        val isKetua = target == "ketua"
        SignaturePadDialog(
            title = if (isKetua) "Tanda Tangan Manual Ketua" else "Tanda Tangan Manual Sekretaris",
            signerName = if (isKetua) uiState.suratDraftPenandatangan else uiState.suratDraftSekretaris,
            signerRole = if (isKetua) uiState.suratDraftJabatan else uiState.suratDraftJabatanSekretaris,
            onSaveSignature = { dataUrl ->
                viewModel.updateState {
                    if (isKetua) it.copy(suratDraftTtdKetua = dataUrl)
                    else it.copy(suratDraftTtdSekretaris = dataUrl)
                }
                viewModel.showToast("✍️ Tanda tangan manual ${if (isKetua) "Ketua" else "Sekretaris"} diterapkan ke surat!")
            },
            onDismiss = { activeSigPadTarget = null }
        )
    }

    previewArsip?.let { s ->
        MmqaModalDialog(title = "📄 Arsip Surat: ${s.nomorSurat}", onDismiss = { previewArsip = null }) {
            SuratPaperPreview(
                kop1 = s.kopBaris1,
                kop2 = s.kopBaris2,
                kop3 = s.kopBaris3,
                kota = s.kotaTanggal,
                nomor = s.nomorSurat,
                tanggal = s.tanggal,
                lampiran = s.lampiran,
                perihal = s.perihal,
                tujuan = s.tujuan,
                isi = s.isi,
                penandatangan = s.penandatangan,
                jabatan = s.jabatanPenandatangan,
                sekretaris = s.sekretaris,
                jabatanSekretaris = s.jabatanSekretaris,
                tampilkanSekretaris = s.tampilkanSekretaris,
                ttdKetuaDataUrl = s.ttdKetuaDataUrl,
                ttdSekretarisDataUrl = s.ttdSekretarisDataUrl,
                fontStyle = s.fontStyle
            )
            if (canPrintPdf) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        printHtmlDocument(
                            context,
                            "Surat_${s.nomorSurat}",
                            buildPrintableSuratHtml(
                                kop1 = s.kopBaris1,
                                kop2 = s.kopBaris2,
                                kop3 = s.kopBaris3,
                                kota = s.kotaTanggal,
                                nomor = s.nomorSurat,
                                tanggal = s.tanggal,
                                lampiran = s.lampiran,
                                perihal = s.perihal,
                                tujuan = s.tujuan,
                                isi = s.isi,
                                penandatangan = s.penandatangan,
                                jabatan = s.jabatanPenandatangan,
                                sekretaris = s.sekretaris,
                                jabatanSekretaris = s.jabatanSekretaris,
                                tampilkanSekretaris = s.tampilkanSekretaris,
                                ttdKetuaDataUrl = s.ttdKetuaDataUrl,
                                ttdSekretarisDataUrl = s.ttdSekretarisDataUrl,
                                fontStyle = s.fontStyle,
                                ukuranKertas = s.ukuranKertas,
                                marginMm = s.marginMm
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🖨️ Cetak / Simpan PDF", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    viewingTemplatePdf?.let { (pdfTitle, dataUrl) ->
        JuknisPdfViewerDialog(
            doc = JuknisDocumentItem(
                id = "TPL-PDF-PREVIEW",
                judul = pdfTitle,
                kategori = "Pedoman Template Surat",
                nomorDokumen = "FORMAT-RESMI",
                deskripsi = "File PDF Pedoman Format Surat Resmi MMQA AR2",
                pdfName = pdfTitle,
                pdfDataUrl = dataUrl
            ),
            onSaveUploadedPdf = { _, _, _ ->
                viewModel.showToast("Gunakan tombol Cetak / PDF pada dokumen surat untuk mencetak hasil akhir.")
            },
            onDismiss = { viewingTemplatePdf = null }
        )
    }

    if (showTemplateModal) {
        val init = editingTemplate
        var kode by remember(init) { mutableStateOf(init?.kodeSurat ?: "A") }
        var nama by remember(init) { mutableStateOf(init?.nama ?: "") }
        var perihal by remember(init) { mutableStateOf(init?.perihalDefault ?: "") }
        var lampiranDef by remember(init) { mutableStateOf(init?.lampiranDefault ?: "-") }
        var tujuanDef by remember(init) { mutableStateOf(init?.tujuanDefault ?: "Yth. Seluruh Mu’allim Firqoh MMQA AR2") }
        var isi by remember(init) { mutableStateOf(init?.isiDefault ?: "") }
        var kop1 by remember(init) { mutableStateOf(init?.kopBaris1 ?: "MAJELIS MUSYAWARAH QIRA'ATIL KUTUB & AL-QUR'AN") }
        var kop2 by remember(init) { mutableStateOf(init?.kopBaris2 ?: "MMQA AL RIFAI 02 (AR2)") }
        var kop3 by remember(init) { mutableStateOf(init?.kopBaris3 ?: "Sekretariat Dewan Harian Majelis • Sistem Administrasi Terpadu") }
        var kotaDef by remember(init) { mutableStateOf(init?.kotaDefault ?: "Malang") }
        var ketuaDef by remember(init) { mutableStateOf(init?.penandatanganDefault ?: "Ust. H. Ahmad Fauzi, M.Pd") }
        var jabKetuaDef by remember(init) { mutableStateOf(init?.jabatanDefault ?: "Ketua Dewan Harian MMQA") }
        var sekDef by remember(init) { mutableStateOf(init?.sekretarisDefault ?: "Ust. Muhammad Ridwan") }
        var jabSekDef by remember(init) { mutableStateOf(init?.jabatanSekretarisDefault ?: "Sekretaris Umum MMQA") }
        var showSekDef by remember(init) { mutableStateOf(init?.tampilkanSekretaris ?: true) }
        var ttdKetuaDef by remember(init) { mutableStateOf(init?.ttdKetuaDefault ?: "") }
        var ttdSekDef by remember(init) { mutableStateOf(init?.ttdSekretarisDefault ?: "") }
        var fontStyle by remember(init) { mutableStateOf(init?.fontStyle ?: "Times New Roman (Resmi)") }
        var kertas by remember(init) { mutableStateOf(init?.ukuranKertas ?: "A4") }
        var marginTxt by remember(init) { mutableStateOf((init?.marginMm ?: 20).toString()) }
        var pedoman by remember(init) { mutableStateOf(init?.pedomanFormat ?: "") }
        var pdfName by remember(init) { mutableStateOf(init?.pdfName ?: "") }
        var pdfDataUrl by remember(init) { mutableStateOf(init?.pdfDataUrl ?: "") }
        var tplSigTarget by remember { mutableStateOf<String?>(null) }

        MmqaModalDialog(
            title = if (init == null) "📐 Tambah Pedoman Template Surat" else "✏️ Edit Pedoman Template Surat",
            onDismiss = { showTemplateModal = false }
        ) {
            CompactDropdownSelector(
                label = "Kode Surat",
                selectedText = MmqaConstants.getKodeSuratLabel(kode),
                options = listOf("A" to "Kode A — Undangan", "B" to "Kode B — Permohonan & Pemberitahuan", "C" to "Kode C — Instruksi"),
                onSelect = { kode = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Template") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onPickPdfTemplate { fName, dUrl ->
                        pdfName = fName
                        pdfDataUrl = dUrl
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (pdfName.isBlank()) "📎 Unggah PDF Pedoman Format (Opsional)" else "📎 PDF Terpilih: $pdfName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald900
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Kop Surat & Tata Letak Resmi", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Emerald900)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = kop1, onValueChange = { kop1 = it }, label = { Text("Kop Baris 1", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = kop2, onValueChange = { kop2 = it }, label = { Text("Kop Baris 2 (Judul Utama)", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = kop3, onValueChange = { kop3 = it }, label = { Text("Kop Baris 3 (Alamat/Sub)", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactDropdownSelector(
                    label = "Jenis Huruf",
                    selectedText = fontStyle,
                    options = listOf("Times New Roman (Resmi)" to "Times New Roman (Resmi)", "Sans-Serif (Modern)" to "Sans-Serif (Modern)"),
                    onSelect = { fontStyle = it },
                    modifier = Modifier.weight(1.3f)
                )
                CompactDropdownSelector(
                    label = "Kertas",
                    selectedText = kertas,
                    options = listOf("A4" to "A4", "F4 / Folio" to "F4 / Folio"),
                    onSelect = { kertas = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = kotaDef, onValueChange = { kotaDef = it }, label = { Text("Kota Default", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = marginTxt, onValueChange = { marginTxt = it.filter { c -> c.isDigit() } }, label = { Text("Margin (mm)", fontSize = 11.sp) }, modifier = Modifier.weight(0.7f))
                OutlinedTextField(value = lampiranDef, onValueChange = { lampiranDef = it }, label = { Text("Lampiran", fontSize = 11.sp) }, modifier = Modifier.weight(0.8f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = tujuanDef, onValueChange = { tujuanDef = it }, label = { Text("Tujuan Default", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = perihal, onValueChange = { perihal = it }, label = { Text("Perihal Default", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = isi, onValueChange = { isi = it }, label = { Text("Kerangka Isi Surat Default", fontSize = 11.sp) }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = pedoman,
                onValueChange = { pedoman = it },
                label = { Text("Catatan Pedoman Format Detail", fontSize = 11.sp) },
                placeholder = { Text("Aturan penomoran, spasi, susunan acara, dll.", fontSize = 11.sp) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Penandatangan Default (Ketua & Sekretaris)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Emerald900)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = ketuaDef, onValueChange = { ketuaDef = it }, label = { Text("Ketua Default", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = jabKetuaDef, onValueChange = { jabKetuaDef = it }, label = { Text("Jabatan Ketua", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            SignatureActionCard(
                label = "TTD Default Ketua (Opsional)",
                dataUrl = ttdKetuaDef,
                onDrawClick = { tplSigTarget = "ketua" },
                onClearClick = { ttdKetuaDef = "" },
                testTagPrefix = "tpl_ttd_ketua"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sertakan Sekretaris pada Template", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Switch(checked = showSekDef, onCheckedChange = { showSekDef = it })
            }
            if (showSekDef) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sekDef, onValueChange = { sekDef = it }, label = { Text("Sekretaris Default", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jabSekDef, onValueChange = { jabSekDef = it }, label = { Text("Jabatan Sekretaris", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                SignatureActionCard(
                    label = "TTD Default Sekretaris (Opsional)",
                    dataUrl = ttdSekDef,
                    onDrawClick = { tplSigTarget = "sekretaris" },
                    onClearClick = { ttdSekDef = "" },
                    testTagPrefix = "tpl_ttd_sekretaris"
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showTemplateModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (nama.isNotBlank() && perihal.isNotBlank() && isi.isNotBlank()) {
                            viewModel.saveTemplateSurat(
                                id = init?.id,
                                kode = kode,
                                nama = nama,
                                perihal = perihal,
                                isi = isi,
                                pdfName = pdfName,
                                pdfDataUrl = pdfDataUrl,
                                kopBaris1 = kop1,
                                kopBaris2 = kop2,
                                kopBaris3 = kop3,
                                kotaDefault = kotaDef,
                                lampiranDefault = lampiranDef,
                                tujuanDefault = tujuanDef,
                                penandatanganDefault = ketuaDef,
                                jabatanDefault = jabKetuaDef,
                                sekretarisDefault = sekDef,
                                jabatanSekretarisDefault = jabSekDef,
                                tampilkanSekretaris = showSekDef,
                                ttdKetuaDefault = ttdKetuaDef,
                                ttdSekretarisDefault = ttdSekDef,
                                fontStyle = fontStyle,
                                ukuranKertas = kertas,
                                marginMm = marginTxt.toIntOrNull()?.coerceIn(10, 40) ?: 20,
                                pedomanFormat = pedoman
                            )
                            showTemplateModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Simpan Pedoman", fontWeight = FontWeight.ExtraBold) }
            }
        }

        tplSigTarget?.let { target ->
            val isKetua = target == "ketua"
            SignaturePadDialog(
                title = if (isKetua) "TTD Default Ketua (Template)" else "TTD Default Sekretaris (Template)",
                signerName = if (isKetua) ketuaDef else sekDef,
                signerRole = if (isKetua) jabKetuaDef else jabSekDef,
                onSaveSignature = { url ->
                    if (isKetua) ttdKetuaDef = url else ttdSekDef = url
                },
                onDismiss = { tplSigTarget = null }
            )
        }
    }
}

@Composable
private fun SuratPaperPreview(
    kop1: String,
    kop2: String,
    kop3: String,
    kota: String,
    nomor: String,
    tanggal: String,
    lampiran: String,
    perihal: String,
    tujuan: String,
    isi: String,
    penandatangan: String,
    jabatan: String,
    sekretaris: String,
    jabatanSekretaris: String,
    tampilkanSekretaris: Boolean,
    ttdKetuaDataUrl: String,
    ttdSekretarisDataUrl: String,
    fontStyle: String
) {
    val isSerif = fontStyle.contains("Times", ignoreCase = true)
    val family = if (isSerif) FontFamily.Serif else FontFamily.SansSerif
    val ketuaBmp = remember(ttdKetuaDataUrl) { decodeSignatureImageBitmap(ttdKetuaDataUrl) }
    val sekBmp = remember(ttdSekretarisDataUrl) { decodeSignatureImageBitmap(ttdSekretarisDataUrl) }

    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(kop1, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = family, color = Emerald900, textAlign = TextAlign.Center)
                Text(kop2, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = family, color = Slate900, textAlign = TextAlign.Center)
                Text(kop3, fontSize = 10.sp, fontFamily = family, color = Slate500, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 2.dp, color = Slate900)
            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(thickness = 0.8.dp, color = Slate700)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nomor : $nomor", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = family, color = Slate900)
                    Text("Lampiran : $lampiran", fontSize = 11.sp, fontFamily = family, color = Slate700)
                    Text("Perihal : $perihal", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = family, color = Slate900)
                }
                Text("$kota, $tanggal", fontSize = 10.sp, fontFamily = family, color = Slate700)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Kepada Yth.", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = family, color = Slate900)
            Text(tujuan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = family, color = Slate900)
            Text("Di Tempat", fontSize = 11.sp, fontFamily = family, color = Slate700)
            Spacer(modifier = Modifier.height(10.dp))
            Text(isi, fontSize = 11.sp, lineHeight = 17.sp, fontFamily = family, color = Slate900)
            Spacer(modifier = Modifier.height(18.dp))

            if (tampilkanSekretaris && sekretaris.isNotBlank()) {
                Text(
                    text = "Hormat Kami,\nDewan Harian $kop2",
                    fontSize = 10.sp,
                    fontFamily = family,
                    color = Slate700,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SignatoryPreviewColumn(
                        jabatan = jabatan,
                        nama = penandatangan,
                        sigBitmap = ketuaBmp,
                        fontFamily = family,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    SignatoryPreviewColumn(
                        jabatan = jabatanSekretaris,
                        nama = sekretaris,
                        sigBitmap = sekBmp,
                        fontFamily = family,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 160.dp)) {
                        Text("Hormat Kami,", fontSize = 11.sp, fontFamily = family, color = Slate700)
                        SignatoryPreviewColumn(
                            jabatan = jabatan,
                            nama = penandatangan,
                            sigBitmap = ketuaBmp,
                            fontFamily = family
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatoryPreviewColumn(
    jabatan: String,
    nama: String,
    sigBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = jabatan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
            color = Slate900,
            textAlign = TextAlign.Center
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(vertical = 3.dp)
        ) {
            if (sigBitmap != null) {
                Image(
                    bitmap = sigBitmap,
                    contentDescription = "Tanda tangan $nama",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = nama,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = fontFamily,
            color = Slate900,
            textAlign = TextAlign.Center,
            textDecoration = TextDecoration.Underline
        )
    }
}

private fun buildPrintableSuratHtml(
    kop1: String,
    kop2: String,
    kop3: String,
    kota: String,
    nomor: String,
    tanggal: String,
    lampiran: String,
    perihal: String,
    tujuan: String,
    isi: String,
    penandatangan: String,
    jabatan: String,
    sekretaris: String,
    jabatanSekretaris: String,
    tampilkanSekretaris: Boolean,
    ttdKetuaDataUrl: String,
    ttdSekretarisDataUrl: String,
    fontStyle: String,
    ukuranKertas: String,
    marginMm: Int
): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val cssFont = if (fontStyle.contains("Times", ignoreCase = true)) {
        "'Times New Roman', Times, Georgia, serif"
    } else {
        "Arial, Helvetica, sans-serif"
    }
    val pageSizeCss = if (ukuranKertas.contains("F4", ignoreCase = true)) "215mm 330mm" else "A4"
    val safeMargin = marginMm.coerceIn(10, 40)

    val ketuaSigHtml = if (ttdKetuaDataUrl.startsWith("data:image/")) {
        """<img class="sig-img" src="$ttdKetuaDataUrl" alt="TTD Ketua"/>"""
    } else {
        """<div class="sig-space"></div>"""
    }
    val sekSigHtml = if (ttdSekretarisDataUrl.startsWith("data:image/")) {
        """<img class="sig-img" src="$ttdSekretarisDataUrl" alt="TTD Sekretaris"/>"""
    } else {
        """<div class="sig-space"></div>"""
    }

    val ttdBlockHtml = if (tampilkanSekretaris && sekretaris.isNotBlank()) {
        """
        <div class="ttd-header">Hormat Kami,<br/>Dewan Harian ${esc(kop2)}</div>
        <div class="ttd-dual">
          <div class="ttd-col">
            <div><strong>${esc(jabatan)}</strong></div>
            $ketuaSigHtml
            <div><u><strong>${esc(penandatangan)}</strong></u></div>
          </div>
          <div class="ttd-col">
            <div><strong>${esc(jabatanSekretaris)}</strong></div>
            $sekSigHtml
            <div><u><strong>${esc(sekretaris)}</strong></u></div>
          </div>
        </div>
        """.trimIndent()
    } else {
        """
        <div class="ttd-single">
          <div>Hormat Kami,</div>
          <div><strong>${esc(jabatan)}</strong></div>
          $ketuaSigHtml
          <div><u><strong>${esc(penandatangan)}</strong></u></div>
        </div>
        """.trimIndent()
    }

    return """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"><style>
          @page { size: $pageSizeCss; margin: ${safeMargin}mm; }
          body { font-family: $cssFont; color: #0f172a; padding: 12px; line-height: 1.55; font-size: 12pt; }
          .kop { text-align: center; border-bottom: 3px double #0f172a; padding-bottom: 10px; margin-bottom: 18px; }
          .kop h1 { margin: 2px 0; font-size: 16pt; letter-spacing: 0.4px; }
          .kop p { margin: 0; font-size: 10.5pt; color: #064e3b; font-weight: bold; }
          .kop .sub { font-size: 9.5pt; color: #475569; margin-top: 2px; }
          .meta { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; font-size: 11.5pt; }
          .isi { white-space: pre-line; text-align: justify; margin: 18px 0 30px 0; }
          .ttd-header { text-align: center; font-size: 11pt; margin-bottom: 12px; }
          .ttd-dual { display: flex; justify-content: space-between; gap: 24px; text-align: center; page-break-inside: avoid; }
          .ttd-col { flex: 1; }
          .ttd-single { float: right; text-align: center; min-width: 240px; page-break-inside: avoid; }
          .sig-img { height: 68px; max-width: 190px; object-fit: contain; display: block; margin: 6px auto; }
          .sig-space { height: 70px; }
        </style></head>
        <body>
          <div class="kop">
            <p>${esc(kop1)}</p>
            <h1>${esc(kop2)}</h1>
            <div class="sub">${esc(kop3)}</div>
          </div>
          <div class="meta">
            <div>
              <div><strong>Nomor :</strong> ${esc(nomor)}</div>
              <div><strong>Lampiran :</strong> ${esc(lampiran)}</div>
              <div><strong>Perihal :</strong> ${esc(perihal)}</div>
            </div>
            <div>${esc(kota)}, ${esc(tanggal)}</div>
          </div>
          <div>
            <div><strong>Kepada Yth.</strong></div>
            <div><strong>${esc(tujuan)}</strong></div>
            <div>Di Tempat</div>
          </div>
          <div class="isi">${esc(isi)}</div>
          $ttdBlockHtml
        </body>
        </html>
    """.trimIndent()
}
