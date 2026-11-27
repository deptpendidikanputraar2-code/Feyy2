package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.MmqaDefaults
import com.example.data.MmqaJsonCodec
import com.example.update.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MMQA AR2", appName)
  }

  @Test
  fun `parse version json and file provider install intent`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rawJson = """
      {
        "versionCode": 2,
        "versionName": "1.1.0",
        "apkUrl": "https://cdn.example.com/releases/mmqa-v1.1.0.apk",
        "sha256": "ABCDEF123456",
        "forceUpdate": true,
        "releaseDate": "2026-11-27",
        "changelog": ["Perbaikan NFC", "Fitur OTA Update"]
      }
    """.trimIndent()
    val manifest = AppUpdateManager.parseVersionManifest(rawJson)
    assertEquals(2L, manifest.versionCode)
    assertEquals("1.1.0", manifest.versionName)
    assertEquals("https://cdn.example.com/releases/mmqa-v1.1.0.apk", manifest.apkUrl)
    assertEquals("abcdef123456", manifest.sha256)
    assertTrue(manifest.forceUpdate)
    assertEquals(2, manifest.changelog.size)

    val baseDb = MmqaDefaults.createDefaultDatabase()
    val withUrl = baseDb.copy(
      adminConfig = baseDb.adminConfig.copy(
        permissions = baseDb.adminConfig.permissions.copy(
          appUpdateVersionUrl = "https://cdn.example.com/mmqa/version.json"
        )
      )
    )
    val decoded = MmqaJsonCodec.decodeDatabase(MmqaJsonCodec.encodeDatabase(withUrl))
    assertEquals(
      "https://cdn.example.com/mmqa/version.json",
      decoded.adminConfig.permissions.appUpdateVersionUrl
    )

    val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
    val fakeApk = File(updatesDir, "mmqa_update_v2.apk").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
    val installIntent = AppUpdateManager.buildInstallApkIntent(context, fakeApk)
    assertEquals(Intent.ACTION_VIEW, installIntent.action)
    assertEquals("application/vnd.android.package-archive", installIntent.type)
    assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    assertTrue(installIntent.data.toString().contains("${context.packageName}.fileprovider"))
    fakeApk.delete()
  }
}
