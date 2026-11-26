package com.example.ui.components

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.MmqaViewModel
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream

/**
 * Live-resolved barcode identity card model.
 * Every field (Nama, Firqoh, Mu'allim, Jabatan, PJ/Divisi) is dynamically derived
 * from the active MmqaDatabase so barcode cards automatically stay in sync when DB changes.
 */
data class MmqaBarcodeCard(
    val tipe: String, // "santri" | "muallim" | "dewan_harian"
    val targetId: String,
    val firqohId: String,
    val codeValue: String,
    val roleLabel: String,
    val nomorIdentitas: String,
    val nama: String,
    val primaryDetailLabel: String,
    val primaryDetailValue: String,
    val secondaryDetailLabel: String,
    val secondaryDetailValue: String
)

object MmqaBarcodeEngine {

    fun codeFor(tipe: String, targetId: String): String = when (tipe) {
        "santri" -> "MMQA|SNT|$targetId"
        "muallim" -> "MMQA|MLM|$targetId"
        else -> "MMQA|DWH|$targetId"
    }

    fun buildCardForSantri(db: MmqaDatabase, s: SantriItem): MmqaBarcodeCard {
        val firqohName = db.firqoh.firstOrNull { it.id == s.firqohId }?.nama ?: s.firqohId
        val muallimNames = db.muallim
            .filter { it.firqohId == s.firqohId }
            .map { it.nama }
            .distinct()
            .joinToString(", ")
            .ifBlank { "Belum ditetapkan" }
        return MmqaBarcodeCard(
            tipe = "santri",
            targetId = s.id,
            firqohId = s.firqohId,
            codeValue = codeFor("santri", s.id),
            roleLabel = "SANTRI MMQA AR2",
            nomorIdentitas = "NIS: ${s.nis}",
            nama = s.nama,
            primaryDetailLabel = "Firqoh",
            primaryDetailValue = firqohName,
            secondaryDetailLabel = "Mu'allim",
            secondaryDetailValue = muallimNames
        )
    }

    fun buildCardForMuallim(db: MmqaDatabase, m: MuallimItem): MmqaBarcodeCard {
        val firqohName = db.firqoh.firstOrNull { it.id == m.firqohId }?.nama ?: m.firqohId
        return MmqaBarcodeCard(
            tipe = "muallim",
            targetId = m.id,
            firqohId = m.firqohId,
            codeValue = codeFor("muallim", m.id),
            roleLabel = "MU'ALLIM FIRQOH",
            nomorIdentitas = "NIY: ${m.niy}",
            nama = m.nama,
            primaryDetailLabel = "Firqoh",
            primaryDetailValue = firqohName,
            secondaryDetailLabel = "Bidang",
            secondaryDetailValue = m.bidang.ifBlank { "Pengajar Firqoh" }
        )
    }

    fun buildCardForDewan(db: MmqaDatabase, d: DewanHarianItem): MmqaBarcodeCard {
        val linkedPersonalia = db.personalia.firstOrNull {
            it.nama.equals(d.nama, ignoreCase = true) ||
                (d.kode.isNotBlank() && it.kode.equals(d.kode, ignoreCase = true))
        }
        val pjDesc = linkedPersonalia?.divisi?.takeIf { it.isNotBlank() }
            ?: d.jabatan.takeIf { it.isNotBlank() }
            ?: "Dewan Harian MMQA"
        return MmqaBarcodeCard(
            tipe = "dewan_harian",
            targetId = d.id,
            firqohId = "",
            codeValue = codeFor("dewan_harian", d.id),
            roleLabel = "DEWAN HARIAN MMQA",
            nomorIdentitas = "Kode: ${d.kode}",
            nama = d.nama,
            primaryDetailLabel = "PJ / Divisi",
            primaryDetailValue = pjDesc,
            secondaryDetailLabel = "Jabatan",
            secondaryDetailValue = d.jabatan.ifBlank { "Pengurus Harian" }
        )
    }

