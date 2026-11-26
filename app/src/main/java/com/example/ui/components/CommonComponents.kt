package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.BiometricCapability
import com.example.data.SecuritySettings
import com.example.data.UserAccount
import com.example.ui.notifications.NotificationUiState
import com.example.ui.theme.*

fun decodeBase64ImageBitmap(dataOrBase64: String): ImageBitmap? {
    if (dataOrBase64.isBlank()) return null
    return runCatching {
        val pureB64 = dataOrBase64.substringAfter("base64,", dataOrBase64).trim()
        if (pureB64.isBlank()) return null
        val bytes = Base64.decode(pureB64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

@Composable
fun UserAvatarImage(
    name: String,
    avatarBase64: String,
    size: Dp = 40.dp,
    bgColor: Color = Emerald800,
    textColor: Color = Amber300,
    borderColor: Color = Amber300.copy(alpha = 0.55f),
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarBase64) { decodeBase64ImageBitmap(avatarBase64) }
    val initials = remember(name) {
        name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "U" }
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.size(size)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto profil $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = initials,
                    color = textColor,
                    fontSize = (size.value * 0.36f).sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MmqaLogoImage(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.mmqa_logo),
        contentDescription = "Logo MMQA Al Rifai 02",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
fun StatusPill(
    text: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

fun pctBadgeColors(pct: Int): Triple<Color, Color, Color> = when {
    pct >= 85 -> Triple(Emerald100, Emerald900, Emerald700.copy(alpha = 0.25f))
    pct >= 70 -> Triple(Amber100, Amber900, Amber400.copy(alpha = 0.45f))
    else -> Triple(Rose100, Rose800, Rose200)
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    borderColor: Color = Slate200,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun StatMetricCard(
    title: String,
    value: String,
    subtitle: String,
    iconEmoji: String,
    valueColor: Color = Emerald900,
    iconBg: Color = Emerald100,
    highlightGradient: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = if (highlightGradient) null else BorderStroke(1.dp, Slate200),
        shadowElevation = if (highlightGradient) 4.dp else 1.dp,
        color = Color.Transparent,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (highlightGradient) {
                        Brush.linearGradient(listOf(Emerald900, Emerald800))
                    } else {
                        Brush.linearGradient(listOf(Color.White, Color.White))
                    }
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (highlightGradient) Emerald100 else Slate500,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (highlightGradient) Color.White.copy(alpha = 0.15f) else iconBg)
                    ) {
                        Text(text = iconEmoji, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (highlightGradient) Amber300 else valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (highlightGradient) Emerald100.copy(alpha = 0.9f) else Slate500,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SubmenuTabsRow(
    items: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        items.forEach { (id, label) ->
            val active = id == selectedId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (active) Emerald900 else Color.White,
                border = if (active) null else BorderStroke(1.dp, Slate200),
                shadowElevation = if (active) 3.dp else 0.dp,
                modifier = Modifier
                    .defaultMinSize(minHeight = 42.dp)
                    .clickable { onSelect(id) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (active) Amber300 else Slate700
                    )
                }
            }
        }
    }
}

@Composable
fun <T> CompactDropdownSelector(
    label: String,
    selectedText: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Box {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate300),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable { expanded = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = selectedText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Pilih",
                        tint = Slate700
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MmqaModalDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Emerald900, Emerald800)))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onDismiss)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    MmqaModalDialog(title = title, onDismiss = onDismiss) {
        Text(text = message, fontSize = 13.sp, color = Slate700)
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Slate700, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ya, Lanjutkan", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    user: UserAccount,
    ruleSummary: String,
    draftAvatarBase64: String,
    onPickAvatarPhoto: () -> Unit,
    onClearAvatar: () -> Unit,
    onSaveProfile: (newName: String, newAvatarBase64: String) -> Boolean,
    onChangePassword: (oldPwd: String, newPwd: String, confirmPwd: String) -> Boolean,
    onDismiss: () -> Unit,
    securitySettings: SecuritySettings = SecuritySettings(),
    biometricCapability: BiometricCapability = BiometricCapability.UNAVAILABLE,
    onToggleBiometricLock: (Boolean) -> Unit = {},
    onToggleBiometricQuickLogin: (Boolean) -> Unit = {},
    onLockNow: () -> Unit = {}
) {
    var activeTab by remember { mutableStateOf("profil") }
    var nameInput by remember(user.id) { mutableStateOf(user.name) }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }

    MmqaModalDialog(title = "👤 Profil & Keamanan Akun", onDismiss = onDismiss) {
        SubmenuTabsRow(
            items = listOf(
                "profil" to "Profil & Foto",
                "password" to "Password",
                "keamanan" to "Biometrik & Enkripsi"
            ),
            selectedId = activeTab,
            onSelect = { activeTab = it }
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (activeTab == "profil") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatarImage(
                        name = nameInput.ifBlank { user.name },
                        avatarBase64 = draftAvatarBase64,
                        size = 88.dp,
                        borderColor = Emerald700,
                        modifier = Modifier
                            .clickable(onClick = onPickAvatarPhoto)
                            .testTag("profile_avatar_picker_trigger")
                    )
                    Surface(
                        color = Amber400,
                        shape = CircleShape,
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(onClick = onPickAvatarPhoto)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Ganti Foto Profil",
                                tint = Emerald950,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickAvatarPhoto,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("pick_profile_photo_button")
                    ) {
                        Text("📷 Pilih Foto Profil", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald900)
                    }
                    if (draftAvatarBase64.isNotBlank()) {
                        TextButton(onClick = onClearAvatar) {
                            Text("Hapus Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column {
                        Text("@${user.username}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(user.jabatan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                    }
                    StatusPill(
                        text = if (user.role == "dewan_harian") "DEWAN HARIAN" else "MU'ALLIM",
                        bgColor = if (user.role == "dewan_harian") Emerald100 else Amber100,
                        textColor = if (user.role == "dewan_harian") Emerald900 else Amber900
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Nama Lengkap Tampilan", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_profile_name_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Batal", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (onSaveProfile(nameInput, draftAvatarBase64)) onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Simpan Profil", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        } else if (activeTab == "password") {
            Surface(
                color = Amber50,
                border = BorderStroke(1.dp, Amber200),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Aturan Password Aktif: $ruleSummary",
                    fontSize = 11.sp,
                    color = Amber900,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = oldPwd,
                onValueChange = { oldPwd = it },
                label = { Text("Password Saat Ini", fontSize = 12.sp) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = newPwd,
                onValueChange = { newPwd = it },
                label = { Text("Password Baru", fontSize = 12.sp) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPwd,
                onValueChange = { confirmPwd = it },
                label = { Text("Konfirmasi Password Baru", fontSize = 12.sp) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Batal", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (onChangePassword(oldPwd, newPwd, confirmPwd)) onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_own_password_button")
                ) {
                    Text("Simpan Password Baru", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        } else {
            Surface(
                color = Emerald50,
                border = BorderStroke(1.dp, Emerald100),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (securitySettings.keystoreBacked) "🛡️ Enkripsi Hardware Keystore AES-256-GCM Aktif" else "🛡️ Enkripsi Lokal AES-256-GCM Aktif",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Emerald950
                    )
                    Text(
                        text = "Database lokal offline, data akun & antrean sinkronisasi tersimpan terenkripsi otomatis.",
                        fontSize = 11.sp,
                        color = Slate700
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (biometricCapability != BiometricCapability.AVAILABLE) {
                Surface(color = Amber50, border = BorderStroke(1.dp, Amber200), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ℹ️ Aktifkan sidik jari, pengenalan wajah, atau kunci layar di Pengaturan HP Anda untuk memakai fitur biometrik.",
                        fontSize = 11.sp,
                        color = Amber950,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Surface(color = Slate50, border = BorderStroke(1.dp, Slate200), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kunci Biometrik Aplikasi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text("Minta sidik jari / wajah / PIN saat membuka aplikasi", fontSize = 10.sp, color = Slate500)
                        }
                        Switch(
                            checked = securitySettings.biometricLockEnabled,
                            onCheckedChange = onToggleBiometricLock
                        )
                    }
                    HorizontalDivider(color = Slate200)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Masuk Cepat Biometrik", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text("Login ke @${user.username} langsung pakai biometrik", fontSize = 10.sp, color = Slate500)
                        }
                        Switch(
                            checked = securitySettings.biometricQuickLoginEnabled,
                            onCheckedChange = onToggleBiometricQuickLogin
                        )
                    }
                }
            }
            if (securitySettings.biometricLockEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onDismiss()
                        onLockNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔒 Kunci Aplikasi Sekarang", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun NotificationInboxDialog(
    notificationsState: NotificationUiState,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MmqaModalDialog(title = "🔔 Notifikasi Majelis", onDismiss = onDismiss) {
        if (!hasNotificationPermission) {
            Surface(
                color = Amber50,
                border = BorderStroke(1.dp, Amber200),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Izinkan notifikasi agar pesan chat baru muncul otomatis di bar Android.",
                        fontSize = 11.sp,
                        color = Amber900,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Emerald950),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Izinkan", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (notificationsState) {
            is NotificationUiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    CircularProgressIndicator(color = Emerald800)
                }
            }
            is NotificationUiState.Error -> {
                Surface(
                    color = Rose50,
                    border = BorderStroke(1.dp, Rose200),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notificationsState.message,
                        fontSize = 12.sp,
                        color = Rose800,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            is NotificationUiState.Success -> {
                val items = notificationsState.items
                if (items.isEmpty()) {
                    Surface(
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text("📭 Belum ada notifikasi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Pesan chat baru atau pengumuman majelis akan muncul otomatis di sini.",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { item ->
                            Surface(
                                color = if (item.read) Color.White else Emerald50,
                                border = BorderStroke(1.dp, if (item.read) Slate200 else Emerald100),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            StatusPill(
                                                text = item.category.uppercase(),
                                                bgColor = if (item.read) Slate100 else Emerald100,
                                                textColor = if (item.read) Slate700 else Emerald900
                                            )
                                            if (!item.read) {
                                                StatusPill("BARU", Amber100, Amber900)
                                            }
                                        }
                                        Text(
                                            text = item.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = item.body,
                                            fontSize = 11.sp,
                                            color = Slate700
                                        )
                                    }
                                    Column {
                                        if (!item.read) {
                                            IconButton(
                                                onClick = { onMarkRead(item.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DoneAll,
                                                    contentDescription = "Tandai dibaca",
                                                    tint = Emerald800,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDelete(item.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Hapus notifikasi",
                                                tint = Rose600,
                                                modifier = Modifier.size(18.dp)
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
}

@Composable
fun WaPdfActionRow(
    onShareWa: () -> Unit,
    onDownloadPdf: () -> Unit,
    modifier: Modifier = Modifier,
    waLabel: String = "Kirim ke WA",
    pdfLabel: String = "Unduh PDF",
    waTestTag: String = "share_wa_button",
    pdfTestTag: String = "download_pdf_button"
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onShareWa,
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald700,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
                .testTag(waTestTag)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = waLabel,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = waLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onDownloadPdf,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Rose200),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Rose50,
                contentColor = Rose800
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
                .testTag(pdfTestTag)
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = pdfLabel,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = pdfLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun shareTextToWhatsApp(context: Context, text: String, title: String = "Kirim ke WhatsApp") {
    val pm = context.packageManager
    val waPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
    val targetPackage = waPackages.firstOrNull { pkg ->
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (targetPackage != null) setPackage(targetPackage)
    }
    runCatching {
        if (targetPackage != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent.createChooser(intent, title))
        }
    }.onFailure {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(fallback, title))
        }.onFailure {
            Toast.makeText(context, "Tidak ada aplikasi untuk membagikan pesan.", Toast.LENGTH_SHORT).show()
        }
    }
}

fun printHtmlDocument(context: Context, jobName: String, htmlBody: String) {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            val adapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, htmlBody, "text/html", "UTF-8", null)
}
