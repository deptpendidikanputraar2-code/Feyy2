package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.*
import com.example.update.AppUpdateState
import com.example.update.AppVersionManifest
import java.util.Locale

private fun formatDownloadMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(Locale.US, "%.2f MB", mb)
    else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onStartDownload: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onInstallNow: () -> Unit,
    onRetryCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeManifest: AppVersionManifest? = when (state) {
        is AppUpdateState.UpdateAvailable -> state.manifest
        is AppUpdateState.Downloading -> state.manifest
        is AppUpdateState.ReadyToInstall -> state.manifest
        is AppUpdateState.NeedsInstallPermission -> state.manifest
        else -> null
    }
    val isForceUpdate = activeManifest?.forceUpdate == true
    val isDownloading = state is AppUpdateState.Downloading
    val canDismiss = !isForceUpdate && !isDownloading

    if (!canDismiss) {
        BackHandler(enabled = true) { /* Block dismissing mandatory update or active download */ }
    }

    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier
                .padding(18.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Column {
                // Header banner styled like an official Play Store update sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Emerald900, Emerald700)))
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(2.dp, Amber300),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mmqa_logo),
                                contentDescription = "Logo MMQA AR2",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (state) {
                                    is AppUpdateState.Checking -> "Memeriksa Pembaruan..."
                                    is AppUpdateState.UpToDate -> "Aplikasi Sudah Terbaru"
                                    is AppUpdateState.Downloading -> "Mengunduh Pembaruan..."
                                    is AppUpdateState.NeedsInstallPermission -> "Izin Instalasi Diperlukan"
                                    is AppUpdateState.ReadyToInstall -> "Siap Dipasang"
                                    is AppUpdateState.Error -> "Gagal Memeriksa Pembaruan"
                                    else -> "Pembaruan Tersedia"
                                },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeManifest?.let {
                                    "MMQA AR2 • Versi ${it.versionName} (Build ${it.versionCode})"
                                } ?: "MMQA Al Rifa'i 02",
                                color = Emerald100,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = when (state) {
                                is AppUpdateState.UpToDate -> Icons.Filled.CheckCircle
                                is AppUpdateState.Error -> Icons.Filled.ErrorOutline
                                is AppUpdateState.Downloading -> Icons.Filled.CloudDownload
                                is AppUpdateState.ReadyToInstall -> Icons.Filled.InstallMobile
                                else -> Icons.Filled.SystemUpdate
                            },
                            contentDescription = "Status Pembaruan",
                            tint = Amber300,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    when (state) {
                        is AppUpdateState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Emerald800,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = "Menghubungi server version.json untuk memeriksa rilis terbaru...",
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                            }
                        }

                        is AppUpdateState.UpToDate -> {
                            Surface(
                                color = Emerald50,
                                border = BorderStroke(1.dp, Emerald100),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "MMQA AR2 Anda sudah menggunakan versi terbaru.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald900
                                    )
                                    Text(
                                        text = "Versi terpasang: v${state.currentVersionName} (versionCode ${state.currentVersionCode})",
                                        fontSize = 11.sp,
                                        color = Slate700
                                    )
                                }
                            }
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("update_dialog_close_btn")
                            ) {
                                Text("Tutup", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        is AppUpdateState.UpdateAvailable -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = "Saat ini: v${state.currentVersionName} (${state.currentVersionCode})",
                                    bgColor = Slate100,
                                    textColor = Slate700
                                )
                                StatusPill(
                                    text = "Baru: v${state.manifest.versionName} (${state.manifest.versionCode})",
                                    bgColor = Emerald100,
                                    textColor = Emerald900
                                )
                            }

                            if (state.manifest.releaseDate.isNotBlank()) {
                                Text(
                                    text = "Tanggal rilis: ${state.manifest.releaseDate}",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (state.manifest.forceUpdate) {
                                Surface(
                                    color = Amber50,
                                    border = BorderStroke(1.dp, Amber400),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Pembaruan ini bersifat wajib agar seluruh fitur dan sinkronisasi data tetap berjalan lancar.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber950,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            ChangelogCard(state.manifest.changelog)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (!state.manifest.forceUpdate) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Slate300),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("update_later_btn")
                                    ) {
                                        Text("Nanti", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                    }
                                }
                                Button(
                                    onClick = onStartDownload,
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_download_now_btn")
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Perbarui", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        is AppUpdateState.Downloading -> {
                            val pctText = if (state.progressFraction >= 0f) {
                                "${(state.progressFraction * 100f).toInt()}%"
                            } else {
                                "Mengunduh..."
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (state.totalBytes > 0L) {
                                        "${formatDownloadMb(state.downloadedBytes)} / ${formatDownloadMb(state.totalBytes)}"
                                    } else {
                                        "Terunduh: ${formatDownloadMb(state.downloadedBytes)}"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = pctText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Emerald800
                                )
                            }
                            if (state.progressFraction >= 0f) {
                                LinearProgressIndicator(
                                    progress = { state.progressFraction.coerceIn(0f, 1f) },
                                    color = Emerald800,
                                    trackColor = Emerald100,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = Emerald800,
                                    trackColor = Emerald100,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                            Text(
                                text = "Mohon tunggu, berkas APK sedang diunduh dan diverifikasi keamanannya...",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }

                        is AppUpdateState.NeedsInstallPermission -> {
                            Surface(
                                color = Amber50,
                                border = BorderStroke(1.dp, Amber400),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = Amber900,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "APK v${state.manifest.versionName} sudah siap! Android memerlukan izin \"Instal aplikasi tidak dikenal\" untuk MMQA AR2 agar dapat membuka Package Installer sistem.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Amber950
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (!state.manifest.forceUpdate) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Batal", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                    }
                                }
                                Button(
                                    onClick = onOpenInstallPermissionSettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Amber950),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_grant_install_perm_btn")
                                ) {
                                    Text("Buka Izin Instalasi", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        is AppUpdateState.ReadyToInstall -> {
                            Surface(
                                color = Emerald50,
                                border = BorderStroke(1.dp, Emerald100),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Berkas MMQA AR2 v${state.manifest.versionName} selesai diunduh dan lolos verifikasi. Ketuk Pasang Sekarang untuk membuka pemasang paket Android.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald900,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (!state.manifest.forceUpdate) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("Nanti", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                    }
                                }
                                Button(
                                    onClick = onInstallNow,
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_install_now_btn")
                                ) {
                                    Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pasang Sekarang", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        is AppUpdateState.Error -> {
                            Surface(
                                color = Rose50,
                                border = BorderStroke(1.dp, Rose200),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Rose800,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Tutup", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                }
                                Button(
                                    onClick = onRetryCheck,
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("update_retry_btn")
                                ) {
                                    Text("Coba Lagi", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        AppUpdateState.Idle -> {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Tutup", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogCard(items: List<String>) {
    Surface(
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "Yang Baru di Versi Ini",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900
            )
            if (items.isEmpty()) {
                Text(
                    text = "• Peningkatan stabilitas dan perbaikan performa aplikasi.",
                    fontSize = 11.sp,
                    color = Slate700
                )
            } else {
                items.forEach { line ->
                    Text(
                        text = "• $line",
                        fontSize = 11.sp,
                        color = Slate700,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
