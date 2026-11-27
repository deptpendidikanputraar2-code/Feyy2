package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttendancePeriodHelper
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
    val context = LocalContext.current
    var showWhatsappControllingDialog by remember { mutableStateOf(false) }

    val activePeriod = uiState.dashPeriodeFilter
    val activeRefDate = uiState.dashPeriodeRefDate
    val periodLabel = remember(activePeriod, activeRefDate) {
        AttendancePeriodHelper.getPeriodRangeLabel(activePeriod, activeRefDate)
    }

    val isWaliUser = uiState.currentUser?.role == "wali_santri"
    val linkedSantriId = if (isWaliUser) uiState.currentUser?.santriId.orEmpty().takeIf { it.isNotBlank() } else null

    val canViewSantriRekap = viewModel.canAccessAnakSub("dashboard_santri_rekap_view")
    val canViewMuallimRekap = viewModel.canAccessAnakSub("dashboard_muallim_rekap_view")
    val canViewDewanRekap = viewModel.canAccessAnakSub("dashboard_dewan_rekap_view")
    val canViewBermasalahDash = viewModel.canAccessAnakSub("dashboard_bermasalah_view")
    val canViewWaControllingDash = viewModel.canAccessAnakSub("dashboard_wa_controlling") && !isWaliUser
    val canViewKeuanganDash = viewModel.canAccessAnakSub("dashboard_keuangan_view")

    val santriStats = viewModel.calcAttendanceStatsByPeriod(
        tipe = "santri",
        targetId = linkedSantriId,
        firqohId = if (linkedSantriId != null) null else uiState.dashFirqohFilter,
        periodFilter = activePeriod,
        referenceDate = activeRefDate
    )
    val santriLateTotal = viewModel.countLateAttendance(
        tipe = "santri",
        targetId = linkedSantriId,
        firqohId = if (linkedSantriId != null) null else uiState.dashFirqohFilter,
        periodFilter = activePeriod,
        referenceDate = activeRefDate
    )
    val muallimStats = viewModel.calcAttendanceStatsByPeriod("muallim", null, null, activePeriod, activeRefDate)
    val muallimLateTotal = viewModel.countLateAttendance("muallim", null, null, activePeriod, activeRefDate)
    val dewanStats = viewModel.calcAttendanceStatsByPeriod("dewan_harian", null, null, activePeriod, activeRefDate)
    val dewanLateTotal = viewModel.countLateAttendance("dewan_harian", null, null, activePeriod, activeRefDate)

    val pemasukan = db.keuangan.filter { it.jenis == "pemasukan" }.sumOf { it.nominal }
    val pengeluaran = db.keuangan.filter { it.jenis == "pengeluaran" }.sumOf { it.nominal }
    val saldoKas = pemasukan - pengeluaran

    val activeJuknisRule = remember(db.juknisDocs) { viewModel.getActiveJuknisAcuan() }
    val problematicSantri = remember(db.santri, db.absensi, db.firqoh, db.juknisDocs, uiState.dashFirqohFilter, activePeriod, activeRefDate, linkedSantriId) {
        val raw = viewModel.getProblematicSantri(uiState.dashFirqohFilter, activePeriod, activeRefDate)
        if (linkedSantriId != null) raw.filter { it.santri.id == linkedSantriId } else raw
    }

    val filteredSantri = if (linkedSantriId != null) {
        db.santri.filter { it.id == linkedSantriId }
    } else {
        db.santri.filter { viewModel.matchesFirqohFilter(it.firqohId, uiState.dashFirqohFilter) }
    }
    val dashFilterLabel = if (linkedSantriId != null) {
        filteredSantri.firstOrNull()?.nama ?: "Santri Saya"
    } else {
        viewModel.getFirqohFilterDisplayLabel(uiState.dashFirqohFilter)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Filter Periode Rekap & Panel Controlling WhatsApp
        SectionCard(
            containerColor = Color.White,
            borderColor = Emerald100
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canViewWaControllingDash) "📅 Filter Periode Rekap & Kontrol WA" else "📅 Filter Periode Rekap Kehadiran",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Emerald950
                    )
                    Text(
                        text = if (canViewWaControllingDash) {
                            "Filter rekap Harian, Mingguan, Bulanan, Persemester, atau Pertahun untuk controlling Dewan Harian, Orang Tua & Mu'allim."
                        } else {
                            "Filter rekap kehadiran Harian, Mingguan, Bulanan, Persemester, atau Pertahun."
                        },
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
                if (canViewWaControllingDash) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showWhatsappControllingDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("dash_open_wa_controlling_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kontrol WA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            PeriodFilterBar(
                selectedPeriod = activePeriod,
                referenceDate = activeRefDate,
                onSelectPeriod = { p -> viewModel.updateState { it.copy(dashPeriodeFilter = p) } },
                onShiftDate = { delta ->
                    viewModel.updateState {
                        it.copy(
                            dashPeriodeRefDate = AttendancePeriodHelper.shiftReferenceDate(
                                it.dashPeriodeFilter,
                                it.dashPeriodeRefDate,
                                delta
                            )
                        )
                    }
                },
                onResetToday = { viewModel.updateState { it.copy(dashPeriodeRefDate = MmqaConstants.todayStr()) } }
            )
        }

        // Kartu Metrik Utama (Dinamis sesuai hak akses rekapan dari Admin)
        val kpiCards = buildList<@Composable (Modifier) -> Unit> {
            if (canViewSantriRekap) {
                add { mod ->
                    StatMetricCard(
                        title = "Kehadiran Santri",
                        value = "${santriStats.pct}%",
                        subtitle = "(${santriStats.hadir}/${santriStats.total} sesi) • Telat:$santriLateTotal • I:${santriStats.izin} S:${santriStats.sakit} A:${santriStats.alpha}",
                        iconEmoji = "🎓",
                        valueColor = Emerald900,
                        iconBg = Emerald100,
                        modifier = mod
                    )
                }
            }
            if (canViewMuallimRekap) {
                add { mod ->
                    StatMetricCard(
                        title = "Kehadiran Mu'allim",
                        value = "${muallimStats.pct}%",
                        subtitle = "(${muallimStats.hadir}/${muallimStats.total} sesi) • Telat:$muallimLateTotal • ${db.muallim.size} Mu'allim",
                        iconEmoji = "📖",
                        valueColor = Amber900,
                        iconBg = Amber100,
                        modifier = mod
                    )
                }
            }
            if (canViewDewanRekap) {
                add { mod ->
                    StatMetricCard(
                        title = "Dewan Harian MMQA",
                        value = "${dewanStats.pct}%",
                        subtitle = "(${dewanStats.hadir}/${dewanStats.total} sesi) • Telat:$dewanLateTotal • ${db.dewanHarian.size} Pengurus",
                        iconEmoji = "👑",
                        valueColor = Sky900,
                        iconBg = Sky100,
                        modifier = mod
                    )
                }
            }
            if (canViewKeuanganDash) {
                add { mod ->
                    StatMetricCard(
                        title = "Saldo Kas Majelis",
                        value = MmqaConstants.formatRupiah(saldoKas),
                        subtitle = "Masuk: ${MmqaConstants.formatRupiah(pemasukan)}",
                        iconEmoji = "💰",
                        highlightGradient = true,
                        modifier = mod
                    )
                }
            } else if (canViewBermasalahDash) {
                add { mod ->
                    StatMetricCard(
                        title = "Perlu Pembinaan",
                        value = "${problematicSantri.size} Santri",
                        subtitle = "Batas Juknis: A≥${activeJuknisRule.batasAlphaSp1} / I≥${activeJuknisRule.batasIzinPeringatan}",
                        iconEmoji = "⚠️",
                        valueColor = if (problematicSantri.isNotEmpty()) Rose700 else Emerald900,
                        iconBg = if (problematicSantri.isNotEmpty()) Rose100 else Emerald100,
                        modifier = mod
                    )
                }
            }
        }

        if (kpiCards.isNotEmpty()) {
            val cols = if (isWideScreen) kpiCards.size.coerceAtMost(4) else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                kpiCards.chunked(cols).forEach { rowCards ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowCards.forEach { card ->
                            card(Modifier.weight(1f))
                        }
                        repeat(cols - rowCards.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Rekap Keuangan Dashboard (Dikelola Aksesnya oleh Admin)
        if (canViewKeuanganDash) {
            val lunasCount = db.santri.count { it.statusSyahriah == "Lunas" }
            SectionCard(
                containerColor = Emerald50.copy(alpha = 0.55f),
                borderColor = Emerald100
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💰 Rekap Keuangan Majelis (Dashboard)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Emerald950
                        )
                        Text(
                            text = "Syahriah Lunas: $lunasCount/${db.santri.size} Santri • Keluar: ${MmqaConstants.formatRupiah(pengeluaran)}",
                            fontSize = 11.sp,
                            color = Emerald800
                        )
                    }
                    StatusPill("Akses Admin", Emerald100, Emerald900)
                }
                Spacer(modifier = Modifier.height(10.dp))
                WaPdfActionRow(
                    onShareWa = {
                        val msg = MmqaReportFormatters.buildDashboardKeuanganWa(db)
                        shareTextToWhatsApp(context, msg, "Kirim Rekap Keuangan")
                    },
                    onDownloadPdf = {
                        val html = MmqaReportFormatters.buildDashboardKeuanganPdfHtml(db)
                        printHtmlDocument(context, "Rekap_Keuangan_MMQA", html)
                    },
                    waTestTag = "dash_keuangan_wa_btn",
                    pdfTestTag = "dash_keuangan_pdf_btn"
                )
            }
        }

        // Rekap Santri Bermasalah (Alpha & Izin Sinkron Otomatis dengan Juknis Aktif)
        if (canViewBermasalahDash) {
        SectionCard(
            containerColor = if (problematicSantri.isNotEmpty()) Rose50.copy(alpha = 0.5f) else Color.White,
            borderColor = if (problematicSantri.isNotEmpty()) Rose200 else Slate200
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚠️ Rekap Santri Bermasalah (Sinkron Juknis)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                    Text(
                        text = "Acuan: ${activeJuknisRule.judul} • $periodLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                StatusPill(
                    text = "Lihat Juknis →",
                    bgColor = Amber100,
                    textColor = Amber950,
                    borderColor = Amber300,
                    modifier = Modifier.clickable { viewModel.selectMenu("juknis") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Amber200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚖️ Batas Aktif: Alpha SP1 ≥ ${activeJuknisRule.batasAlphaSp1}x • SP2 ≥ ${activeJuknisRule.batasAlphaSp2}x • Berat ≥ ${activeJuknisRule.batasAlphaSp3}x • Izin ≥ ${activeJuknisRule.batasIzinPeringatan}x",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Amber950,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            WaPdfActionRow(
                onShareWa = {
                    val msg = MmqaReportFormatters.buildProblematicSantriWa(dashFilterLabel, activeJuknisRule, problematicSantri)
                    shareTextToWhatsApp(context, msg, "Kirim Rekap Santri Bermasalah")
                },
                onDownloadPdf = {
                    val html = MmqaReportFormatters.buildProblematicSantriPdfHtml(dashFilterLabel, activeJuknisRule, problematicSantri)
                    printHtmlDocument(context, "Rekap_Santri_Bermasalah_MMQA", html)
                },
                waTestTag = "dash_bermasalah_wa_btn",
                pdfTestTag = "dash_bermasalah_pdf_btn"
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (problematicSantri.isEmpty()) {
                Surface(
                    color = Emerald50,
                    border = BorderStroke(1.dp, Emerald100),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✅ Alhamdulillah, seluruh santri pada filter \"$dashFilterLabel\" ($periodLabel) berada dalam batas disiplin kehadiran sesuai Juknis aktif.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald900,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    problematicSantri.forEach { res ->
                        val (lvlBg, lvlTxt, lvlBorder) = when (res.severityLevel) {
                            3 -> Triple(Rose600, Color.White, Rose700)
                            2 -> Triple(Rose100, Rose800, Rose200)
                            else -> Triple(Amber100, Amber950, Amber300)
                        }
                        Surface(
                            color = Color.White,
                            border = BorderStroke(1.dp, lvlBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = res.santri.nama,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "NIS ${res.santri.nis} • ${res.firqohName}",
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPill(res.levelLabel, lvlBg, lvlTxt)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill("Alpha: ${res.alphaCount}x", Rose100, Rose800)
                                    StatusPill("Izin: ${res.izinCount}x", Amber100, Amber900)
                                    StatusPill("Hadir: ${res.hadirCount}x", Emerald100, Emerald900)
                                }
                                Surface(
                                    color = Slate50,
                                    border = BorderStroke(1.dp, Slate200),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Konsekuensi Sesuai Juknis Aktif:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Rose800
                                        )
                                        res.consequences.forEach { cons ->
                                            Text(
                                                text = "• $cons",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Slate900
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
        }

        // Grafik Progress Kehadiran Santri Per Firqoh
        if (canViewSantriRekap && linkedSantriId == null) {
        SectionCard {
            Text(
                text = "📊 Persentase Kehadiran Santri Per Firqoh",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Slate900
            )
            Text(
                text = "Periode: $periodLabel",
                fontSize = 11.sp,
                color = Emerald800,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            WaPdfActionRow(
                onShareWa = {
                    val msg = MmqaReportFormatters.buildDashboardFirqohWa(db) { f ->
                        viewModel.calcAttendanceStatsByPeriod("santri", null, f.id, activePeriod, activeRefDate)
                    }
                    shareTextToWhatsApp(context, msg, "Kirim Rekapan Per Firqoh")
                },
                onDownloadPdf = {
                    val html = MmqaReportFormatters.buildDashboardFirqohPdfHtml(db) { f ->
                        viewModel.calcAttendanceStatsByPeriod("santri", null, f.id, activePeriod, activeRefDate)
                    }
                    printHtmlDocument(context, "Rekapan_Kehadiran_Per_Firqoh", html)
                },
                waTestTag = "dash_firqoh_wa_btn",
                pdfTestTag = "dash_firqoh_pdf_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                db.firqoh.chunked(if (isWideScreen) 2 else 1).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { f ->
                            val st = viewModel.calcAttendanceStatsByPeriod("santri", null, f.id, activePeriod, activeRefDate)
                            val fLate = viewModel.countLateAttendance("santri", null, f.id, activePeriod, activeRefDate)
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
                                        text = "$countSantri Santri • Hadir ${st.hadir}/${st.total} catatan" +
                                            if (fLate > 0) " • ⏰ Telat $fLate" else "",
                                        fontSize = 11.sp,
                                        color = if (fLate > 0) Rose800 else Slate500,
                                        fontWeight = if (fLate > 0) FontWeight.Bold else FontWeight.Normal
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
        }

        // Rekapan Persentase Kehadiran Setiap Santri
        if (canViewSantriRekap) {
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (linkedSantriId != null) "🎓 Rekapan Kehadiran Santri Anda" else "🎓 Rekapan Persentase Kehadiran Setiap Santri",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900
                    )
                    Text(
                        text = "Periode: $periodLabel • Filter: $dashFilterLabel",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }
            if (linkedSantriId == null) {
                Spacer(modifier = Modifier.height(10.dp))
                val firqohOptions = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = true)
                CompactDropdownSelector(
                    label = "",
                    selectedText = firqohOptions.firstOrNull { it.first == uiState.dashFirqohFilter }?.second ?: dashFilterLabel,
                    options = firqohOptions,
                    onSelect = { selected -> viewModel.updateState { it.copy(dashFirqohFilter = selected) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            WaPdfActionRow(
                onShareWa = {
                    val msg = MmqaReportFormatters.buildDashboardSantriWa(
                        db = db,
                        filterLabel = "$dashFilterLabel ($periodLabel)",
                        santriList = filteredSantri
                    ) { s -> viewModel.calcAttendanceStatsByPeriod("santri", s.id, null, activePeriod, activeRefDate) }
                    shareTextToWhatsApp(context, msg, "Kirim Rekapan Santri")
                },
                onDownloadPdf = {
                    val html = MmqaReportFormatters.buildDashboardSantriPdfHtml(
                        db = db,
                        filterLabel = "$dashFilterLabel ($periodLabel)",
                        santriList = filteredSantri
                    ) { s -> viewModel.calcAttendanceStatsByPeriod("santri", s.id, null, activePeriod, activeRefDate) }
                    printHtmlDocument(context, "Rekapan_Kehadiran_Santri", html)
                },
                waTestTag = "dash_santri_wa_btn",
                pdfTestTag = "dash_santri_pdf_btn"
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredSantri.forEach { s ->
                    val st = viewModel.calcAttendanceStatsByPeriod("santri", s.id, null, activePeriod, activeRefDate)
                    val sLate = viewModel.countLateAttendance("santri", s.id, null, activePeriod, activeRefDate)
                    val (bg, txt, border) = pctBadgeColors(st.pct)
                    Surface(
                        color = if (sLate > 0) Rose50.copy(alpha = 0.55f) else Slate50,
                        border = BorderStroke(1.dp, if (sLate > 0) Rose200 else Slate200),
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
                                    text = "H:${st.hadir}  I:${st.izin}  S:${st.sakit}  A:${st.alpha}" +
                                        if (sLate > 0) "  ⏰Telat:${sLate}x" else "",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sLate > 0) Rose800 else Slate700
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatusPill("${st.pct}%", bg, txt, border)
                                if (canViewWaControllingDash && s.hpWali.isNotBlank()) {
                                    Text(
                                        text = "📲 WA Wali",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Emerald800,
                                        modifier = Modifier.clickable {
                                            val recent = db.absensi.filter {
                                                it.tipe == "santri" && it.targetId == s.id &&
                                                    AttendancePeriodHelper.matchesPeriod(it.tanggal, activePeriod, activeRefDate)
                                            }.sortedByDescending { it.tanggal }
                                            val msg = MmqaReportFormatters.buildWaliControllingWa(
                                                db = db,
                                                santri = s,
                                                stats = st,
                                                lateCount = sLate,
                                                periodLabel = periodLabel,
                                                recentLogs = recent
                                            )
                                            openWhatsAppDirect(context, s.hpWali, msg, "Kirim Rekap ke Wali ${s.nama}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Rekapan Mu'allim & Dewan Harian (Dikelola Aksesnya oleh Admin)
        val muallimCard: @Composable () -> Unit = {
            SectionCard {
                Text(
                    text = "📖 Persentase Kehadiran Setiap Mu'allim",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate900
                )
                Text(
                    text = "Rekapan presensi mengajar Mu'allim • $periodLabel",
                    fontSize = 11.sp,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(10.dp))
                WaPdfActionRow(
                    onShareWa = {
                        val msg = MmqaReportFormatters.buildDashboardMuallimWa(db) { m ->
                            viewModel.calcAttendanceStatsByPeriod("muallim", m.id, null, activePeriod, activeRefDate)
                        }
                        shareTextToWhatsApp(context, msg, "Kirim Rekapan Mu'allim")
                    },
                    onDownloadPdf = {
                        val html = MmqaReportFormatters.buildDashboardMuallimPdfHtml(db) { m ->
                            viewModel.calcAttendanceStatsByPeriod("muallim", m.id, null, activePeriod, activeRefDate)
                        }
                        printHtmlDocument(context, "Rekapan_Kehadiran_Muallim", html)
                    },
                    waTestTag = "dash_muallim_wa_btn",
                    pdfTestTag = "dash_muallim_pdf_btn"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    db.muallim.forEach { m ->
                        val st = viewModel.calcAttendanceStatsByPeriod("muallim", m.id, null, activePeriod, activeRefDate)
                        val mLate = viewModel.countLateAttendance("muallim", m.id, null, activePeriod, activeRefDate)
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
                                        text = "H/I/S/A: ${st.hadir}/${st.izin}/${st.sakit}/${st.alpha}" +
                                            if (mLate > 0) " • ⏰Telat:${mLate}x" else "",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mLate > 0) Rose800 else Slate700
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
                    text = "Rekapan piket & kehadiran Dewan Harian • $periodLabel",
                    fontSize = 11.sp,
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(10.dp))
                WaPdfActionRow(
                    onShareWa = {
                        val msg = MmqaReportFormatters.buildDashboardDewanWa(db) { d ->
                            viewModel.calcAttendanceStatsByPeriod("dewan_harian", d.id, null, activePeriod, activeRefDate)
                        }
                        shareTextToWhatsApp(context, msg, "Kirim Rekapan Dewan Harian")
                    },
                    onDownloadPdf = {
                        val html = MmqaReportFormatters.buildDashboardDewanPdfHtml(db) { d ->
                            viewModel.calcAttendanceStatsByPeriod("dewan_harian", d.id, null, activePeriod, activeRefDate)
                        }
                        printHtmlDocument(context, "Rekapan_Kehadiran_Dewan_Harian", html)
                    },
                    waTestTag = "dash_dewan_wa_btn",
                    pdfTestTag = "dash_dewan_pdf_btn"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    db.dewanHarian.forEach { d ->
                        val st = viewModel.calcAttendanceStatsByPeriod("dewan_harian", d.id, null, activePeriod, activeRefDate)
                        val dLate = viewModel.countLateAttendance("dewan_harian", d.id, null, activePeriod, activeRefDate)
                        val (bg, txt, border) = pctBadgeColors(st.pct)
                        Surface(
                            color = if (dLate > 0) Rose50.copy(alpha = 0.55f) else Sky100.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (dLate > 0) Rose200 else Sky100),
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
                                        text = "H/I/S/A: ${st.hadir}/${st.izin}/${st.sakit}/${st.alpha}" +
                                            if (dLate > 0) " • ⏰Telat:${dLate}x" else "",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dLate > 0) Rose800 else Slate700
                                    )
                                }
                                StatusPill("${st.pct}%", bg, txt, border)
                            }
                        }
                    }
                }
            }
        }

        if (canViewMuallimRekap && canViewDewanRekap && isWideScreen) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) { muallimCard() }
                Box(modifier = Modifier.weight(1f)) { dewanCard() }
            }
        } else {
            if (canViewMuallimRekap) muallimCard()
            if (canViewDewanRekap) dewanCard()
        }

        if (!canViewSantriRekap && !canViewMuallimRekap && !canViewDewanRekap && !canViewBermasalahDash && !canViewKeuanganDash) {
            SectionCard {
                Text(
                    text = "🔒 Tampilan rekapan Dashboard sedang dibatasi oleh Admin Dewan Harian.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
            }
        }
    }

    if (showWhatsappControllingDialog) {
        WhatsappControllingDialog(
            db = db,
            viewModel = viewModel,
            periodFilter = activePeriod,
            referenceDate = activeRefDate,
            firqohFilter = uiState.dashFirqohFilter,
            onDismiss = { showWhatsappControllingDialog = false }
        )
    }
}
