package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import com.example.data.*
import com.example.ui.MmqaViewModel
import com.example.ui.theme.*

private data class ControllingRecipientUi(
    val id: String,
    val nama: String,
    val subtitle: String,
    val phone: String,
    val statsBadge: String,
    val lateBadge: String,
    val isFlagged: Boolean,
    val message: String
)

@Composable
fun WhatsappControllingDialog(
    db: MmqaDatabase,
    viewModel: MmqaViewModel,
    periodFilter: String,
    referenceDate: String,
    firqohFilter: String = "ALL",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("wali") } // "dewan" | "muallim" | "wali"
    var onlyFlagged by remember { mutableStateOf(false) }
    var sentIds by remember { mutableStateOf(setOf<String>()) }

    val periodLabel = remember(periodFilter, referenceDate) {
        AttendancePeriodHelper.getPeriodRangeLabel(periodFilter, referenceDate)
    }

    val santriAllStats = remember(db.absensi, periodFilter, referenceDate, firqohFilter) {
        viewModel.calcAttendanceStatsByPeriod("santri", null, firqohFilter, periodFilter, referenceDate)
    }
    val santriAllLate = remember(db.absensi, periodFilter, referenceDate, firqohFilter) {
        viewModel.countLateAttendance("santri", null, firqohFilter, periodFilter, referenceDate)
    }
    val muallimAllStats = remember(db.absensi, periodFilter, referenceDate) {
        viewModel.calcAttendanceStatsByPeriod("muallim", null, null, periodFilter, referenceDate)
    }
    val muallimAllLate = remember(db.absensi, periodFilter, referenceDate) {
        viewModel.countLateAttendance("muallim", null, null, periodFilter, referenceDate)
    }
    val dewanAllStats = remember(db.absensi, periodFilter, referenceDate) {
        viewModel.calcAttendanceStatsByPeriod("dewan_harian", null, null, periodFilter, referenceDate)
    }
    val dewanAllLate = remember(db.absensi, periodFilter, referenceDate) {
        viewModel.countLateAttendance("dewan_harian", null, null, periodFilter, referenceDate)
    }
    val flaggedViolations = remember(db.santri, db.absensi, db.juknisDocs, firqohFilter, periodFilter, referenceDate) {
        viewModel.getProblematicSantri(firqohFilter, periodFilter, referenceDate)
    }

    val dewanRecipients = remember(db.dewanHarian, db.absensi, periodFilter, referenceDate, firqohFilter) {
        db.dewanHarian.map { d ->
            val msg = MmqaReportFormatters.buildDewanControllingWa(
                dewan = d,
                periodLabel = periodLabel,
                santriStats = santriAllStats,
                santriLate = santriAllLate,
                muallimStats = muallimAllStats,
                dewanStats = dewanAllStats,
                flaggedSantri = flaggedViolations,
                muallimLate = muallimAllLate,
                dewanLate = dewanAllLate
            )
            val totalPengurusLate = santriAllLate + muallimAllLate + dewanAllLate
            ControllingRecipientUi(
                id = "dewan_${d.id}",
                nama = d.nama,
                subtitle = "${d.jabatan} • HP: ${d.hp.ifBlank { "-" }}",
                phone = d.hp,
                statsBadge = "Santri ${santriAllStats.pct}%",
                lateBadge = if (totalPengurusLate > 0) "⏰ Telat S:$santriAllLate M:$muallimAllLate D:$dewanAllLate" else "Tertib",
                isFlagged = flaggedViolations.isNotEmpty() || totalPengurusLate > 0,
                message = msg
            )
        }
    }

    val muallimRecipients = remember(db.muallim, db.santri, db.absensi, periodFilter, referenceDate) {
        db.muallim.map { m ->
            val mStats = viewModel.calcAttendanceStatsByPeriod("muallim", m.id, null, periodFilter, referenceDate)
            val fStats = viewModel.calcAttendanceStatsByPeriod("santri", null, m.firqohId, periodFilter, referenceDate)
            val fLate = viewModel.countLateAttendance("santri", null, m.firqohId, periodFilter, referenceDate)
            val flaggedInFirqoh = db.santri
                .filter { it.firqohId == m.firqohId }
                .mapNotNull { s ->
                    val st = viewModel.calcAttendanceStatsByPeriod("santri", s.id, null, periodFilter, referenceDate)
                    val lCnt = viewModel.countLateAttendance("santri", s.id, null, periodFilter, referenceDate)
                    if (st.alpha > 0 || lCnt > 0) Triple(s, st, lCnt) else null
                }
            val msg = MmqaReportFormatters.buildMuallimControllingWa(
                db = db,
                muallim = m,
                firqohStats = fStats,
                lateSantriCount = fLate,
                muallimStats = mStats,
                periodLabel = periodLabel,
                flaggedSantri = flaggedInFirqoh
            )
            ControllingRecipientUi(
                id = "muallim_${m.id}",
                nama = m.nama,
                subtitle = "${viewModel.getFirqohName(m.firqohId)} • HP: ${m.hp.ifBlank { "-" }}",
                phone = m.hp,
                statsBadge = "Firqoh ${fStats.pct}%",
                lateBadge = if (fLate > 0) "⏰ $fLate Telat" else "Tertib",
                isFlagged = fLate > 0 || fStats.alpha > 0,
                message = msg
            )
        }
    }

    val waliRecipients = remember(db.santri, db.absensi, periodFilter, referenceDate, firqohFilter) {
        db.santri
            .filter { viewModel.matchesFirqohFilter(it.firqohId, firqohFilter) }
            .map { s ->
                val st = viewModel.calcAttendanceStatsByPeriod("santri", s.id, null, periodFilter, referenceDate)
                val lCnt = viewModel.countLateAttendance("santri", s.id, null, periodFilter, referenceDate)
                val recentLogs = db.absensi
                    .filter {
                        it.tipe == "santri" &&
                            it.targetId == s.id &&
                            AttendancePeriodHelper.matchesPeriod(it.tanggal, periodFilter, referenceDate)
                    }
                    .sortedWith(compareByDescending<AbsensiRecord> { it.tanggal }.thenByDescending { it.waktuScan })
                val msg = MmqaReportFormatters.buildWaliControllingWa(
                    db = db,
                    santri = s,
                    stats = st,
                    lateCount = lCnt,
                    periodLabel = periodLabel,
                    recentLogs = recentLogs
                )
                ControllingRecipientUi(
                    id = "wali_${s.id}",
                    nama = "${s.nama} (${s.wali.ifBlank { "Wali" }})",
                    subtitle = "${viewModel.getFirqohName(s.firqohId)} • WA Wali: ${s.hpWali.ifBlank { "-" }}",
                    phone = s.hpWali,
                    statsBadge = "${st.pct}% (H:${st.hadir} A:${st.alpha})",
                    lateBadge = if (lCnt > 0) "⏰ Telat ${lCnt}x" else "✅ Tepat Waktu",
                    isFlagged = lCnt > 0 || st.alpha > 0,
                    message = msg
                )
            }
    }

    val currentList = when (activeTab) {
        "dewan" -> dewanRecipients
        "muallim" -> muallimRecipients
        else -> waliRecipients
    }.let { list -> if (onlyFlagged) list.filter { it.isFlagged } else list }

    val nextUnsent = currentList.firstOrNull {
        it.id !in sentIds && MmqaConstants.normalizeIndoPhoneForWa(it.phone).isNotBlank()
    }
    val sentCountInTab = currentList.count { it.id in sentIds }

    MmqaModalDialog(title = "📲 Kontrol Rekap Otomatis ke WhatsApp", onDismiss = onDismiss) {
        Surface(
            color = Emerald50,
            border = BorderStroke(1.dp, Emerald100),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "📅 Periode Rekap: $periodLabel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Emerald900
                )
                Text(
                    text = "Kirim laporan kehadiran & keterlambatan langsung ke nomor WhatsApp Dewan Harian, Mu'allim, atau Wali Santri.",
                    fontSize = 10.sp,
                    color = Slate700
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                "wali" to "👨‍👩‍👦 Wali (${waliRecipients.size})",
                "muallim" to "📖 Mu'allim (${muallimRecipients.size})",
                "dewan" to "👑 Dewan (${dewanRecipients.size})"
            ).forEach { (id, lbl) ->
                val active = activeTab == id
                Surface(
                    color = if (active) Emerald900 else Slate100,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clickable { activeTab = id }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) Amber300 else Slate700,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Checkbox(
                    checked = onlyFlagged,
                    onCheckedChange = { onlyFlagged = it },
                    colors = CheckboxDefaults.colors(checkedColor = Rose700)
                )
                Text(
                    text = "Hanya yang Telat / Alpha",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )
            }
            StatusPill(
                text = "Terkirim: $sentCountInTab/${currentList.size}",
                bgColor = Emerald100,
                textColor = Emerald900
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sequential One-Tap Auto Dispatcher + Group Share
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val target = nextUnsent ?: currentList.firstOrNull {
                        MmqaConstants.normalizeIndoPhoneForWa(it.phone).isNotBlank()
                    }
                    if (target != null) {
                        sentIds = sentIds + target.id
                        openWhatsAppDirect(context, target.phone, target.message, "Kirim WA ke ${target.nama}")
                    } else {
                        viewModel.showToast("Tidak ada nomor WA valid pada daftar ini.", isError = true)
                    }
                },
                enabled = currentList.any { MmqaConstants.normalizeIndoPhoneForWa(it.phone).isNotBlank() },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1.4f)
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("wa_controlling_next_btn")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (nextUnsent != null) "Kirim Berikutnya (${sentCountInTab + 1}/${currentList.size})" else "Ulangi Kirim WA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    val logs = viewModel.getDetailedAbsensiLogs(
                        tipe = if (activeTab == "muallim") "muallim" else if (activeTab == "dewan") "dewan_harian" else "santri",
                        firqohFilter = firqohFilter,
                        periodFilter = periodFilter,
                        referenceDate = referenceDate,
                        onlyLate = onlyFlagged
                    )
                    val summaryMsg = MmqaReportFormatters.buildDetailedPeriodRecapWa(
                        periodLabel = periodLabel,
                        kategoriLabel = when (activeTab) {
                            "dewan" -> "Dewan Harian MMQA"
                            "muallim" -> "Mu'allim Firqoh"
                            else -> "Santri (${viewModel.getFirqohFilterDisplayLabel(firqohFilter)})"
                        },
                        stats = when (activeTab) {
                            "dewan" -> dewanAllStats
                            "muallim" -> muallimAllStats
                            else -> santriAllStats
                        },
                        lateCount = if (activeTab == "wali") santriAllLate else 0,
                        logs = logs
                    )
                    shareTextToWhatsApp(context, summaryMsg, "Bagikan ke Grup WhatsApp")
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.45f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("wa_controlling_group_btn")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Emerald900, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Grup WA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald900)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (currentList.isEmpty()) {
            Surface(
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tidak ada penerima pada filter saat ini.",
                    fontSize = 12.sp,
                    color = Slate500,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentList.forEach { rec ->
                    val isSent = rec.id in sentIds
                    val hasValidPhone = MmqaConstants.normalizeIndoPhoneForWa(rec.phone).isNotBlank()
                    Surface(
                        color = when {
                            isSent -> Emerald50.copy(alpha = 0.7f)
                            rec.isFlagged -> Rose50.copy(alpha = 0.55f)
                            else -> Slate50
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSent -> Emerald700.copy(alpha = 0.4f)
                                rec.isFlagged -> Rose200
                                else -> Slate200
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = rec.nama,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = rec.subtitle,
                                    fontSize = 10.sp,
                                    color = Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(rec.statsBadge, Emerald100, Emerald900)
                                    StatusPill(
                                        text = rec.lateBadge,
                                        bgColor = if (rec.isFlagged) Rose100 else Slate100,
                                        textColor = if (rec.isFlagged) Rose800 else Slate700
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    sentIds = sentIds + rec.id
                                    openWhatsAppDirect(context, rec.phone, rec.message, "Kirim WA ke ${rec.nama}")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSent) Emerald100 else if (hasValidPhone) Emerald700 else Slate300,
                                    contentColor = if (isSent) Emerald900 else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                            ) {
                                Text(
                                    text = if (isSent) "✓ Terkirim" else if (hasValidPhone) "Kirim WA" else "Bagikan",
                                    fontSize = 10.sp,
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
