package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.JuknisDocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Direct in-app PDF viewer powered by Android's native PdfRenderer.
 * If the document has an uploaded PDF file (base64 dataUrl), renders every page directly in-app.
 * Otherwise renders a crisp structured Juknis / Rules sheet and supports PDF export/print.
 */
@Composable
fun JuknisPdfViewerDialog(
    doc: JuknisDocumentItem,
    onSaveUploadedPdf: (fileName: String, mimeType: String, dataUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasBinaryPdf = doc.pdfDataUrl.isNotBlank()

    var renderedPages by remember(doc.id, doc.pdfDataUrl) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var currentPage by remember(doc.id) { mutableIntStateOf(0) }
    var isLoading by remember(doc.id, doc.pdfDataUrl) { mutableStateOf(hasBinaryPdf) }
    var renderError by remember(doc.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(doc.id, doc.pdfDataUrl) {
        if (!hasBinaryPdf) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        renderError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val pureB64 = doc.pdfDataUrl.substringAfter("base64,", doc.pdfDataUrl).trim()
                val bytes = Base64.decode(pureB64, Base64.DEFAULT)
                val tempFile = File.createTempFile("mmqa_juknis_view_", ".pdf", context.cacheDir)
                try {
                    tempFile.writeBytes(bytes)
                    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pfd.use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            val maxPages = renderer.pageCount.coerceAtMost(25)
                            val list = mutableListOf<ImageBitmap>()
                            for (i in 0 until maxPages) {
                                renderer.openPage(i).use { page ->
                                    val scale = (1080f / page.width.coerceAtLeast(1)).coerceIn(1.0f, 2.2f)
                                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    list.add(bmp.asImageBitmap())
                                }
                            }
                            list
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }
        result.onSuccess { pages ->
            renderedPages = pages
            currentPage = 0
        }.onFailure { err ->
            renderError = err.message ?: "Gagal merender halaman PDF."
        }
        isLoading = false
    }

    MmqaModalDialog(title = "📘 ${doc.judul}", onDismiss = onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusPill(doc.kategori, Emerald100, Emerald900)
            if (doc.isAcuanAturan) {
                StatusPill("⚖️ Acuan Peraturan Aktif", Amber100, Amber900)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No: ${doc.nomorDokumen.ifBlank { "-" }} • Tanggal: ${doc.tanggal} • Oleh: ${doc.uploadedBy}",
            fontSize = 11.sp,
            color = Slate500
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (hasBinaryPdf) {
            when {
                isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Emerald800)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Memuat halaman PDF...", fontSize = 11.sp, color = Slate500)
                        }
                    }
                }
                renderError != null -> {
                    Surface(
                        color = Rose50,
                        border = BorderStroke(1.dp, Rose200),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Preview langsung tidak tersedia ($renderError). Gunakan tombol Unduh PDF di bawah.",
                            fontSize = 12.sp,
                            color = Rose800,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                renderedPages.isNotEmpty() -> {
                    val safeIdx = currentPage.coerceIn(0, renderedPages.lastIndex)
                    Surface(
                        color = Slate100,
                        border = BorderStroke(1.dp, Slate300),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            bitmap = renderedPages[safeIdx],
                            contentDescription = "Halaman ${safeIdx + 1} dari ${doc.judul}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp)
                                .background(Color.White)
                        )
                    }
                    if (renderedPages.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { if (currentPage > 0) currentPage -= 1 },
                                enabled = currentPage > 0,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Sebelumnya")
                                Text("Sebelumnya", fontSize = 11.sp)
                            }
                            Text(
                                text = "Hal ${safeIdx + 1} / ${renderedPages.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Emerald900
                            )
                            OutlinedButton(
                                onClick = { if (currentPage < renderedPages.lastIndex) currentPage += 1 },
                                enabled = currentPage < renderedPages.lastIndex,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Berikutnya", fontSize = 11.sp)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Berikutnya")
                            }
                        }
                    }
                }
            }
        } else {
            // Structured document reader view when no binary PDF file was attached
            Surface(
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ringkasan & Isi Dokumen", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Emerald900)
                    Text(
                        text = doc.deskripsi.ifBlank { "Tidak ada ringkasan tambahan." },
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Slate700
                    )
                }
            }
        }

        if (doc.isAcuanAturan) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Amber50,
                border = BorderStroke(1.dp, Amber200),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "⚖️ Parameter Peraturan & Konsekuensi Tersinkron ke Dashboard",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Amber950
                    )
                    Text("• Alpha ≥ ${doc.batasAlphaSp1}x → ${doc.konsekuensiSp1}", fontSize = 11.sp, color = Slate900)
                    Text("• Alpha ≥ ${doc.batasAlphaSp2}x → ${doc.konsekuensiSp2}", fontSize = 11.sp, color = Slate900)
                    Text("• Alpha ≥ ${doc.batasAlphaSp3}x → ${doc.konsekuensiSp3}", fontSize = 11.sp, color = Rose800, fontWeight = FontWeight.Bold)
                    Text("• Izin ≥ ${doc.batasIzinPeringatan}x → ${doc.konsekuensiIzin}", fontSize = 11.sp, color = Amber900)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (hasBinaryPdf) {
                Button(
                    onClick = {
                        onSaveUploadedPdf(
                            doc.pdfName.ifBlank { "${doc.judul.take(28)}.pdf" },
                            "application/pdf",
                            doc.pdfDataUrl
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Unduh PDF", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh File PDF", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Button(
                    onClick = {
                        val html = buildJuknisDocHtml(doc)
                        printHtmlDocument(context, "Juknis_${doc.id}", html)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose700),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Cetak / Unduh PDF", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Unduh / Cetak PDF", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

fun buildJuknisDocHtml(doc: JuknisDocumentItem): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val ruleSection = if (doc.isAcuanAturan) {
        """
        <h3>Acuan Peraturan Pelanggaran Kehadiran & Konsekuensi</h3>
        <table>
          <thead><tr><th>Kategori Pelanggaran</th><th>Batas Minimal</th><th>Konsekuensi Resmi</th></tr></thead>
          <tbody>
            <tr><td>Peringatan 1 (Alpha)</td><td>≥ ${doc.batasAlphaSp1} kali</td><td>${esc(doc.konsekuensiSp1)}</td></tr>
            <tr><td>Peringatan 2 (Alpha)</td><td>≥ ${doc.batasAlphaSp2} kali</td><td>${esc(doc.konsekuensiSp2)}</td></tr>
            <tr><td>Tingkat Berat (Alpha)</td><td>≥ ${doc.batasAlphaSp3} kali</td><td>${esc(doc.konsekuensiSp3)}</td></tr>
            <tr><td>Batas Izin</td><td>≥ ${doc.batasIzinPeringatan} kali</td><td>${esc(doc.konsekuensiIzin)}</td></tr>
          </tbody>
        </table>
        """.trimIndent()
    } else ""

    return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8"/>
      <style>
        body { font-family: sans-serif; margin: 28px; color: #0F172A; line-height: 1.5; }
        .kop { text-align: center; border-bottom: 3px double #064E3B; padding-bottom: 10px; margin-bottom: 18px; }
        .kop h2 { margin: 0; color: #064E3B; }
        .kop p { margin: 3px 0 0; font-size: 12px; color: #475569; }
        .meta { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 10px; padding: 10px 14px; font-size: 12px; margin-bottom: 16px; }
        h3 { color: #065F46; margin-top: 18px; }
        table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px; }
        th, td { border: 1px solid #CBD5E1; padding: 8px; text-align: left; }
        th { background: #ECFDF5; color: #064E3B; }
      </style>
    </head>
    <body>
      <div class="kop">
        <h2>MMQA AL RIFAI 02</h2>
        <p>${esc(doc.kategori.uppercase())} RESMI MAJELIS</p>
      </div>
      <h3>${esc(doc.judul)}</h3>
      <div class="meta">
        <b>Nomor Dokumen:</b> ${esc(doc.nomorDokumen.ifBlank { "-" })}<br/>
        <b>Tanggal Terbit:</b> ${esc(doc.tanggal)}<br/>
        <b>Diunggah Oleh:</b> ${esc(doc.uploadedBy)}
      </div>
      <p>${esc(doc.deskripsi).replace("\n", "<br/>")}</p>
      $ruleSection
    </body>
    </html>
    """.trimIndent()
}