    /**
     * Resolves any scanned QR / Code128 string back to a live MmqaBarcodeCard.
     * Supports MMQA|SNT|id, MMQA|MLM|id, MMQA|DWH|id, plus direct NIS / NIY / Kode / ID.
     */
    fun resolveScannedCode(db: MmqaDatabase, rawInput: String): MmqaBarcodeCard? {
        val code = rawInput.trim()
        if (code.isBlank()) return null

        val parts = code.split("|")
        if (parts.size >= 3 && parts[0].equals("MMQA", ignoreCase = true)) {
            val kind = parts[1].uppercase()
            val id = parts[2].trim()
            return when (kind) {
                "SNT" -> db.santri.firstOrNull { it.id.equals(id, true) || it.nis.equals(id, true) }
                    ?.let { buildCardForSantri(db, it) }
                "MLM" -> db.muallim.firstOrNull { it.id.equals(id, true) || it.niy.equals(id, true) }
                    ?.let { buildCardForMuallim(db, it) }
                "DWH" -> db.dewanHarian.firstOrNull { it.id.equals(id, true) || it.kode.equals(id, true) }
                    ?.let { buildCardForDewan(db, it) }
                else -> null
            }
        }

        // Direct fallback match by ID, NIS, NIY, or Kode
        db.santri.firstOrNull { it.id.equals(code, true) || it.nis.equals(code, true) }
            ?.let { return buildCardForSantri(db, it) }
        db.muallim.firstOrNull { it.id.equals(code, true) || it.niy.equals(code, true) }
            ?.let { return buildCardForMuallim(db, it) }
        db.dewanHarian.firstOrNull { it.id.equals(code, true) || it.kode.equals(code, true) }
            ?.let { return buildCardForDewan(db, it) }

        return null
    }

