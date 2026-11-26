package com.example.ui.components

import com.example.data.*
import com.example.ui.AttendanceStats

object MmqaReportFormatters {

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun firqohName(db: MmqaDatabase, firqohId: String): String =
        db.firqoh.firstOrNull { it.id == firqohId }?.nama ?: firqohId.ifBlank { "-" }

    private fun wrapReportHtml(
        title: String,
        subtitle: String,
        metaLine: String,
        tableHeaders: List<String>,
        rows: List<List<String>>,
        summaryBanner: String? = null
    ): String {
        val headerHtml = tableHeaders.joinToString("") { "<th>${esc(it)}</th>" }
        val bodyHtml = if (rows.isEmpty()) {
            "<tr><td colspan=\"${tableHeaders.size}\" style=\"text-align:center;color:#64748b;padding:16px;\">Belum ada data pada filter ini.</td></tr>"
        } else {
            rows.joinToString("\n") { row ->
                "<tr>" + row.joinToString("") { cell -> "<td>${esc(cell)}</td>" } + "</tr>"
            }
        }
        val summaryHtml = if (!summaryBanner.isNullOrBlank()) {
            "<div class=\"summary\">${esc(summaryBanner)}</div>"
        } else ""

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: sans-serif; color: #0f172a; padding: 20px; font-size: 10.5pt; line-height: 1.4; }
                .kop { text-align: center; border-bottom: 3px double #064e3b; padding-bottom: 10px; margin-bottom: 14px; }
                .kop .org { font-size: 9.5pt; font-weight: bold; color: #047857; letter-spacing: 0.6px; }
                .kop h1 { margin: 3px 0; font-size: 15pt; color: #0f172a; }
                .kop .sub { font-size: 9pt; color: #475569; }
                .doc-title { font-size: 13pt; font-weight: bold; color: #064e3b; margin-bottom: 2px; }
                .doc-meta { font-size: 9.5pt; color: #475569; margin-bottom: 12px; }
                .summary { background: #ecfdf5; border: 1px solid #a7f3d0; color: #064e3b; padding: 8px 12px; border-radius: 6px; font-weight: bold; font-size: 10pt; margin-bottom: 12px; }
                table { width: 100%; border-collapse: collapse; margin-top: 6px; }
                th { background: #064e3b; color: #ffffff; text-align: left; padding: 7px 8px; font-size: 9.5pt; border: 1px solid #064e3b; }
                td { padding: 6px 8px; border: 1px solid #cbd5e1; font-size: 9.5pt; vertical-align: top; }
                tr:nth-child(even) td { background: #f8fafc; }
                .footer { margin-top: 18px; font-size: 8.5pt; color: #64748b; text-align: right; }
              </style>
            </head>
            <body>
              <div class="kop">
                <div class="org">MAJELIS MUSYAWARAH QIRA'ATIL KUTUB &amp; AL-QUR'AN</div>
                <h1>MMQA AL RIFAI 02 (AR2)</h1>
                <div class="sub">${esc(subtitle)}</div>
              </div>
              <div class="doc-title">${esc(title)}</div>
              <div class="doc-meta">${esc(metaLine)} • Dicetak: ${esc(MmqaConstants.todayStr())} ${esc(MmqaConstants.nowTimeHm())}</div>
              $summaryHtml
              <table>
                <thead><tr>$headerHtml</tr></thead>
                <tbody>$bodyHtml</tbody>
              </table>
              <div class="footer">Dokumen resmi dihasilkan dari aplikasi MMQA Al Rifai 02</div>
            </body>
            </html>
        """.trimIndent()
    }

    // =========================================================================
    // 1. DASHBOARD REKAPAN
    // =========================================================================
    fun buildDashboardFirqohWa(db: MmqaDatabase, statsCalc: (FirqohItem) -> AttendanceStats): String = buildString {
        appendLine("📊 *REKAPAN KEHADIRAN SANTRI PER FIRQOH*")
        appendLine("_MMQA Al Rifai 02 • ${MmqaConstants.todayStr()}_")
        appendLine()
        db.firqoh.forEachIndexed { idx, f ->
            val st = statsCalc(f)
            val count = db.santri.count { it.firqohId == f.id }
            appendLine("${idx + 1}. *${f.nama}*")
            appendLine("   • Jumlah: $count Santri | Kehadiran: *${st.pct}%*")
            appendLine("   • Hadir: ${st.hadir}/${st.total} (I:${st.izin} S:${st.sakit} A:${st.alpha})")
        }
    }.trim()

    fun buildDashboardFirqohPdfHtml(db: MmqaDatabase, statsCalc: (FirqohItem) -> AttendanceStats): String {
        val rows = db.firqoh.mapIndexed { idx, f ->
            val st = statsCalc(f)
            val count = db.santri.count { it.firqohId == f.id }
            listOf("${idx + 1}", f.kode, f.nama, "$count", "${st.hadir}", "${st.izin}", "${st.sakit}", "${st.alpha}", "${st.pct}%")
        }
        return wrapReportHtml(
            title = "Rekapan Kehadiran Santri Per Firqoh",
            subtitle = "Laporan Persentase Kehadiran Majelis",
            metaLine = "Total ${db.firqoh.size} Firqoh • ${db.santri.size} Santri",
            tableHeaders = listOf("No", "Kode", "Nama Firqoh", "Santri", "H", "I", "S", "A", "Persen"),
            rows = rows
        )
    }

    fun buildDashboardSantriWa(
        db: MmqaDatabase,
        filterLabel: String,
        santriList: List<SantriItem>,
        statsCalc: (SantriItem) -> AttendanceStats
    ): String = buildString {
        appendLine("🎓 *REKAPAN KEHADIRAN SETIAP SANTRI*")
        appendLine("Filter: *$filterLabel* (${santriList.size} Santri)")
        appendLine("_MMQA Al Rifai 02 • ${MmqaConstants.todayStr()}_")
        appendLine()
        santriList.forEachIndexed { idx, s ->
            val st = statsCalc(s)
            appendLine("${idx + 1}. *${s.nama}* (NIS ${s.nis})")
            appendLine("   ${firqohName(db, s.firqohId)} • *${st.pct}%* (H:${st.hadir} I:${st.izin} S:${st.sakit} A:${st.alpha})")
        }
    }.trim()

    fun buildDashboardSantriPdfHtml(
        db: MmqaDatabase,
        filterLabel: String,
        santriList: List<SantriItem>,
        statsCalc: (SantriItem) -> AttendanceStats
    ): String {
        val rows = santriList.mapIndexed { idx, s ->
            val st = statsCalc(s)
            listOf("${idx + 1}", s.nis, s.nama, firqohName(db, s.firqohId), "${st.hadir}", "${st.izin}", "${st.sakit}", "${st.alpha}", "${st.pct}%")
        }
        return wrapReportHtml(
            title = "Rekapan Kehadiran Setiap Santri",
            subtitle = "Akumulasi Seluruh Sesi Presensi Santri",
            metaLine = "Filter: $filterLabel • Jumlah: ${santriList.size} Santri",
            tableHeaders = listOf("No", "NIS", "Nama Santri", "Firqoh", "H", "I", "S", "A", "Kehadiran"),
            rows = rows
        )
    }

    fun buildDashboardMuallimWa(db: MmqaDatabase, statsCalc: (MuallimItem) -> AttendanceStats): String = buildString {
        appendLine("📖 *REKAPAN KEHADIRAN MU'ALLIM*")
        appendLine("_MMQA Al Rifai 02 • ${MmqaConstants.todayStr()}_")
        appendLine()
        db.muallim.forEachIndexed { idx, m ->
            val st = statsCalc(m)
            appendLine("${idx + 1}. *${m.nama}* (NIY ${m.niy})")
            appendLine("   ${firqohName(db, m.firqohId)} • *${st.pct}%* (H:${st.hadir} I:${st.izin} S:${st.sakit} A:${st.alpha})")
        }
    }.trim()

    fun buildDashboardMuallimPdfHtml(db: MmqaDatabase, statsCalc: (MuallimItem) -> AttendanceStats): String {
        val rows = db.muallim.mapIndexed { idx, m ->
            val st = statsCalc(m)
            listOf("${idx + 1}", m.niy, m.nama, firqohName(db, m.firqohId), "${st.hadir}", "${st.izin}", "${st.sakit}", "${st.alpha}", "${st.pct}%")
        }
        return wrapReportHtml(
            title = "Rekapan Persentase Kehadiran Mu'allim",
            subtitle = "Presensi Mengajar Mu'allim Perfirqoh",
            metaLine = "Total: ${db.muallim.size} Mu'allim",
            tableHeaders = listOf("No", "NIY", "Nama Mu'allim", "Firqoh", "H", "I", "S", "A", "Kehadiran"),
            rows = rows
        )
    }

    fun buildDashboardDewanWa(db: MmqaDatabase, statsCalc: (DewanHarianItem) -> AttendanceStats): String = buildString {
        appendLine("👑 *REKAPAN KEHADIRAN DEWAN HARIAN MMQA*")
        appendLine("_MMQA Al Rifai 02 • ${MmqaConstants.todayStr()}_")
        appendLine()
        db.dewanHarian.forEachIndexed { idx, d ->
            val st = statsCalc(d)
            appendLine("${idx + 1}. *${d.nama}* (${d.kode})")
            appendLine("   ${d.jabatan} • *${st.pct}%* (H:${st.hadir} I:${st.izin} S:${st.sakit} A:${st.alpha})")
        }
    }.trim()

    fun buildDashboardDewanPdfHtml(db: MmqaDatabase, statsCalc: (DewanHarianItem) -> AttendanceStats): String {
        val rows = db.dewanHarian.mapIndexed { idx, d ->
            val st = statsCalc(d)
            listOf("${idx + 1}", d.kode, d.nama, d.jabatan, "${st.hadir}", "${st.izin}", "${st.sakit}", "${st.alpha}", "${st.pct}%")
        }
        return wrapReportHtml(
            title = "Rekapan Kehadiran Dewan Harian MMQA",
            subtitle = "Presensi Piket & Rapat Pengurus Dewan Harian",
            metaLine = "Total: ${db.dewanHarian.size} Pengurus",
            tableHeaders = listOf("No", "Kode", "Nama Pengurus", "Jabatan", "H", "I", "S", "A", "Kehadiran"),
            rows = rows
        )
    }

    // =========================================================================
    // 2. UJIAN: PESERTA UJIAN & REKAPAN HASIL UJIAN
    // =========================================================================
    fun buildUjianPesertaWa(
        db: MmqaDatabase,
        filterLabel: String,
        santriList: List<SantriItem>
    ): String = buildString {
        val terdaftar = santriList.count { it.pesertaUjian }
        appendLine("📋 *DAFTAR PESERTA UJIAN MMQA AR2*")
        appendLine("Filter: *$filterLabel* ($terdaftar/${santriList.size} Terdaftar)")
        appendLine("_Tanggal: ${MmqaConstants.todayStr()}_")
        appendLine()
        santriList.forEachIndexed { idx, s ->
            val status = if (s.pesertaUjian) "✅ Terdaftar" else "⏸ Ditangguhkan"
            appendLine("${idx + 1}. *${s.nama}* (UJN-${s.nis})")
            appendLine("   ${firqohName(db, s.firqohId)} • $status")
        }
    }.trim()

    fun buildUjianPesertaPdfHtml(
        db: MmqaDatabase,
        filterLabel: String,
        santriList: List<SantriItem>
    ): String {
        val terdaftar = santriList.count { it.pesertaUjian }
        val rows = santriList.mapIndexed { idx, s ->
            listOf(
                "${idx + 1}",
                "UJN-${s.nis}",
                s.nis,
                s.nama,
                firqohName(db, s.firqohId),
                if (s.pesertaUjian) "Terdaftar Ujian" else "Ditangguhkan"
            )
        }
        return wrapReportHtml(
            title = "Daftar Peserta Ujian Santri",
            subtitle = "Penetapan Peserta Ujian dari Data Induk",
            metaLine = "Filter: $filterLabel",
            summaryBanner = "Terdaftar Ujian: $terdaftar dari ${santriList.size} Santri",
            tableHeaders = listOf("No", "No. Peserta", "NIS", "Nama Santri", "Firqoh", "Status"),
            rows = rows
        )
    }

    fun buildUjianRekapanWa(
        db: MmqaDatabase,
        filterLabel: String,
        rowsData: List<Triple<SantriItem, NilaiUjianItem?, Int>>,
        avgNilai: String,
        lulusCount: Int
    ): String = buildString {
        val sudah = rowsData.count { it.second != null }
        appendLine("🎓 *REKAPAN HASIL UJIAN MMQA AR2*")
        appendLine("Filter: *$filterLabel*")
        appendLine("Dinilai: *$sudah/${rowsData.size}* • Lulus KKM: *$lulusCount* • Rata-rata: *$avgNilai*")
        appendLine()
        rowsData.forEachIndexed { idx, (santri, nilai, kkm) ->
            if (nilai != null) {
                val status = if (nilai.nilaiAkhir >= kkm) "LULUS" else "REMEDIAL"
                appendLine("${idx + 1}. *${santri.nama}* (UJN-${santri.nis})")
                appendLine("   ${firqohName(db, santri.firqohId)} • Nilai: *${nilai.nilaiAkhir}* (${nilai.predikat} - $status)")
            } else {
                appendLine("${idx + 1}. *${santri.nama}* (UJN-${santri.nis})")
                appendLine("   ${firqohName(db, santri.firqohId)} • _Belum Ujian_")
            }
        }
    }.trim()

    fun buildUjianRekapanPdfHtml(
        db: MmqaDatabase,
        filterLabel: String,
        rowsData: List<Triple<SantriItem, NilaiUjianItem?, Int>>,
        avgNilai: String,
        lulusCount: Int
    ): String {
        val sudah = rowsData.count { it.second != null }
        val rows = rowsData.mapIndexed { idx, (santri, nilai, kkm) ->
            val status = when {
                nilai == null -> "Belum Ujian"
                nilai.nilaiAkhir >= kkm -> "Lulus KKM"
                else -> "Remedial"
            }
            listOf(
                "${idx + 1}",
                "UJN-${santri.nis}",
                santri.nama,
                firqohName(db, santri.firqohId),
                "$kkm",
                nilai?.nilaiAkhir?.toString() ?: "-",
                nilai?.predikat ?: "-",
                status,
                nilai?.catatan?.ifBlank { "-" } ?: "-"
            )
        }
        return wrapReportHtml(
            title = "Rekapan Hasil Ujian Santri",
            subtitle = "Evaluasi Penilaian Rubrik Berbobot",
            metaLine = "Filter: $filterLabel",
            summaryBanner = "Peserta: ${rowsData.size} | Dinilai: $sudah | Lulus KKM: $lulusCount | Rata-rata: $avgNilai",
            tableHeaders = listOf("Rank", "No. Peserta", "Nama Santri", "Firqoh", "KKM", "Nilai", "Predikat", "Status", "Catatan"),
            rows = rows
        )
    }

    // =========================================================================
    // 3. DATA INDUK (SANTRI, MU'ALLIM, DEWAN HARIAN)
    // =========================================================================
    fun buildDataSantriWa(
        db: MmqaDatabase,
        filterLabel: String,
        santriList: List<SantriItem>
    ): String = buildString {
        appendLine("🗂️ *DATA INDUK SANTRI MMQA AR2*")
        appendLine("Filter: *$filterLabel* (${santriList.size} Santri)")
        appendLine("_Tanggal: ${MmqaConstants.todayStr()}_")
        appendLine()
        santriList.forEachIndexed { idx, s ->
            appendLine("${idx + 1}. *${s.nama}* (NIS ${s.nis})")
            appendLine("   ${firqohName(db, s.firqohId)} • Angk. ${s.angkatan.ifBlank { "-" }}")
            if (s.wali.isNotBlank() || s.hpWali.isNotBlank()) {
                appendLine("   Wali: ${s.wali.ifBlank { "-" }} (${s.hpWali.ifBlank { "-" }})")
            }
        }
    }.trim()

    fun buildDataSantriPdfHtml(
        db: MmqaDatabase,
        filterLabel: String,
        santriList: List<SantriItem>
    ): String {
        val rows = santriList.mapIndexed { idx, s ->
            listOf(
                "${idx + 1}",
                s.nis,
                s.nama,
                firqohName(db, s.firqohId),
                s.angkatan.ifBlank { "-" },
                s.wali.ifBlank { "-" },
                s.hpWali.ifBlank { "-" },
                s.statusSyahriah
            )
        }
        return wrapReportHtml(
            title = "Data Induk Santri",
            subtitle = "Master Data Santri Majelis",
            metaLine = "Filter: $filterLabel • Total: ${santriList.size} Santri",
            tableHeaders = listOf("No", "NIS", "Nama Santri", "Firqoh", "Angkatan", "Wali", "HP Wali", "Syahriah"),
            rows = rows
        )
    }

    fun buildDataMuallimWa(db: MmqaDatabase, muallimList: List<MuallimItem>): String = buildString {
        appendLine("📖 *DATA INDUK MU'ALLIM MMQA AR2*")
        appendLine("Total: *${muallimList.size} Mu'allim* • _${MmqaConstants.todayStr()}_")
        appendLine()
        muallimList.forEachIndexed { idx, m ->
            appendLine("${idx + 1}. *${m.nama}* (NIY ${m.niy})")
            appendLine("   Firqoh: ${firqohName(db, m.firqohId)}")
            appendLine("   Bidang: ${m.bidang.ifBlank { "-" }} • HP: ${m.hp.ifBlank { "-" }}")
        }
    }.trim()

    fun buildDataMuallimPdfHtml(db: MmqaDatabase, muallimList: List<MuallimItem>): String {
        val rows = muallimList.mapIndexed { idx, m ->
            listOf("${idx + 1}", m.niy, m.nama, firqohName(db, m.firqohId), m.bidang.ifBlank { "-" }, m.hp.ifBlank { "-" })
        }
        return wrapReportHtml(
            title = "Data Induk Mu'allim",
            subtitle = "Daftar Pengajar & Mu'allim Firqoh",
            metaLine = "Total: ${muallimList.size} Mu'allim",
            tableHeaders = listOf("No", "NIY", "Nama Mu'allim", "Firqoh Ampuan", "Bidang Keilmuan", "No. HP"),
            rows = rows
        )
    }

    fun buildDataDewanWa(dewanList: List<DewanHarianItem>): String = buildString {
        appendLine("👑 *DATA DEWAN HARIAN MMQA AR2*")
        appendLine("Total: *${dewanList.size} Pengurus* • _${MmqaConstants.todayStr()}_")
        appendLine()
        dewanList.forEachIndexed { idx, d ->
            appendLine("${idx + 1}. *${d.nama}* (${d.kode})")
            appendLine("   Jabatan: ${d.jabatan} • HP: ${d.hp.ifBlank { "-" }}")
        }
    }.trim()

    fun buildDataDewanPdfHtml(dewanList: List<DewanHarianItem>): String {
        val rows = dewanList.mapIndexed { idx, d ->
            listOf("${idx + 1}", d.kode, d.nama, d.jabatan, d.hp.ifBlank { "-" })
        }
        return wrapReportHtml(
            title = "Data Dewan Harian MMQA",
            subtitle = "Pengurus Harian Majelis",
            metaLine = "Total: ${dewanList.size} Pengurus",
            tableHeaders = listOf("No", "Kode", "Nama Pengurus", "Jabatan", "No. HP"),
            rows = rows
        )
    }

    // =========================================================================
    // 4. STRUKTUR PERSONALIA MMQA
    // =========================================================================
    fun buildPersonaliaWa(list: List<PersonaliaItem>, filterDivisi: String = "ALL"): String = buildString {
        val sorted = list.sortedWith(compareBy<PersonaliaItem> { it.urutan }.thenBy { it.nama })
        appendLine("🧑‍💼 *STRUKTUR PERSONALIA MMQA AL RIFAI 02*")
        if (filterDivisi != "ALL") appendLine("Divisi: *$filterDivisi*")
        appendLine("Total: *${sorted.size} Personalia* • _${MmqaConstants.todayStr()}_")
        appendLine()
        sorted.groupBy { it.divisi.ifBlank { "Pengurus MMQA" } }.forEach { (divisi, members) ->
            appendLine("📌 *$divisi*")
            members.forEach { p ->
                appendLine("  • *${p.jabatan}*: ${p.nama} (${p.kode})")
                if (p.kontak.isNotBlank()) appendLine("    Kontak: ${p.kontak} • Periode: ${p.periode}")
            }
            appendLine()
        }
    }.trim()

    fun buildPersonaliaPdfHtml(list: List<PersonaliaItem>, filterDivisi: String = "ALL"): String {
        val sorted = list.sortedWith(compareBy<PersonaliaItem> { it.urutan }.thenBy { it.nama })
        val rows = sorted.mapIndexed { idx, p ->
            listOf(
                "${idx + 1}",
                p.kode,
                p.divisi,
                p.jabatan,
                p.nama,
                p.periode,
                p.kontak.ifBlank { "-" }
            )
        }
        return wrapReportHtml(
            title = "Struktur Personalia MMQA Al Rifai 02",
            subtitle = "Susunan Kepengurusan & Personalia Resmi Majelis",
            metaLine = if (filterDivisi == "ALL") "Semua Divisi • Total: ${sorted.size} Personalia" else "Divisi: $filterDivisi • Total: ${sorted.size} Personalia",
            tableHeaders = listOf("No", "Kode", "Divisi / Bidang", "Jabatan", "Nama Personalia", "Periode", "Kontak"),
            rows = rows
        )
    }

    // =========================================================================
    // 5. REKAP SANTRI BERMASALAH (SINKRON JUKNIS)
    // =========================================================================
    fun buildProblematicSantriWa(
        filterLabel: String,
        ruleDoc: JuknisDocumentItem,
        items: List<SantriViolationResult>
    ): String = buildString {
        appendLine("⚠️ *REKAP SANTRI PERLU PEMBINAAN (SINKRON JUKNIS)*")
        appendLine("Filter: *$filterLabel* • Jumlah: *${items.size} Santri*")
        appendLine("Acuan: _${ruleDoc.judul} (${ruleDoc.nomorDokumen})_")
        appendLine("Batas: Alpha ≥${ruleDoc.batasAlphaSp1}/${ruleDoc.batasAlphaSp2}/${ruleDoc.batasAlphaSp3}x | Izin ≥${ruleDoc.batasIzinPeringatan}x")
        appendLine()
        if (items.isEmpty()) {
            appendLine("✅ Alhamdulillah, tidak ada santri yang melewati batas pelanggaran pada filter ini.")
        } else {
            items.forEachIndexed { idx, r ->
                appendLine("${idx + 1}. *${r.santri.nama}* (NIS ${r.santri.nis})")
                appendLine("   Firqoh: ${r.firqohName}")
                appendLine("   Status: *${r.levelLabel}* (Alpha: ${r.alphaCount}x | Izin: ${r.izinCount}x | S: ${r.sakitCount}x)")
                appendLine("   Konsekuensi: _${r.consequences.joinToString("; ")}_")
            }
        }
    }.trim()

    fun buildProblematicSantriPdfHtml(
        filterLabel: String,
        ruleDoc: JuknisDocumentItem,
        items: List<SantriViolationResult>
    ): String {
        val rows = items.mapIndexed { idx, r ->
            listOf(
                "${idx + 1}",
                r.santri.nis,
                r.santri.nama,
                r.firqohName,
                "${r.alphaCount}",
                "${r.izinCount}",
                r.levelLabel,
                r.consequences.joinToString(" | ")
            )
        }
        return wrapReportHtml(
            title = "Rekap Santri Bermasalah & Tindak Lanjut Pembinaan",
            subtitle = "Acuan Juknis: ${ruleDoc.judul} (${ruleDoc.nomorDokumen})",
            metaLine = "Filter: $filterLabel • Total Terpantau: ${items.size} Santri",
            summaryBanner = "Batas Aktif Juknis — Alpha SP1 ≥ ${ruleDoc.batasAlphaSp1}x | SP2 ≥ ${ruleDoc.batasAlphaSp2}x | Berat ≥ ${ruleDoc.batasAlphaSp3}x | Izin ≥ ${ruleDoc.batasIzinPeringatan}x",
            tableHeaders = listOf("No", "NIS", "Nama Santri", "Firqoh", "Alpha", "Izin", "Kategori", "Konsekuensi (Sesuai Juknis)"),
            rows = rows
        )
    }

    // =========================================================================
    // 6. REKAP KEUANGAN DASHBOARD
    // =========================================================================
    fun buildDashboardKeuanganWa(db: MmqaDatabase): String = buildString {
        val masuk = db.keuangan.filter { it.jenis == "pemasukan" }.sumOf { it.nominal }
        val keluar = db.keuangan.filter { it.jenis == "pengeluaran" }.sumOf { it.nominal }
        val lunas = db.santri.count { it.statusSyahriah == "Lunas" }
        appendLine("💰 *REKAP KEUANGAN MAJELIS MMQA AR2*")
        appendLine("_Tanggal: ${MmqaConstants.todayStr()}_")
        appendLine()
        appendLine("• Total Pemasukan : *${MmqaConstants.formatRupiah(masuk)}*")
        appendLine("• Total Pengeluaran : *${MmqaConstants.formatRupiah(keluar)}*")
        appendLine("• Saldo Kas Akhir : *${MmqaConstants.formatRupiah(masuk - keluar)}*")
        appendLine("• Syahriah Lunas : *$lunas / ${db.santri.size} Santri*")
    }.trim()

    fun buildDashboardKeuanganPdfHtml(db: MmqaDatabase): String {
        val masuk = db.keuangan.filter { it.jenis == "pemasukan" }.sumOf { it.nominal }
        val keluar = db.keuangan.filter { it.jenis == "pengeluaran" }.sumOf { it.nominal }
        val rows = db.keuangan.mapIndexed { idx, k ->
            listOf(
                "${idx + 1}",
                k.tanggal,
                if (k.jenis == "pemasukan") "Pemasukan" else "Pengeluaran",
                k.kategori,
                MmqaConstants.formatRupiah(k.nominal),
                k.keterangan,
                k.pencatat
            )
        }
        return wrapReportHtml(
            title = "Rekapan Keuangan & Kas Majelis",
            subtitle = "Ringkasan Pemasukan, Pengeluaran & Saldo Kas",
            metaLine = "Total Transaksi: ${db.keuangan.size}",
            summaryBanner = "Pemasukan: ${MmqaConstants.formatRupiah(masuk)} | Pengeluaran: ${MmqaConstants.formatRupiah(keluar)} | Saldo Akhir: ${MmqaConstants.formatRupiah(masuk - keluar)}",
            tableHeaders = listOf("No", "Tanggal", "Jenis", "Kategori", "Nominal", "Keterangan", "Pencatat"),
            rows = rows
        )
    }

    // =========================================================================
    // 7. JADWAL KEGIATAN MMQA
    // =========================================================================
    fun buildJadwalWa(
        db: MmqaDatabase,
        kategoriLabel: String,
        items: List<JadwalItem>
    ): String = buildString {
        appendLine("🗓️ *JADWAL KEGIATAN MMQA AL RIFAI 02*")
        appendLine("Kategori: *$kategoriLabel* (${items.size} Agenda)")
        appendLine("_Diperbarui: ${MmqaConstants.todayStr()}_")
        appendLine()
        items.forEachIndexed { idx, j ->
            val tglInfo = if (j.tanggal.isNotBlank()) "${j.hari}, ${j.tanggal}" else j.hari
            val firqohLbl = if (j.firqohId == "ALL" || j.firqohId.isBlank()) {
                "Semua Firqoh"
            } else {
                FirqohFilterHelper.getDisplayLabel(j.firqohId, db.firqoh)
            }
            appendLine("${idx + 1}. *[${j.kategori}] ${j.judul}*")
            appendLine("   📅 $tglInfo • ⏰ ${j.jamMulai} - ${j.jamSelesai} WIB")
            appendLine("   📍 ${j.tempat} • Firqoh: $firqohLbl")
            appendLine("   👤 PJ: ${j.penanggungJawab}")
            if (j.keterangan.isNotBlank()) appendLine("   📝 ${j.keterangan}")
        }
    }.trim()

    fun buildJadwalPdfHtml(
        db: MmqaDatabase,
        kategoriLabel: String,
        items: List<JadwalItem>
    ): String {
        val rows = items.mapIndexed { idx, j ->
            val tglInfo = if (j.tanggal.isNotBlank()) "${j.hari}, ${j.tanggal}" else j.hari
            val firqohLbl = if (j.firqohId == "ALL" || j.firqohId.isBlank()) {
                "Semua Firqoh"
            } else {
                FirqohFilterHelper.getDisplayLabel(j.firqohId, db.firqoh)
            }
            listOf(
                "${idx + 1}",
                j.kategori,
                j.judul,
                tglInfo,
                "${j.jamMulai} - ${j.jamSelesai}",
                j.tempat,
                firqohLbl,
                j.penanggungJawab,
                j.keterangan.ifBlank { "-" }
            )
        }
        return wrapReportHtml(
            title = "Jadwal Resmi Kegiatan MMQA Al Rifai 02",
            subtitle = "Agenda Harian, Ujian, Absensi & Event Khusus Majelis",
            metaLine = "Kategori: $kategoriLabel • Jumlah Agenda: ${items.size}",
            tableHeaders = listOf("No", "Kategori", "Agenda / Kegiatan", "Hari / Tanggal", "Jam", "Tempat", "Firqoh", "PJ", "Keterangan"),
            rows = rows
        )
    }

    // =========================================================================
    // 8. REKAP ABSENSI PER SESI / EVENT KEHADIRAN TERTENTU
    // =========================================================================
    fun buildSessionAbsensiWa(
        tanggal: String,
        sesiLabel: String,
        kategoriLabel: String,
        rows: List<Triple<String, String, String>> // (nama, info, status)
    ): String = buildString {
        val hadir = rows.count { it.third == "Hadir" }
        val izin = rows.count { it.third == "Izin" }
        val sakit = rows.count { it.third == "Sakit" }
        val alpha = rows.count { it.third == "Alpha" }
        val belum = rows.count { it.third.isBlank() }
        appendLine("📋 *REKAP KEHADIRAN — $sesiLabel*")
        appendLine("📅 Tanggal: *$tanggal* • Kategori: *$kategoriLabel*")
        appendLine("✅ Hadir: *$hadir* | 🟡 Izin: *$izin* | 🔵 Sakit: *$sakit* | 🔴 Alpha: *$alpha* | ⚪ Belum: *$belum*")
        appendLine()
        rows.forEachIndexed { idx, (nama, info, status) ->
            val st = status.ifBlank { "Belum Absen" }
            appendLine("${idx + 1}. *$nama* — *$st* ($info)")
        }
    }.trim()

    fun buildSessionAbsensiPdfHtml(
        tanggal: String,
        sesiLabel: String,
        kategoriLabel: String,
        rows: List<Triple<String, String, String>>
    ): String {
        val hadir = rows.count { it.third == "Hadir" }
        val izin = rows.count { it.third == "Izin" }
        val sakit = rows.count { it.third == "Sakit" }
        val alpha = rows.count { it.third == "Alpha" }
        val belum = rows.count { it.third.isBlank() }
        val tableRows = rows.mapIndexed { idx, (nama, info, status) ->
            listOf("${idx + 1}", nama, info, status.ifBlank { "Belum Absen" })
        }
        return wrapReportHtml(
            title = "Rekap Presensi — $sesiLabel",
            subtitle = "Daftar Kehadiran $kategoriLabel MMQA Al Rifai 02",
            metaLine = "Tanggal: $tanggal • Total Peserta: ${rows.size}",
            summaryBanner = "Hadir: $hadir | Izin: $izin | Sakit: $sakit | Alpha: $alpha | Belum Absen: $belum",
            tableHeaders = listOf("No", "Nama Lengkap", "Keterangan / Firqoh", "Status Kehadiran"),
            rows = tableRows
        )
    }
}
