package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.example.ui.components.CompactDropdownSelector
import com.example.ui.components.SectionCard
import com.example.ui.components.SubmenuTabsRow
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
                        label = "Sesi Majelis",
                        selectedText = "Sesi ${uiState.absensiSesi}",
                        options = listOf("Pagi" to "Sesi Pagi", "Sore" to "Sesi Sore", "Malam" to "Sesi Malam"),
                        onSelect = { sesi -> viewModel.updateState { it.copy(absensiSesi = sesi) } },
                        modifier = Modifier.weight(1f)
                    )
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
                            }
                        )
                    }
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
    onCatatanCommit: (String) -> Unit
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
            Column {
                Text(text = row.nama, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                Text(
                    text = row.subInfo,
                    fontSize = 11.sp,
                    color = Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