    fun generateBarcodeBitmap(
        content: String,
        format: BarcodeFormat,
        width: Int,
        height: Int
    ): Bitmap? {
        return runCatching {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = MultiFormatWriter().encode(content, format, width, height, hints)
            val pixels = IntArray(width * height)
            val black = android.graphics.Color.BLACK
            val white = android.graphics.Color.WHITE
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) black else white
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    fun bitmapToDataUrl(bmp: Bitmap?): String {
        if (bmp == null) return ""
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$b64"
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun buildPrintableCardsHtml(title: String, cards: List<MmqaBarcodeCard>): String {
        val cardsHtml = cards.joinToString("\n") { c ->
            val qrB64 = bitmapToDataUrl(generateBarcodeBitmap(c.codeValue, BarcodeFormat.QR_CODE, 220, 220))
            val barB64 = bitmapToDataUrl(generateBarcodeBitmap(c.codeValue, BarcodeFormat.CODE_128, 420, 96))
            val badgeBg = when (c.tipe) {
                "santri" -> "#065F46"
                "muallim" -> "#92400E"
                else -> "#075985"
            }
            """
            <div class="card">
              <div class="hdr" style="background:$badgeBg;">
                <div class="brand">MMQA AL RIFAI 02</div>
                <div class="role">${esc(c.roleLabel)}</div>
              </div>
              <div class="body">
                <div class="top-row">
                  <div class="info">
                    <div class="name">${esc(c.nama)}</div>
                    <div class="id-num">${esc(c.nomorIdentitas)}</div>
                    <div class="meta"><b>${esc(c.primaryDetailLabel)}:</b> ${esc(c.primaryDetailValue)}</div>
                    <div class="meta"><b>${esc(c.secondaryDetailLabel)}:</b> ${esc(c.secondaryDetailValue)}</div>
                  </div>
                  <img class="qr" src="$qrB64" alt="QR"/>
                </div>
                <div class="bar-wrap">
                  <img class="bar" src="$barB64" alt="Barcode"/>
                  <div class="code-txt">${esc(c.codeValue)}</div>
                </div>
              </div>
            </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <style>
            body { font-family: sans-serif; margin: 16px; color: #0F172A; }
            h2 { text-align: center; color: #064E3B; margin-bottom: 4px; }
            .sub { text-align: center; font-size: 11px; color: #64748B; margin-bottom: 16px; }
            .grid { display: flex; flex-wrap: wrap; gap: 12px; justify-content: flex-start; }
            .card { width: 47%; border: 1.5px solid #CBD5E1; border-radius: 12px; overflow: hidden; page-break-inside: avoid; background: #FFFFFF; }
            .hdr { color: #FFFFFF; padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; }
            .brand { font-weight: 800; font-size: 11px; color: #FCD34D; }
            .role { font-weight: 700; font-size: 9px; background: rgba(255,255,255,0.18); padding: 2px 7px; border-radius: 99px; }
            .body { padding: 10px 12px; }
            .top-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
            .info { flex: 1; }
            .name { font-size: 13px; font-weight: 800; color: #0F172A; margin-bottom: 2px; }
            .id-num { font-size: 10px; font-family: monospace; color: #047857; font-weight: 700; margin-bottom: 5px; }
            .meta { font-size: 10px; color: #334155; margin-bottom: 2px; line-height: 1.35; }
            .qr { width: 72px; height: 72px; border: 1px solid #E2E8F0; border-radius: 6px; padding: 2px; }
            .bar-wrap { margin-top: 8px; text-align: center; border-top: 1px dashed #CBD5E1; padding-top: 6px; }
            .bar { width: 94%; height: 38px; object-fit: contain; }
            .code-txt { font-family: monospace; font-size: 9px; color: #475569; margin-top: 2px; }
          </style>
        </head>
        <body>
          <h2>${esc(title)}</h2>
          <div class="sub">Kartu Barcode Absensi MMQA Al Rifai 02 • Sinkron Otomatis Database • Dicetak ${MmqaConstants.todayStr()}</div>
          <div class="grid">$cardsHtml</div>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Generates a dedicated standalone PDF containing ONLY one individual's crisp high-resolution QR code
     * and live database identity label (not merged with other people's cards).
     */
    fun buildPrintableSingleQrOnlyHtml(card: MmqaBarcodeCard): String {
        val qrB64 = bitmapToDataUrl(generateBarcodeBitmap(card.codeValue, BarcodeFormat.QR_CODE, 520, 520))
        val accentBg = when (card.tipe) {
            "santri" -> "#065F46"
            "muallim" -> "#92400E"
            else -> "#075985"
        }
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <style>
            @page { size: A5 portrait; margin: 14mm; }
            body { font-family: sans-serif; margin: 0; padding: 16px; color: #0F172A; display: flex; justify-content: center; }
            .wrap { width: 100%; max-width: 380px; border: 2px solid $accentBg; border-radius: 18px; overflow: hidden; background: #FFFFFF; text-align: center; }
            .hdr { background: $accentBg; color: #FFFFFF; padding: 14px 16px; }
            .brand { font-size: 13px; font-weight: 900; color: #FCD34D; letter-spacing: 0.6px; }
            .role { font-size: 10px; font-weight: 700; margin-top: 4px; opacity: 0.92; }
            .content { padding: 18px 18px 20px 18px; }
            .name { font-size: 18px; font-weight: 900; color: #0F172A; margin-bottom: 4px; }
            .id-num { font-size: 12px; font-family: monospace; font-weight: 700; color: $accentBg; margin-bottom: 12px; }
            .qr-box { display: inline-block; padding: 12px; border: 1.5px solid #CBD5E1; border-radius: 14px; background: #FFFFFF; }
            .qr { width: 220px; height: 220px; display: block; }
            .code-val { font-family: monospace; font-size: 11px; font-weight: 800; color: #334155; margin-top: 10px; }
            .details { margin-top: 14px; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 12px; padding: 10px 12px; text-align: left; }
            .detail-row { font-size: 12px; color: #1E293B; margin: 4px 0; }
            .foot { font-size: 9px; color: #64748B; margin-top: 12px; }
          </style>
        </head>
        <body>
          <div class="wrap">
            <div class="hdr">
              <div class="brand">MMQA AL RIFAI 02 (AR2)</div>
              <div class="role">QR CODE PRESENSI INDIVIDU • ${esc(card.roleLabel)}</div>
            </div>
            <div class="content">
              <div class="name">${esc(card.nama)}</div>
              <div class="id-num">${esc(card.nomorIdentitas)}</div>
              <div class="qr-box">
                <img class="qr" src="$qrB64" alt="QR Code ${esc(card.nama)}"/>
              </div>
              <div class="code-val">${esc(card.codeValue)}</div>
              <div class="details">
                <div class="detail-row"><b>${esc(card.primaryDetailLabel)}:</b> ${esc(card.primaryDetailValue)}</div>
                <div class="detail-row"><b>${esc(card.secondaryDetailLabel)}:</b> ${esc(card.secondaryDetailValue)}</div>
              </div>
              <div class="foot">Dicetak ${MmqaConstants.todayStr()} • Sinkron otomatis dengan Database MMQA AR2</div>
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }
}

@Composable
fun BarcodeCardPreviewDialog(
    card: MmqaBarcodeCard,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap: ImageBitmap? = remember(card.codeValue) {
        MmqaBarcodeEngine.generateBarcodeBitmap(card.codeValue, BarcodeFormat.QR_CODE, 360, 360)?.asImageBitmap()
    }
    val code128Bitmap: ImageBitmap? = remember(card.codeValue) {
        MmqaBarcodeEngine.generateBarcodeBitmap(card.codeValue, BarcodeFormat.CODE_128, 640, 140)?.asImageBitmap()
    }

    val badgeColor = when (card.tipe) {
        "santri" -> Emerald900
        "muallim" -> Amber900
        else -> Sky900
    }

    MmqaModalDialog(title = "🪪 Kartu Barcode ${card.roleLabel}", onDismiss = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, badgeColor.copy(alpha = 0.35f)),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(badgeColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("MMQA AL RIFAI 02", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Amber300)
                    StatusPill(card.roleLabel, Color.White.copy(alpha = 0.2f), Color.White)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(card.nama, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Slate900)
                    Text(
                        text = card.nomorIdentitas,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Emerald800
                    )

                    Surface(
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${card.primaryDetailLabel}: ${card.primaryDetailValue}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "${card.secondaryDetailLabel}: ${card.secondaryDetailValue}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                        }
                    }

                    if (qrBitmap != null) {
                        Surface(
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "QR Code ${card.nama}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(168.dp)
                                    .padding(8.dp)
                            )
                        }
                    }

                    if (code128Bitmap != null) {
                        Image(
                            bitmap = code128Bitmap,
                            contentDescription = "Barcode ${card.nama}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }

                    Text(
                        text = card.codeValue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate700
                    )
                    Text(
                        text = "Keterangan kartu otomatis berubah mengikuti pembaruan Data Induk.",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val safeName = card.nama.replace(Regex("[^A-Za-z0-9_-]"), "_").take(28)
                val html = MmqaBarcodeEngine.buildPrintableSingleQrOnlyHtml(card)
                printHtmlDocument(context, "QR_${safeName}_${card.targetId}", html)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("barcode_download_single_qr_pdf")
        ) {
            Icon(Icons.Default.Download, contentDescription = "Unduh PDF QR Sendiri", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Download PDF QR Sendiri (Khusus ${card.nama.substringBefore(" ")})", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val info = buildString {
                        appendLine("*KARTU BARCODE ${card.roleLabel}*")
                        appendLine("Nama: ${card.nama}")
                        appendLine(card.nomorIdentitas)
                        appendLine("${card.primaryDetailLabel}: ${card.primaryDetailValue}")
                        appendLine("${card.secondaryDetailLabel}: ${card.secondaryDetailValue}")
                        appendLine("Kode Scan: ${card.codeValue}")
                    }
                    shareTextToWhatsApp(context, info, "Bagikan Info Barcode")
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share WA", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share WA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            OutlinedButton(
                onClick = {
                    val html = MmqaBarcodeEngine.buildPrintableCardsHtml("Kartu Barcode — ${card.nama}", listOf(card))
                    printHtmlDocument(context, "Kartu_${card.targetId}", html)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Print, contentDescription = "Cetak Kartu Lengkap", tint = Emerald900, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kartu Lengkap", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Emerald900)
            }
        }
    }
}

@Composable
fun BarcodeDirectoryDialog(
    db: MmqaDatabase,
    viewModel: MmqaViewModel,
    initialTab: String,
    initialFirqohFilter: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(initialTab) }
    var firqohFilter by remember { mutableStateOf(initialFirqohFilter) }
    var search by remember { mutableStateOf("") }
    var previewCard by remember { mutableStateOf<MmqaBarcodeCard?>(null) }

    val cards = remember(db, activeTab, firqohFilter, search) {
        val q = search.trim().lowercase()
        val raw = when (activeTab) {
            "santri" -> db.santri
                .filter { viewModel.matchesFirqohFilter(it.firqohId, firqohFilter) }
                .map { MmqaBarcodeEngine.buildCardForSantri(db, it) }
            "muallim" -> db.muallim.map { MmqaBarcodeEngine.buildCardForMuallim(db, it) }
            else -> db.dewanHarian.map { MmqaBarcodeEngine.buildCardForDewan(db, it) }
        }
        if (q.isEmpty()) raw else raw.filter {
            it.nama.lowercase().contains(q) ||
                it.primaryDetailValue.lowercase().contains(q) ||
                it.secondaryDetailValue.lowercase().contains(q) ||
                it.nomorIdentitas.lowercase().contains(q)
        }
    }

    MmqaModalDialog(title = "🪪 Direktori Kartu Barcode Otomatis", onDismiss = onDismiss) {
        SubmenuTabsRow(
            items = listOf(
                "santri" to "Santri (${db.santri.size})",
                "muallim" to "Mu'allim (${db.muallim.size})",
                "dewan_harian" to "Dewan (${db.dewanHarian.size})"
            ),
            selectedId = activeTab,
            onSelect = { activeTab = it }
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (activeTab == "santri") {
            val opts = viewModel.buildFirqohFilterOptions(includeAll = true, countBySantri = true)
            CompactDropdownSelector(
                label = "Filter Firqoh / Grup",
                selectedText = opts.firstOrNull { it.first == firqohFilter }?.second
                    ?: viewModel.getFirqohFilterDisplayLabel(firqohFilter),
                options = opts,
                onSelect = { firqohFilter = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("🔍 Cari nama, firqoh, mu'allim, jabatan...", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (cards.isNotEmpty()) {
                    val label = when (activeTab) {
                        "santri" -> "Kartu Barcode Santri (${viewModel.getFirqohFilterDisplayLabel(firqohFilter)})"
                        "muallim" -> "Kartu Barcode Mu'allim MMQA AR2"
                        else -> "Kartu Barcode Dewan Harian MMQA AR2"
                    }
                    val html = MmqaBarcodeEngine.buildPrintableCardsHtml(label, cards)
                    printHtmlDocument(context, "Kartu_Barcode_${activeTab}_MMQA", html)
                }
            },
            enabled = cards.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("barcode_print_batch_btn")
        ) {
            Icon(Icons.Default.Print, contentDescription = "Cetak Semua PDF", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cetak / Unduh Semua (${cards.size} Kartu PDF)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { card ->
                Surface(
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { previewCard = card }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Surface(
                            color = Emerald100,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QrCode2, contentDescription = "Lihat Barcode", tint = Emerald900)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.nama,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate900,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${card.primaryDetailLabel}: ${card.primaryDetailValue}",
                                fontSize = 11.sp,
                                color = Emerald800,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${card.secondaryDetailLabel}: ${card.secondaryDetailValue}",
                                fontSize = 10.sp,
                                color = Slate500,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = Emerald900,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clickable {
                                    val safeName = card.nama.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24)
                                    val html = MmqaBarcodeEngine.buildPrintableSingleQrOnlyHtml(card)
                                    printHtmlDocument(context, "QR_${safeName}_${card.targetId}", html)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download PDF QR ${card.nama}",
                                    tint = Amber300,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("PDF QR", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Amber300)
                            }
                        }
                    }
                }
            }
        }
    }

    previewCard?.let { card ->
        BarcodeCardPreviewDialog(card = card, onDismiss = { previewCard = null })
    }
}
