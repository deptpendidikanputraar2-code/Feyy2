package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    var showTemplateModal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubmenuTabsRow(
            items = enabledSubmenus.map { it.id to it.label },
            selectedId = activeTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(suratTab = tabId) } }
        )

        when (activeTab) {
            "buat" -> {
                val formCard: @Composable () -> Unit = {
                    SectionCard {
                        Text("✍️ Pembuatan Surat Resmi", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
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
                            label = "Pilih Template Surat",
                            selectedText = tplOptions.firstOrNull { it.first == uiState.suratDraftTemplateId }?.second ?: "-- Pilih Template --",
                            options = tplOptions,
                            onSelect = { tid -> viewModel.onSelectSuratTemplate(tid) }
                        )

                        val selectedTpl = db.suratTemplates.firstOrNull { it.id == uiState.suratDraftTemplateId }
                        if (selectedTpl != null && selectedTpl.pdfName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(color = Amber50, border = BorderStroke(1.dp, Amber200), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "📄 Format PDF Terlampir: ${selectedTpl.pdfName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Amber950,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
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
                        OutlinedTextField(
                            value = uiState.suratDraftPerihal,
                            onValueChange = { v -> viewModel.updateState { it.copy(suratDraftPerihal = v) } },
                            label = { Text("Perihal") },
                            modifier = Modifier.fillMaxWidth()
                        )
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.suratDraftPenandatangan,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftPenandatangan = v) } },
                                label = { Text("Penandatangan", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = uiState.suratDraftJabatan,
                                onValueChange = { v -> viewModel.updateState { it.copy(suratDraftJabatan = v) } },
                                label = { Text("Jabatan", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (canPrintPdf || canPublishSurat) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                if (canPrintPdf) {
                                    OutlinedButton(
                                        onClick = {
                                            printHtmlDocument(
                                                context,
                                                "Surat_${uiState.suratDraftNomor}",
                                                buildPrintableSuratHtml(
                                                    uiState.suratDraftNomor, uiState.suratDraftTanggal, uiState.suratDraftLampiran,
                                                    uiState.suratDraftPerihal, uiState.suratDraftTujuan, uiState.suratDraftIsi,
                                                    uiState.suratDraftPenandatangan, uiState.suratDraftJabatan
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🖨️ Cetak / PDF", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                                    }
                                }
                                if (canPublishSurat) {
                                    Button(
                                        onClick = { viewModel.publishAndArchiveSurat() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.3f)
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
                            Text("📄 PRATINJAU DOKUMEN SURAT RESMI:", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                            Spacer(modifier = Modifier.height(8.dp))
                            SuratPaperPreview(
                                nomor = uiState.suratDraftNomor,
                                tanggal = uiState.suratDraftTanggal,
                                lampiran = uiState.suratDraftLampiran,
                                perihal = uiState.suratDraftPerihal,
                                tujuan = uiState.suratDraftTujuan,
                                isi = uiState.suratDraftIsi,
                                penandatangan = uiState.suratDraftPenandatangan,
                                jabatan = uiState.suratDraftJabatan
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
                                    }
                                    Text(s.nomorSurat, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = Slate900)
                                    Text(s.perihal, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                                    Text("Tujuan: ${s.tujuan} • TTD: ${s.penandatangan}", fontSize = 11.sp, color = Slate700)
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
                            Text("📂 Template Format Surat (PDF & Teks)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text("Tambahkan format surat PDF atau kerangka teks siap pakai", fontSize = 11.sp, color = Slate500)
                        }
                        if (canManageTemplates) {
                            Button(
                                onClick = { showTemplateModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                                shape = RoundedCornerShape(12.dp)
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
                                        StatusPill(MmqaConstants.getKodeSuratLabel(t.kodeSurat), Emerald100, Emerald900)
                                        if (t.pdfName.isNotBlank()) StatusPill("PDF", Rose100, Rose800)
                                    }
                                    Text(t.nama, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Slate900)
                                    Text("Perihal: ${t.perihalDefault}", fontSize = 11.sp, color = Slate700)
                                    if (t.pdfName.isNotBlank()) {
                                        Text("📎 File: ${t.pdfName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber900)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.updateState { it.copy(suratTab = "buat") }
                                            viewModel.onSelectSuratTemplate(t.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Gunakan Template →", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewArsip?.let { s ->
        MmqaModalDialog(title = "📄 Arsip Surat: ${s.nomorSurat}", onDismiss = { previewArsip = null }) {
            SuratPaperPreview(
                nomor = s.nomorSurat,
                tanggal = s.tanggal,
                lampiran = s.lampiran,
                perihal = s.perihal,
                tujuan = s.tujuan,
                isi = s.isi,
                penandatangan = s.penandatangan,
                jabatan = s.jabatanPenandatangan
            )
            if (canPrintPdf) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        printHtmlDocument(
                            context,
                            "Surat_${s.nomorSurat}",
                            buildPrintableSuratHtml(
                                s.nomorSurat, s.tanggal, s.lampiran, s.perihal,
                                s.tujuan, s.isi, s.penandatangan, s.jabatanPenandatangan
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

    if (showTemplateModal) {
        var kode by remember { mutableStateOf("A") }
        var nama by remember { mutableStateOf("") }
        var perihal by remember { mutableStateOf("") }
        var isi by remember { mutableStateOf("") }
        var pdfName by remember { mutableStateOf("") }
        var pdfDataUrl by remember { mutableStateOf("") }

        MmqaModalDialog(title = "📂 Tambah Format / Template Surat (PDF)", onDismiss = { showTemplateModal = false }) {
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
                    text = if (pdfName.isBlank()) "📎 Pilih File PDF Template (Opsional)" else "📎 Terpilih: $pdfName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald900
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = perihal, onValueChange = { perihal = it }, label = { Text("Perihal Default") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = isi, onValueChange = { isi = it }, label = { Text("Kerangka Isi Surat Default") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showTemplateModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (nama.isNotBlank() && perihal.isNotBlank() && isi.isNotBlank()) {
                            viewModel.saveTemplateSurat(kode, nama, perihal, isi, pdfName, pdfDataUrl)
                            showTemplateModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Simpan Template", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun SuratPaperPreview(
    nomor: String,
    tanggal: String,
    lampiran: String,
    perihal: String,
    tujuan: String,
    isi: String,
    penandatangan: String,
    jabatan: String
) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("MAJELIS MUSYAWARAH QIRA'ATIL KUTUB & AL-QUR'AN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald900, textAlign = TextAlign.Center)
                Text("MMQA AL RIFAI 02 (AR2)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900, textAlign = TextAlign.Center)
                Text("Sekretariat Dewan Harian Majelis • Sistem Administrasi Terpadu", fontSize = 10.sp, color = Slate500, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 2.dp, color = Slate900)
            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nomor : $nomor", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                    Text("Lampiran : $lampiran", fontSize = 11.sp, color = Slate700)
                    Text("Perihal : $perihal", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                }
                Text("Tanggal: $tanggal", fontSize = 10.sp, color = Slate700)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Kepada Yth.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(tujuan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
            Text("Di Tempat", fontSize = 11.sp, color = Slate700)
            Spacer(modifier = Modifier.height(10.dp))
            Text(isi, fontSize = 11.sp, lineHeight = 17.sp, color = Slate900)
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 160.dp)) {
                    Text("Hormat Kami,", fontSize = 11.sp, color = Slate700)
                    Text(jabatan, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = penandatangan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }
    }
}

private fun buildPrintableSuratHtml(
    nomor: String,
    tanggal: String,
    lampiran: String,
    perihal: String,
    tujuan: String,
    isi: String,
    penandatangan: String,
    jabatan: String
): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"><style>
          body { font-family: sans-serif; color: #0f172a; padding: 24px; line-height: 1.5; font-size: 12pt; }
          .kop { text-align: center; border-bottom: 3px double #0f172a; padding-bottom: 10px; margin-bottom: 16px; }
          .kop h1 { margin: 2px 0; font-size: 16pt; }
          .kop p { margin: 0; font-size: 10pt; color: #064e3b; font-weight: bold; }
          .meta { display: flex; justify-content: space-between; margin-bottom: 16px; font-size: 11pt; }
          .isi { white-space: pre-line; text-align: justify; margin: 18px 0 36px 0; }
          .ttd { float: right; text-align: center; min-width: 220px; }
        </style></head>
        <body>
          <div class="kop">
            <p>MAJELIS MUSYAWARAH QIRA'ATIL KUTUB &amp; AL-QUR'AN</p>
            <h1>MMQA AL RIFAI 02 (AR2)</h1>
            <div style="font-size:9pt;color:#475569;">Sekretariat Dewan Harian Majelis • Sistem Administrasi Terpadu</div>
          </div>
          <div class="meta">
            <div>
              <div><strong>Nomor:</strong> ${esc(nomor)}</div>
              <div><strong>Lampiran:</strong> ${esc(lampiran)}</div>
              <div><strong>Perihal:</strong> ${esc(perihal)}</div>
            </div>
            <div>Tanggal: ${esc(tanggal)}</div>
          </div>
          <div>
            <div><strong>Kepada Yth.</strong></div>
            <div><strong>${esc(tujuan)}</strong></div>
            <div>Di Tempat</div>
          </div>
          <div class="isi">${esc(isi)}</div>
          <div class="ttd">
            <div>Hormat Kami,</div>
            <div><strong>${esc(jabatan)}</strong></div>
            <div style="height:60px;"></div>
            <div><u><strong>${esc(penandatangan)}</strong></u></div>
          </div>
        </body>
        </html>
    """.trimIndent()
}
