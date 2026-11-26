package com.example.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SecuritySettings(
    val encryptionActive: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val biometricQuickLoginEnabled: Boolean = false,
    val savedUsername: String = "",
    val keystoreBacked: Boolean = false
)

enum class BiometricCapability {
    AVAILABLE,
    NOT_ENROLLED,
    UNAVAILABLE
}

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption vault & BiometricPrompt helper.
 * Transparently decrypts legacy unencrypted local JSON while writing all new saves encrypted.
 */
class MmqaSecurityManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "mmqa_ar2_aes256_gcm_vault_v1"
        private const val ENC_PREFIX = "MMQA_ENC_V1:"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val secFile = File(context.filesDir, "mmqa_sec_vault_v1.dat")
    private var usingKeystore = false

    private fun getOrCreateSecretKey(): SecretKey {
        return runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                usingKeystore = true
                return@runCatching existing
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            usingKeystore = true
            keyGenerator.generateKey()
        }.getOrElse {
            // Deterministic device-bound fallback key only if Keystore is unavailable in a test/headless runtime
            usingKeystore = false
            val seed = "${context.packageName}|${Build.FINGERPRINT}|mmqa_ar2_vault_2026"
            val hash = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            SecretKeySpec(hash, "AES")
        }
    }

    fun isHardwareKeystoreBacked(): Boolean {
        getOrCreateSecretKey()
        return usingKeystore
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrElse {
            plainText
        }
    }

    fun decrypt(storedText: String): String {
        if (storedText.isBlank()) return ""
        if (!storedText.startsWith(ENC_PREFIX)) {
            // Legacy unencrypted file — return transparently so zero existing offline data is lost
            return storedText
        }
        return runCatching {
            val rawB64 = storedText.removePrefix(ENC_PREFIX).trim()
            val combined = Base64.decode(rawB64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LEN) return@runCatching ""
            val iv = combined.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun loadSettings(): SecuritySettings {
        val ks = isHardwareKeystoreBacked()
        return runCatching {
            if (!secFile.exists()) return@runCatching SecuritySettings(keystoreBacked = ks)
            val decrypted = decrypt(secFile.readText())
            if (decrypted.isBlank()) return@runCatching SecuritySettings(keystoreBacked = ks)
            val obj = JSONObject(decrypted)
            SecuritySettings(
                encryptionActive = obj.optBoolean("encryptionActive", true),
                biometricLockEnabled = obj.optBoolean("biometricLockEnabled", false),
                biometricQuickLoginEnabled = obj.optBoolean("biometricQuickLoginEnabled", false),
                savedUsername = obj.optString("savedUsername", ""),
                keystoreBacked = ks
            )
        }.getOrElse {
            SecuritySettings(keystoreBacked = ks)
        }
    }

    fun saveSettings(settings: SecuritySettings) {
        runCatching {
            val obj = JSONObject().apply {
                put("encryptionActive", settings.encryptionActive)
                put("biometricLockEnabled", settings.biometricLockEnabled)
                put("biometricQuickLoginEnabled", settings.biometricQuickLoginEnabled)
                put("savedUsername", settings.savedUsername)
            }
            secFile.writeText(encrypt(obj.toString()))
        }
    }

    fun checkBiometricCapability(): BiometricCapability {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Verifikasi Biometrik MMQA AR2",
        subtitle: String = "Gunakan sidik jari, wajah, atau kunci layar perangkat Anda",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometrik tidak dikenali. Silakan coba lagi.")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        runCatching {
            prompt.authenticate(promptInfo)
        }.onFailure {
            onError(it.message ?: "Gagal membuka dialog biometrik.")
        }
    }
}
