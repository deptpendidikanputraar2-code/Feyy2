package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.ByteArrayOutputStream

private data class SignatureStroke(
    val points: List<Offset>,
    val color: Color,
    val widthPx: Float
)

fun decodeSignatureImageBitmap(dataUrl: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (dataUrl.isBlank()) return null
    return runCatching {
        val b64 = dataUrl.substringAfter("base64,", dataUrl).trim()
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

private fun renderStrokesToBase64Png(
    strokes: List<SignatureStroke>,
    canvasSize: IntSize,
    outWidth: Int = 720,
    outHeight: Int = 280
): String {
    if (strokes.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return ""
    val bmp = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    val scaleX = outWidth.toFloat() / canvasSize.width.toFloat()
    val scaleY = outHeight.toFloat() / canvasSize.height.toFloat()
    val strokeScale = (scaleX + scaleY) * 0.5f

    strokes.forEach { st ->
        if (st.points.isEmpty()) return@forEach
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = (st.widthPx * strokeScale).coerceAtLeast(3.5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (st.points.size == 1) {
            val p = st.points.first()
            canvas.drawPoint(p.x * scaleX, p.y * scaleY, paint)
        } else {
            val path = AndroidPath()
            val first = st.points.first()
            path.moveTo(first.x * scaleX, first.y * scaleY)
            for (i in 1 until st.points.size) {
                val prev = st.points[i - 1]
                val cur = st.points[i]
                val midX = (prev.x + cur.x) * 0.5f * scaleX
                val midY = (prev.y + cur.y) * 0.5f * scaleY
                path.quadTo(prev.x * scaleX, prev.y * scaleY, midX, midY)
            }
            val last = st.points.last()
            path.lineTo(last.x * scaleX, last.y * scaleY)
            canvas.drawPath(path, paint)
        }
    }

    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    bmp.recycle()
    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    return "data:image/png;base64,$b64"
}

@Composable
fun SignaturePadDialog(
    title: String,
    signerName: String,
    signerRole: String,
    onSaveSignature: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var inkColor by remember { mutableStateOf(Color(0xFF0F172A)) }
    var strokeWidthPx by remember { mutableFloatStateOf(7.5f) }

    MmqaModalDialog(title = "✍️ $title", onDismiss = onDismiss) {
        Text(
            text = "Penandatangan: $signerName ($signerRole)",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Emerald900
        )
        Text(
            text = "Goreskan tanda tangan manual langsung di kotak kertas putih di bawah ini menggunakan jari atau stylus.",
            fontSize = 11.sp,
            color = Slate500
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Ink color & stroke thickness controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tinta:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                listOf(
                    Color(0xFF0F172A) to "Hitam",
                    Color(0xFF1D4ED8) to "Biru"
                ).forEach { (c, label) ->
                    val selected = inkColor == c
                    Surface(
                        color = if (selected) Emerald50 else Slate50,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Emerald800 else Slate300),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { inkColor = c }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(c)
                            )
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(5f to "Halus", 7.5f to "Sedang", 11f to "Tebal").forEach { (w, lbl) ->
                    val selected = strokeWidthPx == w
                    Surface(
                        color = if (selected) Emerald900 else Slate100,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { strokeWidthPx = w }
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Amber300 else Slate700,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Signature Canvas
        Surface(
            color = Color.White,
            border = BorderStroke(1.5.dp, Emerald700.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(inkColor, strokeWidthPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activePoints = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pos = Offset(
                                        change.position.x.coerceIn(0f, size.width.toFloat()),
                                        change.position.y.coerceIn(0f, size.height.toFloat())
                                    )
                                    activePoints = activePoints + pos
                                },
                                onDragEnd = {
                                    if (activePoints.isNotEmpty()) {
                                        strokes.add(SignatureStroke(activePoints, inkColor, strokeWidthPx))
                                        activePoints = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    if (activePoints.isNotEmpty()) {
                                        strokes.add(SignatureStroke(activePoints, inkColor, strokeWidthPx))
                                        activePoints = emptyList()
                                    }
                                }
                            )
                        }
                        .testTag("signature_pad_canvas")
                ) {
                    // Baseline signature guide
                    val guideY = size.height * 0.78f
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(24f, guideY),
                        end = Offset(size.width - 24f, guideY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                    )

                    fun drawStroke(pts: List<Offset>, c: Color, w: Float) {
                        if (pts.isEmpty()) return
                        if (pts.size == 1) {
                            drawCircle(color = c, radius = w * 0.5f, center = pts.first())
                            return
                        }
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                val prev = pts[i - 1]
                                val cur = pts[i]
                                quadraticTo(prev.x, prev.y, (prev.x + cur.x) * 0.5f, (prev.y + cur.y) * 0.5f)
                            }
                            lineTo(pts.last().x, pts.last().y)
                        }
                        drawPath(
                            path = path,
                            color = c,
                            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    strokes.forEach { st -> drawStroke(st.points, st.color, st.widthPx) }
                    if (activePoints.isNotEmpty()) {
                        drawStroke(activePoints, inkColor, strokeWidthPx)
                    }
                }

                if (strokes.isEmpty() && activePoints.isEmpty()) {
                    Text(
                        text = "✍️ Goreskan tanda tangan manual di sini",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate500,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                enabled = strokes.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Undo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    activePoints = emptyList()
                },
                enabled = strokes.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = Rose700, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ulangi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) {
                Text("Batal", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val dataUrl = renderStrokesToBase64Png(strokes.toList(), canvasSize)
                    if (dataUrl.isNotBlank()) {
                        onSaveSignature(dataUrl)
                        onDismiss()
                    }
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald900, contentColor = Amber300),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("signature_pad_save_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simpan TTD", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun SignatureActionCard(
    label: String,
    dataUrl: String,
    onDrawClick: () -> Unit,
    onClearClick: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val bmp = remember(dataUrl) { decodeSignatureImageBitmap(dataUrl) }
    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, if (bmp != null) Emerald700.copy(alpha = 0.45f) else Slate200),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                if (bmp != null) {
                    StatusPill("TTD Tersimpan", Emerald100, Emerald900)
                } else {
                    StatusPill("Belum TTD", Slate200, Slate700)
                }
            }
            if (bmp != null) {
                Surface(
                    color = Color.White,
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDrawClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Emerald50, contentColor = Emerald900),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_draw_btn")
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        if (bmp != null) "Ubah TTD Manual" else "Buat TTD Manual",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (bmp != null) {
                    OutlinedButton(
                        onClick = onClearClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Rose200),
                        modifier = Modifier.testTag("${testTagPrefix}_clear_btn")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus TTD", tint = Rose700, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
