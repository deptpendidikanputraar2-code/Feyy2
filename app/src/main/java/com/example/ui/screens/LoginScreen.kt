package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MmqaDatabase
import com.example.data.SyncBannerState
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.components.MmqaLogoImage
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@Composable
fun LoginScreen(
    db: MmqaDatabase,
    syncState: SyncBannerState,
    loginError: String?,
    isWideScreen: Boolean,
    firebaseUser: FirebaseUser?,
    onGoogleSignInSuccess: () -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onGoogleSignOut: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val heroGradient = Brush.verticalGradient(listOf(Emerald950, Emerald900, Slate900))

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(heroGradient)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp)
            ) {
                LoginBrandingPanel(db = db)
            }
            Surface(
                color = Slate50,
                shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp)
                ) {
                    LoginFormCard(
                        username = username,
                        password = password,
                        showPassword = showPassword,
                        syncState = syncState,
                        loginError = loginError,
                        firebaseUser = firebaseUser,
                        onGoogleSignInSuccess = onGoogleSignInSuccess,
                        onGoogleSignInError = onGoogleSignInError,
                        onGoogleSignOut = onGoogleSignOut,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                        onTogglePwd = { showPassword = !showPassword },
                        onSubmit = { onLogin(username, password) },
                        modifier = Modifier.widthIn(max = 440.dp)
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(heroGradient)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                LoginBrandingPanel(db = db)
            }
            Surface(
                color = Slate50,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 460.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 26.dp)
                        .navigationBarsPadding()
                ) {
                    LoginFormCard(
                        username = username,
                        password = password,
                        showPassword = showPassword,
                        syncState = syncState,
                        loginError = loginError,
                        firebaseUser = firebaseUser,
                        onGoogleSignInSuccess = onGoogleSignInSuccess,
                        onGoogleSignInError = onGoogleSignInError,
                        onGoogleSignOut = onGoogleSignOut,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                        onTogglePwd = { showPassword = !showPassword },
                        onSubmit = { onLogin(username, password) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginBrandingPanel(db: MmqaDatabase) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MmqaLogoImage(size = 64.dp)
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = Amber400.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Amber300.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "SISTEM TERPADU MULTI-USER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber300,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "MMQA AL RIFAI 02",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Majelis Musyawarah Qira'atil Kutub & Al-Qur'an",
                    fontSize = 12.sp,
                    color = Emerald100.copy(alpha = 0.9f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Manajemen Akademik, Absensi & Kesekretariatan Dalam Satu Genggaman.",
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "Aplikasi resmi Dewan Harian MMQA dan Mu'allim Firqoh dengan sinkronisasi otomatis real-time: presensi bertingkat, dashboard kehadiran, ujian, keuangan syahriah, surat menyurat, inventaris & grup chat majelis.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Emerald100.copy(alpha = 0.85f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LoginStatBox("TOTAL SANTRI", db.santri.size.toString(), Amber300, Modifier.weight(1f))
            LoginStatBox("MU'ALLIM", db.muallim.size.toString(), Color.White, Modifier.weight(1f))
            LoginStatBox("FIRQOH", db.firqoh.size.toString(), Emerald100, Modifier.weight(1f))
        }

        Text(
            text = "© 2026 MMQA Al Rifai 02 • AR2 Digital",
            fontSize = 11.sp,
            color = Emerald100.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun LoginStatBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Emerald100)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = valueColor)
        }
    }
}

@Composable
private fun LoginFormCard(
    username: String,
    password: String,
    showPassword: Boolean,
    syncState: SyncBannerState,
    loginError: String?,
    firebaseUser: FirebaseUser?,
    onGoogleSignInSuccess: () -> Unit,
    onGoogleSignInError: (String) -> Unit,
    onGoogleSignOut: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePwd: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusBg, statusTxt) = when (syncState) {
        is SyncBannerState.Offline -> Triple("○ Offline", Amber100, Amber900)
        is SyncBannerState.Syncing -> Triple("⏳ Sinkron...", Amber100, Amber900)
        else -> Triple("● Online", Emerald100, Emerald900)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill("Portal Masuk Pengguna", Emerald100, Emerald900)
                StatusPill(statusLabel, statusBg, statusTxt)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Assalamu'alaikum Wr. Wb.",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Slate900
            )
            Text(
                text = if (firebaseUser == null) {
                    "Masuk dengan akun Google Anda untuk mengaktifkan sesi dan notifikasi otomatis."
                } else {
                    "Silakan masuk menggunakan username dan kata sandi akun MMQA Anda."
                },
                fontSize = 12.sp,
                color = Slate500
            )
        }

        if (firebaseUser == null) {
            Surface(
                color = Color.White,
                border = BorderStroke(1.dp, Emerald100),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔐 Verifikasi Akun Google",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Emerald950
                    )
                    Text(
                        text = "Masuk dengan Google agar notifikasi chat majelis dan sinkronisasi data antar perangkat berjalan otomatis.",
                        fontSize = 12.sp,
                        color = Slate700
                    )
                    GoogleSignInButton(
                        onAuthSuccess = onGoogleSignInSuccess,
                        onAuthError = onGoogleSignInError,
                        label = "Sign in with Google"
                    )
                }
            }
        } else {
            Surface(
                color = Emerald50,
                border = BorderStroke(1.dp, Emerald100),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Akun Google: ${firebaseUser.displayName ?: "Terhubung"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald950
                        )
                        Text(
                            text = firebaseUser.email ?: firebaseUser.uid,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Emerald800
                        )
                    }
                    TextButton(onClick = onGoogleSignOut) {
                        Text("Ganti", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose700)
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username", fontSize = 12.sp) },
                placeholder = { Text("Masukkan username Anda...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_username_input")
            )

            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kata Sandi / Password", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Text(
                        text = if (showPassword) "Sembunyikan" else "Tampilkan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald800,
                        modifier = Modifier
                            .clickable(onClick = onTogglePwd)
                            .padding(4.dp)
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = { Text("Masukkan password...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )
            }

            if (!loginError.isNullOrBlank()) {
                Surface(
                    color = Rose50,
                    border = BorderStroke(1.dp, Rose200),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = loginError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Rose800,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_button")
            ) {
                Text(
                    text = "Masuk ke Aplikasi MMQA AR2 →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = "Hubungi Admin Dewan Harian apabila Anda belum memiliki akun atau lupa kata sandi.",
                fontSize = 11.sp,
                color = Slate500
            )
        }
    }
}
