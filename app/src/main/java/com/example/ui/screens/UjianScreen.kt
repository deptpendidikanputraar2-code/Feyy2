package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.round

@Composable
fun UjianScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    onExportCsv: (filename: String, csvContent: String) -> Unit
) {
    val context = LocalContext.current
    val canTogglePeserta = viewModel.canAccessAnakSub("ujian_peserta_toggle")
    val canInputNilai = viewModel.canAccessAnakSub("ujian_input_nilai")
    val canExportCsv = viewModel.canAccessAnakSub("ujian_export_csv")
    val canShareChat = viewModel.canAccessAnakSub("ujian_share_chat")
    val canManageUjianMateri = viewModel.canAccessAnakSub("ujian_materi_manage")
    val canManageRubrik = viewModel.canAccessAnakSub("ujian_rubrik_manage")

    val enabledSubmenus = viewModel.getAccessibleSubmenus("ujian")
    if (enabledSubmenus.isEmpty()) {
        SectionCard {
            Text(
                text = "🔒 Tidak ada submenu Ujian yang diizinkan untuk akun Anda.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500
            )
        }
        return
    }
    val activeTab = if (enabledSubmenus.any { it.id == uiState.ujianTab }) {
        uiState.ujianTab
    } else {
        enabledSubmenus.first().id
    }

    val filteredSantri = db.santri.filter { viewModel.matchesFirqohFilter(it.firqohId, uiState.ujianFirqoh) }
    val ujianFilterLabel = viewModel.getFirqohFilterDisplayLabel(uiState.ujianFirqoh)

    var inputNilaiTargetId by remember { mutableStateOf<String?>(null) }
    var showInputNilaiModal by remember { mutableStateOf(false) }

    var editingUjianMateri by remember { mutableStateOf<UjianMateriItem?>(null) }
    var showUjianMateriModal by remember { mutableStateOf(false) }

    var editingRubrik by remember { mutableStateOf<UjianFormatItem?>(null) }
    var showRubrikModal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SubmenuTabsRow(
            items = enabledSubmenus.map { it.id to it.label },
            selectedId = activeTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(ujianTab = tabId) } }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val options = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = true)
            CompactDropdownSelector(
                label = "",
                selectedText = options.firstOrNull { it.first == uiState.ujianFirqoh }?.second ?: ujianFilterLabel,
                options = options,
                onSelect = { fid -> viewModel.updateState { it.copy(ujianFirqoh = fid) } },
                modifier = Modifier.weight(1f)
            )
            if (canInputNilai) {
                Button(
                    onClick = {
                        val firstPeserta = db.santri.firstOrNull { it.pesertaUjian }?.id
                        if (firstPeserta == null) {
                            viewModel.showToast("Belum ada peserta ujian terdaftar di Data Induk.", isError = true)
                        } else {
                            inputNilaiTargetId = firstPeserta
                            showInputNilaiModal = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Emerald950),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text("+ Input Nilai", fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        when (activeTab) {
            "peserta" -> {
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📋 Daftar Peserta Ujian (Data Induk)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text("Nomor peserta & data santri ditarik langsung dari master Data Induk", fontSize = 11.sp, color = Slate500)
                        }
                        StatusPill(
                            "${filteredSantri.count { it.pesertaUjian }}/${filteredSantri.size}",
                            Emerald100, Emerald900
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    WaPdfActionRow(
                        onShareWa = {
                            val msg = MmqaReportFormatters.buildUjianPesertaWa(db, ujianFilterLabel, filteredSantri)
                            shareTextToWhatsApp(context, msg, "Kirim Daftar Peserta Ujian")
                        },
                        onDownloadPdf = {
                            val html = MmqaReportFormatters.buildUjianPesertaPdfHtml(db, ujianFilterLabel, filteredSantri)
                            printHtmlDocument(context, "Peserta_Ujian_MMQA_AR2", html)
                        },
                        waTestTag = "ujian_peserta_wa_btn",
                        pdfTestTag = "ujian_peserta_pdf_btn"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredSantri.forEach { s ->
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
                                        Text(
                                            text = "UJN-${s.nis}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Emerald900
                                        )
                                        Text(text = s.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                        Text(
                                            text = viewModel.getFirqohName(s.firqohId),
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        StatusPill(
                                            text = if (s.pesertaUjian) "✓ Terdaftar Ujian" else "⏸ Ditangguhkan",
                                            bgColor = if (s.pesertaUjian) Emerald100 else Amber100,
                                            textColor = if (s.pesertaUjian) Emerald900 else Amber900,
                                            modifier = Modifier.clickable(enabled = canTogglePeserta) {
                                                viewModel.toggleSantriPesertaUjian(s.id)
                                            }
                                        )
                                    }
                                    if (canInputNilai) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                inputNilaiTargetId = s.id
                                                showInputNilaiModal = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Nilai →", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "rekapan" -> {
                val pesertaList = filteredSantri.filter { it.pesertaUjian }
                val rowsData = pesertaList.map { s ->
                    val nil = db.nilaiUjian.firstOrNull { it.santriId == s.id }
                    val kkm = viewModel.getKkmForFirqoh(s.firqohId)
                    Triple(s, nil, kkm)
                }.sortedByDescending { it.second?.nilaiAkhir ?: -1.0 }

                val sudahUjian = rowsData.filter { it.second != null }
                val lulusCount = sudahUjian.count { (it.second?.nilaiAkhir ?: 0.0) >= it.third }
                val avgNilai = if (sudahUjian.isNotEmpty()) {
                    String.format("%.1f", sudahUjian.sumOf { it.second?.nilaiAkhir ?: 0.0 } / sudahUjian.size)
                } else "0.0"

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMetricCard("Rata-Rata Nilai", avgNilai, "Dari ${sudahUjian.size} dinilai", "📈", Emerald900, Emerald100, modifier = Modifier.weight(1f))
                    StatMetricCard("Lulus KKM", "$lulusCount / ${pesertaList.size}", "Peserta terdaftar", "🏅", Emerald700, Emerald100, modifier = Modifier.weight(1f))
                }

                if (canExportCsv || canShareChat) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (canExportCsv) {
                            OutlinedButton(
                                onClick = { onExportCsv("Rekapan_Ujian_MMQA_AR2.csv", viewModel.buildRekapanUjianCsv()) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📥 Ekspor CSV", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                            }
                        }
                        if (canShareChat) {
                            Button(
                                onClick = { viewModel.shareRekapanUjianToChat() },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("💬 Kirim ke Grup", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                WaPdfActionRow(
                    onShareWa = {
                        val msg = MmqaReportFormatters.buildUjianRekapanWa(db, ujianFilterLabel, rowsData, avgNilai, lulusCount)
                        shareTextToWhatsApp(context, msg, "Kirim Rekapan Hasil Ujian")
                    },
                    onDownloadPdf = {
                        val html = MmqaReportFormatters.buildUjianRekapanPdfHtml(db, ujianFilterLabel, rowsData, avgNilai, lulusCount)
                        printHtmlDocument(context, "Rekapan_Hasil_Ujian_MMQA_AR2", html)
                    },
                    waTestTag = "ujian_rekapan_wa_btn",
                    pdfTestTag = "ujian_rekapan_pdf_btn"
                )

                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowsData.forEachIndexed { idx, (santri, nilai, kkm) ->
                            val lulus = nilai != null && nilai.nilaiAkhir >= kkm
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Text("#${idx + 1}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Slate500)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(santri.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                                Text(
                                                    "UJN-${santri.nis} • ${viewModel.getFirqohName(santri.firqohId)} (KKM $kkm)",
                                                    fontSize = 10.sp,
                                                    color = Slate500,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            text = nilai?.nilaiAkhir?.toString() ?: "-",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Emerald900
                                        )
                                    }

                                    if (nilai != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            db.ujianFormat.forEach { fmt ->
                                                val skor = nilai.skorAspek[fmt.id]?.let {
                                                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                                                } ?: "-"
                                                Surface(color = Color.White, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(8.dp)) {
                                                    Text(
                                                        text = "${fmt.aspek.split(" ").first()}: $skor",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate700,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (nilai != null) {
                                            StatusPill(
                                                text = "${nilai.predikat} • ${if (lulus) "LULUS KKM" else "REMEDIAL"}",
                                                bgColor = if (lulus) Emerald100 else Rose100,
                                                textColor = if (lulus) Emerald900 else Rose800
                                            )
                                        } else {
                                            StatusPill("BELUM UJIAN", Slate100, Slate500)
                                        }

                                        if (canInputNilai) {
                                            Button(
                                                onClick = {
                                                    inputNilaiTargetId = santri.id
                                                    showInputNilaiModal = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Emerald950),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(if (nilai != null) "Edit Nilai" else "Input Nilai", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "materi_ujian" -> {
                if (canManageUjianMateri) {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                editingUjianMateri = null
                                showUjianMateriModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("+ Tambah Materi Ujian", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    db.ujianMateri.forEach { u ->
                        SectionCard {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StatusPill(viewModel.getFirqohName(u.firqohId), Emerald100, Emerald900)
                                StatusPill("KKM: ${u.kkm}", Amber100, Amber900)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(u.namaUjian, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text("Kitab: ${u.kitab}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text(u.keterangan, fontSize = 12.sp, color = Slate700)
                            if (canManageUjianMateri) {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = {
                                        editingUjianMateri = u
                                        showUjianMateriModal = true
                                    }) { Text("✏️ Edit", fontSize = 12.sp, color = Slate700) }
                                    TextButton(onClick = { viewModel.deleteUjianMateri(u.id) }) {
                                        Text("🗑️ Hapus", fontSize = 12.sp, color = Rose700)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val totalBobot = db.ujianFormat.sumOf { it.bobot }
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚖️ Format & Bobot Rubrik Penilaian", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                            Text(
                                text = "Total Bobot: $totalBobot% (Idealnya 100%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalBobot == 100) Emerald700 else Rose600
                            )
                        }
                        if (canManageRubrik) {
                            Button(
                                onClick = {
                                    editingRubrik = null
                                    showRubrikModal = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("+ Aspek", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        db.ujianFormat.forEach { f ->
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatusPill("${f.bobot}%", Emerald900, Amber300)
                                        Text(f.aspek, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Slate900, modifier = Modifier.weight(1f))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(f.deskripsi, fontSize = 11.sp, color = Slate700)
                                    if (canManageRubrik) {
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            TextButton(onClick = {
                                                editingRubrik = f
                                                showRubrikModal = true
                                            }) { Text("✏️ Edit", fontSize = 11.sp, color = Slate700) }
                                            TextButton(onClick = { viewModel.deleteRubrik(f.id) }) {
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
        }
    }

    if (showInputNilaiModal && inputNilaiTargetId != null) {
        InputNilaiUjianModal(
            db = db,
            initialSantriId = inputNilaiTargetId!!,
            viewModel = viewModel,
            onDismiss = { showInputNilaiModal = false }
        )
    }

    if (showUjianMateriModal) {
        var firqohId by remember { mutableStateOf(editingUjianMateri?.firqohId ?: db.firqoh.firstOrNull()?.id ?: "FRQ-1") }
        var namaUjian by remember { mutableStateOf(editingUjianMateri?.namaUjian ?: "") }
        var kitab by remember { mutableStateOf(editingUjianMateri?.kitab ?: "") }
        var kkm by remember { mutableStateOf((editingUjianMateri?.kkm ?: 75).toString()) }
        var ket by remember { mutableStateOf(editingUjianMateri?.keterangan ?: "") }

        MmqaModalDialog(
            title = if (editingUjianMateri != null) "Edit Materi Ujian" else "Tambah Materi Ujian Perfirqoh",
            onDismiss = { showUjianMateriModal = false }
        ) {
            CompactDropdownSelector("Firqoh", viewModel.getFirqohName(firqohId), db.firqoh.map { it.id to it.nama }, { firqohId = it })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = namaUjian, onValueChange = { namaUjian = it }, label = { Text("Judul / Nama Ujian") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = kitab, onValueChange = { kitab = it }, label = { Text("Kitab yang Diujikan") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = kkm,
                onValueChange = { kkm = it.filter { c -> c.isDigit() } },
                label = { Text("Nilai KKM Kelulusan") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = ket, onValueChange = { ket = it }, label = { Text("Teknis & Keterangan") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showUjianMateriModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (namaUjian.isNotBlank() && kitab.isNotBlank()) {
                            viewModel.saveUjianMateri(editingUjianMateri?.id, firqohId, namaUjian, kitab, kkm.toIntOrNull() ?: 75, ket)
                            showUjianMateriModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Simpan", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }

    if (showRubrikModal) {
        var aspek by remember { mutableStateOf(editingRubrik?.aspek ?: "") }
        var bobot by remember { mutableStateOf((editingRubrik?.bobot ?: 20).toString()) }
        var desc by remember { mutableStateOf(editingRubrik?.deskripsi ?: "") }

        MmqaModalDialog(
            title = if (editingRubrik != null) "Edit Aspek Rubrik" else "Tambah Aspek Rubrik",
            onDismiss = { showRubrikModal = false }
        ) {
            OutlinedTextField(value = aspek, onValueChange = { aspek = it }, label = { Text("Nama Aspek Penilaian") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = bobot,
                onValueChange = { bobot = it.filter { c -> c.isDigit() } },
                label = { Text("Bobot Nilai (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Deskripsi Kriteria") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showRubrikModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (aspek.isNotBlank()) {
                            viewModel.saveRubrik(editingRubrik?.id, aspek, bobot.toIntOrNull() ?: 20, desc)
                            showRubrikModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Simpan Rubrik", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun InputNilaiUjianModal(
    db: MmqaDatabase,
    initialSantriId: String,
    viewModel: MmqaViewModel,
    onDismiss: () -> Unit
) {
    val pesertaList = db.santri.filter { it.pesertaUjian }
    var selectedSantriId by remember { mutableStateOf(initialSantriId) }
    val existing = db.nilaiUjian.firstOrNull { it.santriId == selectedSantriId }

    val skorInputs = remember(selectedSantriId) {
        mutableStateMapOf<String, String>().apply {
            db.ujianFormat.forEach { f ->
                val v = existing?.skorAspek?.get(f.id) ?: 80.0
                put(f.id, if (v % 1.0 == 0.0) v.toInt().toString() else v.toString())
            }
        }
    }
    var catatan by remember(selectedSantriId) { mutableStateOf(existing?.catatan ?: "") }

    val totalWeighted = db.ujianFormat.sumOf { f -> (skorInputs[f.id]?.toDoubleOrNull() ?: 0.0) * f.bobot }
    val totalBobot = db.ujianFormat.sumOf { it.bobot }
    val previewFinal = if (totalBobot > 0) round((totalWeighted / totalBobot) * 10.0) / 10.0 else 0.0
    val previewPred = MmqaConstants.getPredikatNilai(previewFinal)

    MmqaModalDialog(title = "🎓 Input & Kalkulasi Nilai Ujian", onDismiss = onDismiss) {
        CompactDropdownSelector(
            label = "Pilih Santri Peserta Ujian (Data Induk)",
            selectedText = pesertaList.firstOrNull { it.id == selectedSantriId }?.let {
                "UJN-${it.nis} — ${it.nama}"
            } ?: "Pilih Santri",
            options = pesertaList.map { it.id to "UJN-${it.nis} — ${it.nama} (${viewModel.getFirqohName(it.firqohId)})" },
            onSelect = { selectedSantriId = it }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Surface(color = Slate50, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("INPUT SKOR PER ASPEK RUBRIK (0 - 100):", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Slate500)
                db.ujianFormat.forEach { f ->
                    Column {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(f.aspek, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1f))
                            Text("Bobot ${f.bobot}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald800)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = skorInputs[f.id] ?: "80",
                            onValueChange = { v -> skorInputs[f.id] = v.filter { c -> c.isDigit() || c == '.' } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(color = Emerald900, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Text("Nilai Akhir Berbobot:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald100)
                Text("$previewFinal ($previewPred)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Amber300)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = catatan,
            onValueChange = { catatan = it },
            label = { Text("Catatan Penguji / Mu'allim", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Batal") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val map = db.ujianFormat.associate { f ->
                        f.id to (skorInputs[f.id]?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                    }
                    viewModel.saveNilaiUjian(selectedSantriId, map, catatan)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simpan ke Rekapan Ujian", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
