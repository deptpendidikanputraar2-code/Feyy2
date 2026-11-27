package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppVersionManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String = "",
    val forceUpdate: Boolean = false,
    val releaseDate: String = "",
    val changelog: List<String> = emptyList()
)

sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data class UpToDate(
        val currentVersionCode: Long,
        val currentVersionName: String,
        val checkedAtMs: Long = System.currentTimeMillis()
    ) : AppUpdateState()

    data class UpdateAvailable(
        val currentVersionCode: Long,
        val currentVersionName: String,
        val manifest: AppVersionManifest
    ) : AppUpdateState()

    data class Downloading(
        val manifest: AppVersionManifest,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressFraction: Float
    ) : AppUpdateState()

    data class ReadyToInstall(
        val manifest: AppVersionManifest,
        val apkFile: File
    ) : AppUpdateState()

    data class NeedsInstallPermission(
        val manifest: AppVersionManifest,
        val apkFile: File
    ) : AppUpdateState()

    data class Error(val message: String) : AppUpdateState()
}

object AppUpdateManager {

    private const val UPDATES_SUBDIR = "updates"
    private const val MAX_MANIFEST_BYTES = 256L * 1024L // 256 KB
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L // 250 MB

    private val metadataClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getInstalledVersionInfo(context: Context): Pair<Long, String> {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
            val name = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            code to name
        }.getOrElse {
            BuildConfig.VERSION_CODE.toLong() to BuildConfig.VERSION_NAME
        }
    }

    fun isNewerVersion(currentVersionCode: Long, remoteVersionCode: Long): Boolean {
        return remoteVersionCode > currentVersionCode
    }

    /**
     * Validates that a remote update or APK URL uses HTTPS on a public host
     * (rejects HTTP, file://, javascript:, loopback, private LAN, and link-local hosts).
     * Returns null if valid, or a user-friendly Indonesian error message if invalid.
     */
    fun validateSecureHttpsUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return "URL pembaruan masih kosong."
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return "Format URL tidak valid."
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return "URL wajib menggunakan HTTPS yang aman (https://...)."
        }
        val host = uri.host?.trim()?.lowercase().orEmpty()
        if (host.isBlank() || !host.contains(".")) {
            return "Host URL tidak valid atau bukan domain publik."
        }
        if (isDisallowedPrivateHost(host)) {
            return "Host lokal/privat tidak diizinkan untuk sumber pembaruan."
        }
        return null
    }

    private fun isDisallowedPrivateHost(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]")
        if (clean == "localhost" || clean == "::1" || clean.endsWith(".local")) return true
        if (clean.contains(":")) {
            val lower = clean.lowercase()
            if (lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80") || lower == "::") {
                return true
            }
        }
        val ipv4Parts = clean.split(".")
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }) {
            val a = ipv4Parts[0].toInt()
            val b = ipv4Parts[1].toInt()
            if (a == 127 || a == 10 || a == 0) return true
            if (a == 169 && b == 254) return true
            if (a == 192 && b == 168) return true
            if (a == 172 && b in 16..31) return true
        }
        return false
    }

    fun parseVersionManifest(jsonRaw: String): AppVersionManifest {
        val obj = JSONObject(jsonRaw)
        val versionCode = obj.optLong("versionCode", -1L)
        if (versionCode <= 0L) {
            throw IllegalArgumentException("Berkas version.json tidak memiliki versionCode yang valid.")
        }
        val versionName = obj.optString("versionName", "").trim().ifBlank { "v$versionCode" }
        val apkUrl = obj.optString("apkUrl", obj.optString("downloadUrl", "")).trim()
        val urlErr = validateSecureHttpsUrl(apkUrl)
        if (urlErr != null) {
            throw IllegalArgumentException("apkUrl di version.json tidak valid: $urlErr")
        }
        val sha256 = obj.optString("sha256", "")
            .trim()
            .replace(" ", "")
            .replace(":", "")
            .lowercase()
        val forceUpdate = obj.optBoolean("forceUpdate", false)
        val releaseDate = obj.optString("releaseDate", "").trim()

        val changelogList = mutableListOf<String>()
        val arr = obj.optJSONArray("changelog")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotBlank()) changelogList.add(item)
            }
        }
        if (changelogList.isEmpty()) {
            val notes = obj.optString("releaseNotes", "").trim()
            if (notes.isNotBlank()) {
                notes.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    changelogList.add(it)
                }
            }
        }

        return AppVersionManifest(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            forceUpdate = forceUpdate,
            releaseDate = releaseDate,
            changelog = changelogList
        )
    }

    suspend fun checkForUpdate(
        context: Context,
        versionJsonUrl: String
    ): AppUpdateState = withContext(Dispatchers.IO) {
        val urlErr = validateSecureHttpsUrl(versionJsonUrl)
        if (urlErr != null) {
            return@withContext AppUpdateState.Error(urlErr)
        }
        val (currentCode, currentName) = getInstalledVersionInfo(context)
        try {
            val request = Request.Builder()
                .url(versionJsonUrl.trim())
                .header("Cache-Control", "no-cache")
                .header("Accept", "application/json")
                .get()
                .build()

            metadataClient.newCall(request).execute().use { response ->
                val finalUrlErr = validateSecureHttpsUrl(response.request.url.toString())
                if (finalUrlErr != null) {
                    return@withContext AppUpdateState.Error("Tujuan redirect version.json tidak aman: $finalUrlErr")
                }
                if (!response.isSuccessful) {
                    return@withContext AppUpdateState.Error(
                        "Gagal memuat version.json (HTTP ${response.code}). Periksa tautan server Anda."
                    )
                }
                val body = response.body
                    ?: return@withContext AppUpdateState.Error("Respon version.json kosong.")
                val declaredLen = body.contentLength()
                if (declaredLen > MAX_MANIFEST_BYTES) {
                    return@withContext AppUpdateState.Error("Ukuran berkas version.json terlalu besar.")
                }
                val rawText = body.string()
                if (rawText.length > MAX_MANIFEST_BYTES) {
                    return@withContext AppUpdateState.Error("Isi berkas version.json melebihi batas aman.")
                }

                val manifest = parseVersionManifest(rawText)
                if (isNewerVersion(currentCode, manifest.versionCode)) {
                    AppUpdateState.UpdateAvailable(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        manifest = manifest
                    )
                } else {
                    AppUpdateState.UpToDate(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName
                    )
                }
            }
        } catch (e: Exception) {
            AppUpdateState.Error(e.message ?: "Gagal memeriksa pembaruan aplikasi.")
        }
    }

    suspend fun downloadApk(
        context: Context,
        manifest: AppVersionManifest,
        onProgress: (downloadedBytes: Long, totalBytes: Long, progressFraction: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val urlErr = validateSecureHttpsUrl(manifest.apkUrl)
            if (urlErr != null) throw IllegalArgumentException(urlErr)

            val updatesDir = File(context.cacheDir, UPDATES_SUBDIR).apply {
                if (!exists()) mkdirs()
            }
            // Remove older cached APKs before downloading the latest version
            updatesDir.listFiles()?.forEach { old ->
                if (old.isFile && old.name.endsWith(".apk", ignoreCase = true)) {
                    runCatching { old.delete() }
                }
            }

            val safeFileName = "mmqa_update_v${manifest.versionCode}.apk"
            val targetFile = File(updatesDir, safeFileName)
            val baseCanonical = updatesDir.canonicalPath + File.separator
            if (!targetFile.canonicalPath.startsWith(baseCanonical)) {
                throw SecurityException("Lokasi penyimpanan APK tidak valid.")
            }

            val request = Request.Builder()
                .url(manifest.apkUrl)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            downloadClient.newCall(request).execute().use { response ->
                val finalUrlErr = validateSecureHttpsUrl(response.request.url.toString())
                if (finalUrlErr != null) {
                    throw SecurityException("Tujuan redirect unduhan APK tidak aman: $finalUrlErr")
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException("Gagal mengunduh file APK (HTTP ${response.code}).")
                }
                val body = response.body
                    ?: throw IllegalStateException("File APK dari server kosong.")
                val totalBytes = body.contentLength()
                if (totalBytes > MAX_APK_BYTES) {
                    throw IllegalStateException("Ukuran file APK melebihi batas maksimum 250 MB.")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloadedBytes = 0L
                val buffer = ByteArray(16 * 1024)
                var lastReportedMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            downloadedBytes += read
                            if (downloadedBytes > MAX_APK_BYTES) {
                                throw IllegalStateException("Ukuran unduhan APK melebihi batas maksimum 250 MB.")
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)

                            val now = System.currentTimeMillis()
                            if (now - lastReportedMs >= 100L || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                                lastReportedMs = now
                                val fraction = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                }
                                onProgress(downloadedBytes, totalBytes, fraction)
                            }
                        }
                        output.flush()
                    }
                }

                // Basic APK header sanity check (ZIP magic bytes: PK\x03\x04)
                if (!hasValidApkZipHeader(targetFile)) {
                    runCatching { targetFile.delete() }
                    throw IllegalStateException("Berkas yang diunduh bukan paket APK yang valid (periksa tautan apkUrl).")
                }

                // Optional SHA-256 integrity verification if specified in version.json
                if (manifest.sha256.isNotBlank()) {
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                    if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
                        runCatching { targetFile.delete() }
                        throw SecurityException("Verifikasi integritas SHA-256 APK gagal! File unduhan dibatalkan demi keamanan.")
                    }
                }

                // Verify APK package name matches this app so user is updating the right app
                val archivePkg = runCatching {
                    val pm = context.packageManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageArchiveInfo(
                            targetFile.absolutePath,
                            PackageManager.PackageInfoFlags.of(0)
                        )?.packageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageArchiveInfo(targetFile.absolutePath, 0)?.packageName
                    }
                }.getOrNull()

                if (archivePkg != null && archivePkg != context.packageName) {
                    runCatching { targetFile.delete() }
                    throw SecurityException(
                        "ID aplikasi APK ($archivePkg) tidak cocok dengan aplikasi ini (${context.packageName})."
                    )
                }

                onProgress(downloadedBytes, if (totalBytes > 0L) totalBytes else downloadedBytes, 1f)
                targetFile
            }
        }
    }

    private fun hasValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        val header = ByteArray(4)
        FileInputStream(file).use { fis ->
            val count = fis.read(header)
            if (count < 4) return false
        }
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun buildInstallApkIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun triggerSystemPackageInstaller(context: Context, apkFile: File): Result<Unit> {
        return runCatching {
            if (!apkFile.exists()) {
                throw IllegalStateException("File APK unduhan tidak ditemukan. Silakan unduh ulang.")
            }
            val intent = buildInstallApkIntent(context, apkFile)
            context.startActivity(intent)
        }
    }
}
