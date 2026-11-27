package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AttendanceTimeEvaluator
import com.example.data.MmqaDatabase
import com.example.data.ScanTimingResult
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

private data class ScannedFeedbackUi(
    val card: MmqaBarcodeCard,
    val status: String,
    val timing: ScanTimingResult,
    val accentBg: Color,
    val accentBorder: Color,
    val accentText: Color,
    val badgeLabel: String
)

private fun distinctPaletteForTarget(targetId: String, terlambat: Boolean): Triple<Color, Color, Color> {
    if (terlambat) return Triple(Rose50, Rose600, Rose800)
    val palettes = listOf(
        Triple(Emerald50, Emerald700, Emerald900),
        Triple(Sky100, Sky700, Sky900),
        Triple(Amber50, Amber500, Amber950)
    )
    return palettes[abs(targetId.hashCode()) % palettes.size]
}

@Composable
fun BarcodeScannerDialog(
    db: MmqaDatabase,
    activeTanggal: String,
    activeSesi: String,
    initialCutoffHm: String? = null,
    onScannedAttendance: (card: MmqaBarcodeCard, status: String, cutoffTimeHm: String) -> ScanTimingResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
    }

    var selectedStatus by remember { mutableStateOf("Hadir") }
    var cutoffTimeHm by remember(activeSesi, db.jadwal, db.adminConfig.permissions, initialCutoffHm) {
        mutableStateOf(
            initialCutoffHm?.takeIf { AttendanceTimeEvaluator.parseMinutesOfDay(it) != null }
                ?: AttendanceTimeEvaluator.defaultCutoffForSession(activeSesi, db.jadwal, db.adminConfig.permissions)
        )
    }
    var soundAndVoiceEnabled by remember { mutableStateOf(true) }
    var autoWaOnScan by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var manualCode by remember { mutableStateOf("") }
    var lastScannedFeedback by remember { mutableStateOf<ScannedFeedbackUi?>(null) }
    var lastScanError by remember { mutableStateOf<String?>(null) }
    var scanCount by remember { mutableIntStateOf(0) }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { instance?.language = Locale("id", "ID") }
                ttsReady = true
            }
        }
        instance
    }

    val toneGen = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95) }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { tts.stop(); tts.shutdown() }
            runCatching { toneGen?.release() }
        }
    }

    // Deduplication map to avoid repeated rapid firing of the same barcode
    val lastSeenTimestamps = remember { mutableMapOf<String, Long>() }

    fun playPerPersonFeedback(card: MmqaBarcodeCard, status: String, timing: ScanTimingResult) {
        if (!soundAndVoiceEnabled) return
        // Distinct tone per person + alert tone when late
        val onTimeTones = listOf(
            ToneGenerator.TONE_PROP_BEEP,
            ToneGenerator.TONE_PROP_ACK,
            ToneGenerator.TONE_PROP_BEEP2,
            ToneGenerator.TONE_SUP_CONFIRM
        )
        runCatching {
            if (timing.terlambat) {
                toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 360)
            } else {
                val tone = onTimeTones[abs(card.targetId.hashCode()) % onTimeTones.size]
                toneGen?.startTone(tone, 200)
            }
        }
        if (ttsReady) {
            val speech = buildString {
                append(card.nama)
                append(", ")
                append(status)
                if (timing.terlambat) {
                    append(", terlambat ${timing.menitTelat} menit")
                } else if (status == "Hadir") {
                    append(", tepat waktu")
                }
            }
            runCatching {
                tts.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "SCAN_${card.targetId}_${System.currentTimeMillis()}")
            }
        }
    }

    fun triggerQuickWaForCard(card: MmqaBarcodeCard, status: String, timing: ScanTimingResult) {
        val waText = MmqaReportFormatters.buildSingleScanControllingWa(
            card = card,
            tanggal = activeTanggal,
            sesi = activeSesi,
            status = status,
            timing = timing
        )
        val phone = when (card.tipe) {
            "santri" -> db.santri.firstOrNull { it.id == card.targetId }?.hpWali ?: ""
            "muallim" -> db.muallim.firstOrNull { it.id == card.targetId }?.hp ?: ""
            else -> db.dewanHarian.firstOrNull { it.id == card.targetId }?.hp ?: ""
        }
        openWhatsAppDirect(context, phone, waText, "Kirim Notifikasi Scan WA")
    }

    fun handleRawCode(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val now = System.currentTimeMillis()
        val prev = lastSeenTimestamps[trimmed] ?: 0L
        if (now - prev < 2500L) return
        lastSeenTimestamps[trimmed] = now

        val card = MmqaBarcodeEngine.resolveScannedCode(db, trimmed)
        if (card != null) {
            lastScanError = null
            val timing = onScannedAttendance(card, selectedStatus, cutoffTimeHm)
            val (bg, border, txt) = distinctPaletteForTarget(card.targetId, timing.terlambat)
            val badge = when {
                timing.terlambat -> "⏰ TERLAMBAT +${timing.menitTelat} MNT"
                selectedStatus == "Hadir" -> "✅ TEPAT WAKTU"
                else -> "📌 ${selectedStatus.uppercase()}"
            }
            lastScannedFeedback = ScannedFeedbackUi(
                card = card,
                status = selectedStatus,
                timing = timing,
                accentBg = bg,
                accentBorder = border,
                accentText = txt,
                badgeLabel = badge
            )
            scanCount += 1
            playPerPersonFeedback(card, selectedStatus, timing)
            if (autoWaOnScan) {
                triggerQuickWaForCard(card, selectedStatus, timing)
            }
        } else {
            lastScanError = "Kode \"$trimmed\" tidak ditemukan di Data Induk MMQA."
            runCatching { toneGen?.startTone(ToneGenerator.TONE_SUP_ERROR, 260) }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isEventMode = activeSesi !in listOf("Pagi", "Sore", "Malam")
    MmqaModalDialog(title = "📷 Scan QR / Barcode Absensi", onDismiss = onDismiss) {
        Surface(
            color = if (isEventMode) Sky100 else Emerald50,
            border = BorderStroke(1.dp, if (isEventMode) Sky700.copy(alpha = 0.35f) else Emerald100),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = if (isEventMode) "🎯 Event Aktif: $activeSesi" else "📋 Sesi Aktif: Sesi $activeSesi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isEventMode) Sky900 else Emerald900
                )
                Text(
                    text = "Tanggal: $activeTanggal • Deteksi otomatis jam masuk, terlambat & notifikasi unik per santri.",
                    fontSize = 11.sp,
                    color = Slate700
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Cutoff time + Voice/Tone + Auto-WA Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = cutoffTimeHm,
                onValueChange = { cutoffTimeHm = it.take(5) },
                label = { Text("Batas Tepat Waktu (HH:mm)", fontSize = 10.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("barcode_cutoff_input")
            )
            Surface(
                color = if (soundAndVoiceEnabled) Emerald100 else Slate100,
                border = BorderStroke(1.dp, if (soundAndVoiceEnabled) Emerald700.copy(alpha = 0.4f) else Slate200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { soundAndVoiceEnabled = !soundAndVoiceEnabled }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (soundAndVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Suara Nama Santri",
                        tint = if (soundAndVoiceEnabled) Emerald900 else Slate500,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (soundAndVoiceEnabled) "Suara Aktif" else "Senyap",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (soundAndVoiceEnabled) Emerald900 else Slate700
                    )
                }
            }
            Surface(
                color = if (autoWaOnScan) Amber100 else Slate100,
                border = BorderStroke(1.dp, if (autoWaOnScan) Amber400 else Slate200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { autoWaOnScan = !autoWaOnScan }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = if (autoWaOnScan) "📲 Auto WA" else "WA Manual",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (autoWaOnScan) Amber950 else Slate700
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selectable Attendance Status for Scans
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Hadir", "Izin", "Sakit", "Alpha").forEach { st ->
                val active = selectedStatus == st
                val activeBg = when (st) {
                    "Hadir" -> Emerald700
                    "Izin" -> Amber500
                    "Sakit" -> Sky700
                    else -> Rose600
                }
                Surface(
                    color = if (active) activeBg else Slate100,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp),
                    onClick = { selectedStatus = st }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = st,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) Color.White else Slate700
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (hasCameraPerm) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Slate900)
            ) {
                CameraBarcodePreview(
                    lifecycleOwner = lifecycleOwner,
                    onCameraReady = { cam -> cameraRef = cam },
                    onBarcodeDetected = { raw -> handleRawCode(raw) },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder Reticle Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 210.dp, height = 126.dp)
                        .border(2.dp, Amber300, RoundedCornerShape(16.dp))
                )

                // Torch toggle button
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        cameraRef?.cameraControl?.enableTorch(torchOn)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Senter Kamera",
                        tint = Amber300
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Terscan sesi ini: $scanCount orang • Batas: $cutoffTimeHm WIB",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            Surface(
                color = Amber50,
                border = BorderStroke(1.dp, Amber200),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Amber900, modifier = Modifier.size(32.dp))
                    Text(
                        text = "Izin kamera diperlukan untuk memindai barcode secara langsung.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber950
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Izinkan Kamera", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        lastScannedFeedback?.let { fb ->
            Surface(
                color = fb.accentBg,
                border = BorderStroke(2.dp, fb.accentBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fb.card.nama,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = fb.accentText,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = fb.badgeLabel,
                            bgColor = if (fb.timing.terlambat) Rose600 else fb.accentBorder,
                            textColor = Color.White
                        )
                    }
                    Text(
                        text = "${fb.card.roleLabel} (${fb.card.nomorIdentitas}) • ${fb.card.primaryDetailValue}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "🕒 Jam Scan: ${fb.timing.waktuScan} WIB" +
                            (if (fb.timing.batasWaktu.isNotBlank()) " • Batas: ${fb.timing.batasWaktu}" else "") +
                            " • ${fb.card.secondaryDetailLabel}: ${fb.card.secondaryDetailValue}",
                        fontSize = 10.sp,
                        color = Slate700
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { triggerQuickWaForCard(fb.card, fb.status, fb.timing) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Emerald900, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (fb.card.tipe == "santri") "Kirim WA Wali" else "Kirim WA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Emerald900
                            )
                        }
                        if (fb.card.tipe == "santri") {
                            val muallim = db.muallim.firstOrNull { it.firqohId == fb.card.firqohId }
                            if (muallim != null && muallim.hp.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val txt = MmqaReportFormatters.buildSingleScanControllingWa(
                                            card = fb.card,
                                            tanggal = activeTanggal,
                                            sesi = activeSesi,
                                            status = fb.status,
                                            timing = fb.timing
                                        )
                                        openWhatsAppDirect(context, muallim.hp, txt, "Info ke Mu'allim")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Amber400),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                ) {
                                    Text("WA Mu'allim", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Amber950)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        lastScanError?.let { err ->
            Surface(
                color = Rose50,
                border = BorderStroke(1.dp, Rose200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ $err",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Rose800,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Manual / USB Barcode Scanner Input Fallback
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = manualCode,
                onValueChange = { manualCode = it },
                label = { Text("Input Kode / NIS / NIY / Scanner USB", fontSize = 11.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    handleRawCode(manualCode)
                    manualCode = ""
                }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("barcode_manual_input")
            )
            Button(
                onClick = {
                    handleRawCode(manualCode)
                    manualCode = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Proses Kode", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraBarcodePreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCameraReady: (Camera) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnDetected by rememberUpdatedState(onBarcodeDetected)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            runCatching { cameraExecutor.shutdown() }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val firstVal = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                            if (!firstVal.isNullOrBlank()) {
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    currentOnDetected(firstVal)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzer
                    )
                    onCameraReady(camera)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}
