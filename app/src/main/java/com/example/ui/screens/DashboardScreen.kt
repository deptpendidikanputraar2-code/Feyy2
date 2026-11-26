package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MmqaConstants
import com.example.data.MmqaDatabase
import com.example.ui.MmqaUiState
import com.example.ui.MmqaViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    db: MmqaDatabase,
    uiState: MmqaUiState,
    viewModel: MmqaViewModel,
    isWideScreen: Boolean
) {
    val santriStats = viewModel.calcAttendanceStats("santri", null, uiState.dashFirqohFilter)
    val muallimStats = viewModel.calcAttendanceStats("muallim")
    val dewanStats = viewModel.calcAttendanceStats("dewan_harian")

    val pemasukan = db.keuangan.filter { it.jenis == "pemasukan" }.sumOf { it.nominal }
    val pengeluaran = db.keuangan.filter { it.jenis == "pengeluaran" }.sumOf { it.nominal }
    val saldoKas = pemasukan - pengeluaran

    val filteredSantri = if (uiState.dashFirqohFilter == "ALL") {
        db.santri
    } else {
        db.santri.filter { it.firqohId == uiState.dashFirqohFilter }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 4 Kartu Metrik Utama
        if (isWideScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    title = "Kehadiran Santri",
                    value = "${santriStats.pct}%",
                    subtitle = "(${santriStats.hadir}/${santriStats.total} sesi) • I:${santriStats.izin} S:${santriStats.sakit} A:${santriStats.alpha}",
                    iconEmoji = "🎓",
                    valueColor = Emerald900,
                    iconBg = Emerald100,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Kehadiran Mu'allim",
                    value = "${muallimStats.pct}%",
                    subtitle = "(${muallimStats.hadir}/${muallimStats.total} sesi) • ${db.muallim.size} Mu'allim Aktif",
                    iconEmoji = "📖",
                    valueColor = Amber900,
                    iconBg = Amber100,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Dewan Harian MMQA",
                    value = "${dewanStats.pct}%",
                    subtitle = "(${dewanStats.hadir}/${dewanStats.total} sesi) • ${db.dewanHarian.size} Pengurus",
                    iconEmoji = "👑",
                    valueColor = Sky900,
                    iconBg = Sky100,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Saldo Kas Majelis",
                    value = MmqaConstants.formatRupiah(saldoKas),
                    subtitle = "Masuk: ${MmqaConstants.formatRupiah(pemasukan)}",
                    iconEmoji = "💰",
                    highlightGradient = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMetricCard(
                        title = "Kehadiran Santri",
                        value = "${santriStats.pct}%",
                        subtitle = "${santriStats.hadir}/${santriStats.total} • I:${santriStats.izin} S:${santriStats.sakit} A:${santriStats.alpha}",
                        iconEmoji = "🎓",
                        valueColor = Emerald900,
                        iconBg = Emerald100,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Kehadiran Mu'allim",
                        value = "${muallimStats.pct}%",
                        subtitle = "${muallimStats.hadir}/${muallimStats.total} • ${db.muallim.size} Mu'allim",
                        iconEmoji = "📖",
                        valueColor = Amber900,
                        iconBg = Amber100,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatMetricCard(
                        title = "Dewan Harian",
                        value = "${dewanStats.pct}%",
                        subtitle = "${dewanStats.hadir}/${dewanStats.total} • ${db.dewanHarian.size} Pengurus",
                        iconEmoji = "👑",
                        valueColor = Sky900,
                        iconBg = Sky100,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        title = "Saldo Kas Majelis",
                        value = MmqaConstants.formatRupiah(saldoKas),
                        subtitle = "Masuk: ${MmqaConstants.formatRupiah(pemasukan)}",
                        iconEmoji = "💰",
                        highlightGradient = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Grafik Progress Kehadiran Santri Per Firqoh
        SectionCard {
            Text(
                text = "📊 Persentase Kehadiran Santri Per Firqoh",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Slate900
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                db.firqoh.chunked(if (isWideScreen) 2 else 1).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { f ->
                            val st = viewModel.calcAttendanceStats("santri", null, f.id)
                            val countSantri = db.santri.count { it.firqohId == f.id }
                            val (bg, txt, border) = pctBadgeColors(st.pct)
                            Surface(
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = f.nama,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Slate900,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        StatusPill("${st.pct}%", bg, txt, border)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { (st.pct / 100f).coerceIn(0f, 1f) },
                                        color = Emerald700,
                                        trackColor = Slate200,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$countSantri Santri • Hadir ${st.hadir}/${st.total} catatan",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }
                        if (isWideScreen && rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Rekapan Persentase Kehadiran Setiap Santri
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎓 Rekapan Persentase Kehadiran Setiap Santri",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                    Text(
                        text = "Akumulasi seluruh sesi presensi santri berdasarkan Data Induk",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val firqohOptions = listOf("ALL" to "Semua Firqoh (${db.santri.size} Santri)") +
                db.firqoh.map { it.id to it.nama }
            CompactDropdownSelector(
                label = "",
                selectedText = firqohOptions.firstOrNull { it.first == uiState.dashFirqohFilter }?.second ?: "Semua Firqoh",
                options = firqohOptions,
                onSelect = { selected -> viewModel.updateState { it.copy(dashFirqohFilter = selected) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredSantri.forEach { s ->
                    val st = viewModel.calcAttendanceStats("santri", s.id)
                    val (bg, txt, border) = pctBadgeColors(st.pct)
                    Surface(
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = s.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text(
                                    text = "NIS ${s.nis} • ${viewModel.getFirqohName(s.firqohId)}",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "H:${st.hadir}  I:${st.izin}  S:${st.sakit}  A:${st.alpha}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill("${st.pct}%", bg, txt, border)
                        }
                    }
                }
            }
        }

        // Rekapan Mu'allim & Dewan Harian
        val muallimCard: @Composable () -> Unit = {
            SectionCard {
                Text(
                    text = "📖 Persentase Kehadiran Setiap Mu'allim",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate900
                )
                Text(
                    text = "Rekapan presensi mengajar Mu'allim perfirqoh",
                    fontSize = 11.sp,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    db.muallim.forEach { m ->
                        val st = viewModel.calcAttendanceStats("muallim", m.id)
                        val (bg, txt, border) = pctBadgeColors(st.pct)
                        Surface(
                            color = Amber50.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Amber200.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = m.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    Text(
                                        text = "NIY: ${m.niy} • ${viewModel.getFirqohName(m.firqohId)}",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "H/I/S/A: ${st.hadir}/${st.izin}/${st.sakit}/${st.alpha}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700
                                    )
                                }
                                StatusPill("${st.pct}%", bg, txt, border)
                            }
                        }
                    }
                }
            }
        }

        val dewanCard: @Composable () -> Unit = {
            SectionCard {
                Text(
                    text = "👑 Persentase Kehadiran Dewan Harian MMQA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate900
                )
                Text(
                    text = "Rekapan piket & kehadiran pengurus Dewan Harian",
                    fontSize = 11.sp,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    db.dewanHarian.forEach { d ->
                        val st = viewModel.calcAttendanceStats("dewan_harian", d.id)
                        val (bg, txt, border) = pctBadgeColors(st.pct)
                        Surface(
                            color = Sky100.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, Sky100),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = d.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    Text(
                                        text = "Kode: ${d.kode} • ${d.jabatan}",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "H/I/S/A: ${st.hadir}/${st.izin}/${st.sakit}/${st.alpha}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700
                                    )
                                }
                                StatusPill("${st.pct}%", bg, txt, border)
                            }
                        }
                    }
                }
            }
        }

        if (isWideScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) { muallimCard() }
                Box(modifier = Modifier.weight(1f)) { dewanCard() }
            }
        } else {
            muallimCard()
            dewanCard()
        }
    }
}
