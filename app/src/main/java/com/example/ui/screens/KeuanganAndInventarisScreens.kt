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
// MODUL KEUANGAN & KONTROL SYAHRIAH MODERN
// =============================================================================
@Composable
fun KeuanganScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean,
    onExportCsv: (filename: String, csvContent: String) -> Unit
) {
    val canAddKas = viewModel.canAccessAnakSub("keuangan_kas_tambah")
    val allowDeleteKas = db.adminConfig.permissions.allowDeleteRecords && viewModel.canAccessAnakSub("keuangan_kas_hapus")
    val canExportShareKas = viewModel.canAccessAnakSub("keuangan_export_share")
    val canToggleSyahriah = viewModel.canAccessAnakSub("keuangan_syahriah_toggle")

    val enabledSubmenus = viewModel.getAccessibleSubmenus("keuangan")
    if (enabledSubmenus.isEmpty()) {
        SectionCard {
            Text(
                text = "🔒 Tidak ada submenu Keuangan yang diizinkan untuk akun Anda.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500
            )
        }
        return
    }
    val activeTab = if (enabledSubmenus.any { it.id == uiState.keuanganTab }) {
        uiState.keuanganTab
    } else {
        enabledSubmenus.first().id
    }

    val pemasukan = db.keuangan.filter { it.jenis == "pemasukan" }.sumOf { it.nominal }
    val pengeluaran = db.keuangan.filter { it.jenis == "pengeluaran" }.sumOf { it.nominal }
    val saldo = pemasukan - pengeluaran
    val lunasCount = db.santri.count { it.statusSyahriah == "Lunas" }

    var showAddKeuanganModal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isWideScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard("Total Pemasukan", MmqaConstants.formatRupiah(pemasukan), "Akumulasi kas masuk", "📥", Emerald700, Emerald100, modifier = Modifier.weight(1f))
                StatMetricCard("Total Pengeluaran", MmqaConstants.formatRupiah(pengeluaran), "Akumulasi kas keluar", "📤", Rose700, Rose100, modifier = Modifier.weight(1f))
                StatMetricCard("Saldo Kas Aktif", MmqaConstants.formatRupiah(saldo), "Dana bersih majelis", "💰", highlightGradient = true, modifier = Modifier.weight(1f))
                StatMetricCard("Syahriah Lunas", "$lunasCount / ${db.santri.size}", "Santri lunas", "💳", Slate900, Slate100, modifier = Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard("Pemasukan", MmqaConstants.formatRupiah(pemasukan), "Kas masuk", "📥", Emerald700, Emerald100, modifier = Modifier.weight(1f))
                StatMetricCard("Pengeluaran", MmqaConstants.formatRupiah(pengeluaran), "Kas keluar", "📤", Rose700, Rose100, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard("Saldo Kas", MmqaConstants.formatRupiah(saldo), "Dana bersih", "💰", highlightGradient = true, modifier = Modifier.weight(1f))
                StatMetricCard("Syahriah Lunas", "$lunasCount / ${db.santri.size}", "Santri lunas", "💳", Slate900, Slate100, modifier = Modifier.weight(1f))
            }
        }

        SubmenuTabsRow(
            items = enabledSubmenus.map { it.id to it.label },
            selectedId = activeTab,
            onSelect = { tabId -> viewModel.updateState { it.copy(keuanganTab = tabId) } }
        )

        if (activeTab == "kas") {
            val list = if (uiState.keuanganFilter == "ALL") db.keuangan else db.keuangan.filter { it.jenis == uiState.keuanganFilter }
            SectionCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf("ALL" to "Semua Transaksi", "pemasukan" to "Pemasukan", "pengeluaran" to "Pengeluaran").forEach { (key, label) ->
                        val active = uiState.keuanganFilter == key
                        Surface(
                            color = if (active) Emerald900 else Slate100,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { viewModel.updateState { it.copy(keuanganFilter = key) } }
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
                Spacer(modifier = Modifier.height(10.dp))
                if (canExportShareKas || canAddKas) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        if (canExportShareKas) {
                            OutlinedButton(
                                onClick = { onExportCsv("Laporan_Keuangan_MMQA_AR2.csv", viewModel.buildKeuanganCsv()) },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("📥 Ekspor CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700) }
                            Button(
                                onClick = { viewModel.shareKeuanganToChat() },
                                colors = ButtonDefaults.buttonColors(containerColor = Sky100, contentColor = Sky900),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("💬 Kirim Laporan Kas", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        if (canAddKas) {
                            Button(
                                onClick = { showAddKeuanganModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("+ Catat Transaksi", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { k ->
                        val isIn = k.jenis == "pemasukan"
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
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusPill(
                                            text = k.jenis.uppercase(),
                                            bgColor = if (isIn) Emerald100 else Rose100,
                                            textColor = if (isIn) Emerald900 else Rose800
                                        )
                                        Text(k.tanggal, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Slate500)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(k.kategori, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                                    Text("${k.keterangan} • Oleh: ${k.pencatat}", fontSize = 11.sp, color = Slate500)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${if (isIn) "+" else "-"} ${MmqaConstants.formatRupiah(k.nominal)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isIn) Emerald700 else Rose700
                                    )
                                    if (allowDeleteKas) {
                                        TextButton(
                                            onClick = { viewModel.deleteKeuangan(k.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Hapus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val listSantri = if (uiState.syahriahFirqoh == "ALL") db.santri else db.santri.filter { it.firqohId == uiState.syahriahFirqoh }
            SectionCard {
                Text("💳 Kontrol Pembayaran Syahriah Santri", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate900)
                Text(
                    text = "Tarif Syahriah Aktif: ${MmqaConstants.formatRupiah(db.adminConfig.permissions.syahriahNominal)} / santri (Otomatis masuk Buku Kas saat dilunaskan)",
                    fontSize = 11.sp,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(10.dp))
                val firqohOptions = listOf("ALL" to "Semua Firqoh") + db.firqoh.map { it.id to it.nama }
                CompactDropdownSelector(
                    label = "",
                    selectedText = firqohOptions.firstOrNull { it.first == uiState.syahriahFirqoh }?.second ?: "Semua Firqoh",
                    options = firqohOptions,
                    onSelect = { fid -> viewModel.updateState { it.copy(syahriahFirqoh = fid) } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listSantri.forEach { s ->
                        val lunas = s.statusSyahriah == "Lunas"
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
                                    Text("NIS ${s.nis}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Slate500)
                                    Text(s.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    Text(viewModel.getFirqohName(s.firqohId), fontSize = 11.sp, color = Slate500)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusPill(
                                        text = if (lunas) "✓ Lunas" else "⏳ Belum Lunas",
                                        bgColor = if (lunas) Emerald100 else Rose100,
                                        textColor = if (lunas) Emerald900 else Rose800
                                    )
                                }
                                if (canToggleSyahriah) {
                                    Button(
                                        onClick = { viewModel.toggleSyahriahStatus(s.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (lunas) Slate100 else Emerald800,
                                            contentColor = if (lunas) Slate700 else Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = if (lunas) "Batalkan" else "Tandai Lunas (+Kas)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddKeuanganModal) {
        var tanggal by remember { mutableStateOf(MmqaConstants.todayStr()) }
        var jenis by remember { mutableStateOf("pemasukan") }
        var kategori by remember { mutableStateOf("") }
        var nominal by remember { mutableStateOf("150000") }
        var ket by remember { mutableStateOf("") }

        MmqaModalDialog(title = "💰 Catat Transaksi Keuangan Baru", onDismiss = { showAddKeuanganModal = false }) {
            OutlinedTextField(value = tanggal, onValueChange = { tanggal = it }, label = { Text("Tanggal (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            CompactDropdownSelector(
                label = "Jenis Transaksi",
                selectedText = if (jenis == "pemasukan") "Pemasukan (+)" else "Pengeluaran (-)",
                options = listOf("pemasukan" to "Pemasukan (+)", "pengeluaran" to "Pengeluaran (-)"),
                onSelect = { jenis = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = kategori, onValueChange = { kategori = it }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nominal,
                onValueChange = { nominal = it.filter { c -> c.isDigit() } },
                label = { Text("Nominal (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = ket, onValueChange = { ket = it }, label = { Text("Keterangan Lengkap") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showAddKeuanganModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (kategori.isNotBlank() && ket.isNotBlank()) {
                            viewModel.addKeuangan(tanggal, jenis, kategori, nominal.toLongOrNull() ?: 0L, ket)
                            showAddKeuanganModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Simpan Transaksi", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

// =============================================================================
// MODUL INVENTARIS MODERN
// =============================================================================
@Composable
fun InventarisScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean,
    onExportCsv: (filename: String, csvContent: String) -> Unit
) {
    val canManage = viewModel.canAccessAnakSub("inventaris_kelola")
    val allowDelete = db.adminConfig.permissions.allowDeleteRecords && viewModel.canAccessAnakSub("inventaris_hapus")
    val canExport = viewModel.canAccessAnakSub("inventaris_export")

    val list = if (uiState.inventarisFilter == "ALL") db.inventaris else db.inventaris.filter { it.kondisi == uiState.inventarisFilter }
    val totalValuasi = db.inventaris.sumOf { it.nilaiEstimasi }
    val totalUnit = db.inventaris.sumOf { it.jumlah }
    val baikCount = db.inventaris.count { it.kondisi == "Baik" }

    var editingItem by remember { mutableStateOf<InventarisItem?>(null) }
    var showFormModal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (isWideScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard("Total Jenis Aset", "${db.inventaris.size} Item ($totalUnit Unit)", "Seluruh sarana majelis", "🏛️", Slate900, Slate100, modifier = Modifier.weight(1f))
                StatMetricCard("Valuasi Inventaris", MmqaConstants.formatRupiah(totalValuasi), "Estimasi nilai aset", "💎", highlightGradient = true, modifier = Modifier.weight(1f))
                StatMetricCard("Kondisi Baik", "$baikCount / ${db.inventaris.size} Aset", "Siap pakai", "✅", Emerald700, Emerald100, modifier = Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard("Total Aset", "${db.inventaris.size} ($totalUnit Unit)", "Jenis barang", "🏛️", Slate900, Slate100, modifier = Modifier.weight(1f))
                StatMetricCard("Kondisi Baik", "$baikCount / ${db.inventaris.size}", "Siap pakai", "✅", Emerald700, Emerald100, modifier = Modifier.weight(1f))
            }
            StatMetricCard("Total Valuasi Inventaris", MmqaConstants.formatRupiah(totalValuasi), "Estimasi nilai seluruh aset majelis", "💎", highlightGradient = true)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf("ALL" to "Semua Kondisi", "Baik" to "Baik", "Perlu Perawatan" to "Perlu Perawatan", "Rusak" to "Rusak").forEach { (k, label) ->
                val active = uiState.inventarisFilter == k
                Surface(
                    color = if (active) Emerald900 else Color.White,
                    border = if (active) null else BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { viewModel.updateState { it.copy(inventarisFilter = k) } }
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

        if (canExport || canManage) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (canExport) {
                    OutlinedButton(
                        onClick = { onExportCsv("Inventaris_MMQA_AR2.csv", viewModel.buildInventarisCsv()) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("📥 Ekspor CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700) }
                }
                if (canManage) {
                    Button(
                        onClick = {
                            editingItem = null
                            showFormModal = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Tambah Aset", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { inv ->
                val (badgeBg, badgeTxt) = when (inv.kondisi) {
                    "Baik" -> Emerald100 to Emerald900
                    "Perlu Perawatan" -> Amber100 to Amber900
                    else -> Rose100 to Rose800
                }
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(inv.kodeAset, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = Slate500)
                        StatusPill(inv.kondisi, badgeBg, badgeTxt)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(inv.nama, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Text("${inv.jumlah} ${inv.satuan} • ${inv.kategori}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    Text("📍 Lokasi: ${inv.lokasi} • PJ: ${inv.pj}", fontSize = 11.sp, color = Slate500)
                    Text("Valuasi: ${MmqaConstants.formatRupiah(inv.nilaiEstimasi)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
                    if (inv.keterangan.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Slate50, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(inv.keterangan, fontSize = 11.sp, color = Slate700, modifier = Modifier.padding(10.dp))
                        }
                    }
                    if (canManage || allowDelete) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            if (canManage) {
                                TextButton(onClick = {
                                    editingItem = inv
                                    showFormModal = true
                                }) { Text("✏️ Edit", fontSize = 12.sp, color = Slate700) }
                            }
                            if (allowDelete) {
                                TextButton(onClick = { viewModel.deleteInventaris(inv.id) }) {
                                    Text("🗑️ Hapus", fontSize = 12.sp, color = Rose700)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormModal) {
        var kode by remember { mutableStateOf(editingItem?.kodeAset ?: "INV-AR2-00${db.inventaris.size + 1}") }
        var kat by remember { mutableStateOf(editingItem?.kategori ?: "Sarana Belajar") }
        var nama by remember { mutableStateOf(editingItem?.nama ?: "") }
        var jml by remember { mutableStateOf((editingItem?.jumlah ?: 1).toString()) }
        var sat by remember { mutableStateOf(editingItem?.satuan ?: "Unit") }
        var kondisi by remember { mutableStateOf(editingItem?.kondisi ?: "Baik") }
        var lokasi by remember { mutableStateOf(editingItem?.lokasi ?: "") }
        var nilai by remember { mutableStateOf((editingItem?.nilaiEstimasi ?: 0L).toString()) }
        var pj by remember { mutableStateOf(editingItem?.pj ?: uiState.currentUser?.name ?: "") }
        var ket by remember { mutableStateOf(editingItem?.keterangan ?: "") }

        MmqaModalDialog(
            title = if (editingItem != null) "Edit Aset Inventaris" else "Tambah Aset Inventaris Modern",
            onDismiss = { showFormModal = false }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = kode, onValueChange = { kode = it }, label = { Text("Kode Aset") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = kat, onValueChange = { kat = it }, label = { Text("Kategori") }, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Barang / Aset") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jml,
                    onValueChange = { jml = it.filter { c -> c.isDigit() } },
                    label = { Text("Jumlah") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(value = sat, onValueChange = { sat = it }, label = { Text("Satuan") }, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            CompactDropdownSelector(
                label = "Kondisi",
                selectedText = kondisi,
                options = listOf("Baik" to "Baik", "Perlu Perawatan" to "Perlu Perawatan", "Rusak" to "Rusak"),
                onSelect = { kondisi = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = lokasi, onValueChange = { lokasi = it }, label = { Text("Lokasi") }, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = nilai,
                    onValueChange = { nilai = it.filter { c -> c.isDigit() } },
                    label = { Text("Valuasi (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = pj, onValueChange = { pj = it }, label = { Text("Penanggung Jawab") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = ket, onValueChange = { ket = it }, label = { Text("Keterangan Teknis") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showFormModal = false }) { Text("Batal") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (nama.isNotBlank() && kode.isNotBlank()) {
                            viewModel.saveInventaris(
                                editingItem?.id, kode, kat, nama,
                                jml.toIntOrNull() ?: 1, sat, kondisi, lokasi,
                                nilai.toLongOrNull() ?: 0L, pj, ket
                            )
                            showFormModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800)
                ) { Text("Simpan Aset", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}
