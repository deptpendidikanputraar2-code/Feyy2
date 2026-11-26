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

@Composable
fun PersonaliaScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val canManage = viewModel.canAccessAnakSub("personalia_manage")
    val allowDelete = db.adminConfig.permissions.allowDeleteRecords && viewModel.canAccessAnakSub("personalia_delete")
    val canExport = viewModel.canAccessAnakSub("personalia_export")

    var searchQuery by remember { mutableStateOf("") }
    var selectedDivisi by remember { mutableStateOf("ALL") }
    var editingItem by remember { mutableStateOf<PersonaliaItem?>(null) }
    var showFormModal by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<PersonaliaItem?>(null) }

    val sortedAll = remember(db.personalia) {
        db.personalia.sortedWith(compareBy<PersonaliaItem> { it.urutan }.thenBy { it.nama })
    }
    val divisions = remember(sortedAll) {
        sortedAll.map { it.divisi.ifBlank { "Pengurus MMQA" } }.distinct()
    }
    val activeDivisi = if (selectedDivisi == "ALL" || selectedDivisi in divisions) selectedDivisi else "ALL"

    val q = searchQuery.trim().lowercase()
    val filteredList = sortedAll.filter { p ->
        val divMatches = activeDivisi == "ALL" || p.divisi.ifBlank { "Pengurus MMQA" } == activeDivisi
        val qMatches = q.isEmpty() ||
            p.nama.lowercase().contains(q) ||
            p.jabatan.lowercase().contains(q) ||
            p.divisi.lowercase().contains(q) ||
            p.kode.lowercase().contains(q)
        divMatches && qMatches
    }

    val activePeriodLabel = sortedAll.firstOrNull()?.periode?.ifBlank { "2026 - 2027" } ?: "2026 - 2027"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isWideScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Total Personalia",
                    value = "${sortedAll.size}",
                    subtitle = "Pengurus & personalia aktif",
                    iconEmoji = "🧑‍💼",
                    valueColor = Emerald900,
                    iconBg = Emerald100,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Divisi / Bidang",
                    value = "${divisions.size}",
                    subtitle = "Struktur organisasi majelis",
                    iconEmoji = "🏛️",
                    valueColor = Amber900,
                    iconBg = Amber100,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Periode Khidmah",
                    value = activePeriodLabel,
                    subtitle = "MMQA Al Rifai 02",
                    iconEmoji = "📜",
                    highlightGradient = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Total Personalia",
                    value = "${sortedAll.size}",
                    subtitle = "${divisions.size} Divisi aktif",
                    iconEmoji = "🧑‍💼",
                    valueColor = Emerald900,
                    iconBg = Emerald100,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Periode",
                    value = activePeriodLabel,
                    subtitle = "Struktur MMQA AR2",
                    iconEmoji = "📜",
                    highlightGradient = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("🔍 Cari nama, jabatan, atau divisi...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("personalia_search_input")
                    )
                    if (canManage) {
                        Button(
                            onClick = {
                                editingItem = null
                                showFormModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("personalia_add_button")
                        ) {
                            Text("+ Tambah", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // Filter Divisi
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val chips = listOf("ALL" to "Semua Divisi (${sortedAll.size})") + divisions.map { d ->
                        d to "$d (${sortedAll.count { it.divisi.ifBlank { "Pengurus MMQA" } == d }})"
                    }
                    chips.forEach { (key, label) ->
                        val active = activeDivisi == key
                        Surface(
                            color = if (active) Emerald900 else Slate100,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { selectedDivisi = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Amber300 else Slate700,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (canExport) {
                    WaPdfActionRow(
                        onShareWa = {
                            val msg = MmqaReportFormatters.buildPersonaliaWa(filteredList, activeDivisi)
                            shareTextToWhatsApp(context, msg, "Kirim Struktur Personalia MMQA")
                        },
                        onDownloadPdf = {
                            val html = MmqaReportFormatters.buildPersonaliaPdfHtml(filteredList, activeDivisi)
                            printHtmlDocument(context, "Struktur_Personalia_MMQA_AR2", html)
                        },
                        waTestTag = "personalia_wa_btn",
                        pdfTestTag = "personalia_pdf_btn"
                    )
                }

                if (canManage) {
                    OutlinedButton(
                        onClick = { viewModel.syncPersonaliaFromDataInduk() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("personalia_sync_induk_btn")
                    ) {
                        Text(
                            text = "🔄 Tarik Dewan Harian & Mu'allim dari Data Induk",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald900
                        )
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            SectionCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    Text("🧑‍💼 Belum ada data personalia", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Text(
                        text = "Tambahkan pengurus secara manual atau tarik langsung dari Data Induk.",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }
        } else {
            val grouped = filteredList.groupBy { it.divisi.ifBlank { "Pengurus MMQA" } }
            grouped.forEach { (divisiName, members) ->
                SectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🏛️ $divisiName",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Emerald900,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill("${members.size} Org", Emerald100, Emerald900)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { p ->
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
                                            name = p.nama,
                                            avatarBase64 = "",
                                            size = 42.dp,
                                            bgColor = Emerald900,
                                            textColor = Amber300
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                StatusPill("#${p.urutan}", Amber100, Amber900)
                                                Text(
                                                    text = p.kode,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Emerald800
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = p.nama,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Slate900
                                            )
                                            Text(
                                                text = p.jabatan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Emerald800,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Periode: ${p.periode}" + if (p.kontak.isNotBlank()) " • Kontak: ${p.kontak}" else "",
                                                fontSize = 11.sp,
                                                color = Slate500,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (canManage || allowDelete) {
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            if (canManage) {
                                                TextButton(onClick = {
                                                    editingItem = p
                                                    showFormModal = true
                                                }) {
                                                    Text("✏️ Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                                }
                                            }
                                            if (allowDelete) {
                                                TextButton(onClick = { deletingItem = p }) {
                                                    Text("🗑️ Hapus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
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

    if (showFormModal) {
        var kode by remember { mutableStateOf(editingItem?.kode ?: "STR-${(sortedAll.size + 1).toString().padStart(2, '0')}") }
        var nama by remember { mutableStateOf(editingItem?.nama ?: "") }
        var jabatan by remember { mutableStateOf(editingItem?.jabatan ?: "") }
        var divisi by remember { mutableStateOf(editingItem?.divisi ?: "Pimpinan & Dewan Harian") }
        var periode by remember { mutableStateOf(editingItem?.periode ?: activePeriodLabel) }
        var kontak by remember { mutableStateOf(editingItem?.kontak ?: "") }
        var urutan by remember { mutableStateOf((editingItem?.urutan ?: (sortedAll.size + 1)).toString()) }

        val presetDivisions = listOf(
            "Pimpinan & Dewan Harian",
            "Kesekretariatan & Administrasi",
            "Keuangan & Syahriah",
            "Kurikulum & Pendidikan",
            "Koordinator Firqoh",
            "Mu'allim Firqoh",
            "Sarpras & Inventaris"
        )

        MmqaModalDialog(
            title = if (editingItem != null) "Edit Struktur Personalia" else "Tambah Personalia MMQA",
            onDismiss = { showFormModal = false }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = kode,
                    onValueChange = { kode = it },
                    label = { Text("Kode") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = urutan,
                    onValueChange = { urutan = it.filter { c -> c.isDigit() } },
                    label = { Text("Urutan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nama,
                onValueChange = { nama = it },
                label = { Text("Nama Lengkap Pengurus") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = jabatan,
                onValueChange = { jabatan = it },
                label = { Text("Jabatan / Amanah") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = divisi,
                onValueChange = { divisi = it },
                label = { Text("Divisi / Bidang") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                presetDivisions.forEach { preset ->
                    Surface(
                        color = if (divisi == preset) Emerald100 else Slate100,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { divisi = preset }
                    ) {
                        Text(
                            text = preset,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (divisi == preset) Emerald900 else Slate700,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = periode,
                    onValueChange = { periode = it },
                    label = { Text("Periode") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = kontak,
                    onValueChange = { kontak = it },
                    label = { Text("No. HP / WA") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showFormModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (nama.isNotBlank() && jabatan.isNotBlank()) {
                            viewModel.savePersonalia(
                                id = editingItem?.id,
                                kode = kode.ifBlank { "STR-01" },
                                nama = nama,
                                jabatan = jabatan,
                                divisi = divisi,
                                periode = periode,
                                kontak = kontak,
                                urutan = urutan.toIntOrNull() ?: 1
                            )
                            showFormModal = false
                        } else {
                            viewModel.showToast("Nama dan jabatan wajib diisi.", isError = true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Simpan", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    deletingItem?.let { item ->
        ConfirmActionDialog(
            title = "Hapus Personalia",
            message = "Yakin ingin menghapus \"${item.nama}\" (${item.jabatan}) dari Struktur Personalia MMQA?",
            onDismiss = { deletingItem = null },
            onConfirm = { viewModel.deletePersonalia(item.id) }
        )
    }
}
