package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.example.data.MmqaDatabase
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerDialog(
    db: MmqaDatabase,
    activeTanggal: String,
    activeSesi: String,
    onScannedAttendance: (card: MmqaBarcodeCard, status: String) -> Unit,
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
    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var manualCode by remember { mutableStateOf("") }
    var lastScannedCard by remember { mutableStateOf<MmqaBarcodeCard?>(null) }
    var lastScanError by remember { mutableStateOf<String?>(null) }
    var scanCount by remember { mutableIntStateOf(0) }

    // Deduplication map to avoid repeated rapid firing of the same barcode
    val lastSeenTimestamps = remember { mutableMapOf<String, Long>() }

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
            lastScannedCard = card
            scanCount += 1
            onScannedAttendance(card, selectedStatus)
        } else {
            lastScanError = "Kode \"$trimmed\" tidak ditemukan di Data Induk MMQA."
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
                    text = "Tanggal: $activeTanggal • Arahkan kamera ke QR / Barcode Santri, Mu'allim, atau Dewan Harian.",
                    fontSize = 11.sp,
                    color = Slate700
                )
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
                    .height(230.dp)
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
                        .size(width = 210.dp, height = 132.dp)
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
                        text = "Terscan sesi ini: $scanCount orang",
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

        lastScannedCard?.let { card ->
            Surface(
                color = Emerald50,
                border = BorderStroke(1.dp, Emerald700.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "✅ ${card.nama} ditandai $selectedStatus",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Emerald900
                    )
                    Text(
                        text = "${card.roleLabel} • ${card.primaryDetailLabel}: ${card.primaryDetailValue}",
                        fontSize = 11.sp,
                        color = Emerald800
                    )
                    Text(
                        text = "${card.secondaryDetailLabel}: ${card.secondaryDetailValue}",
                        fontSize = 10.sp,
                        color = Slate700
                    )
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
