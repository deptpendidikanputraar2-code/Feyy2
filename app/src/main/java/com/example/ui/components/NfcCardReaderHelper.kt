package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.lang.ref.WeakReference

data class NfcTagEvent(
    val uidHex: String,
    val techLabel: String,
    val isMifareClassic1k: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

object NfcCardReaderHelper {

    /**
     * Optimized exclusive NFC reader flags:
     * - Covers NFC-A (MIFARE Classic 1k / ISO-DEP), NFC-B, and NFC-F (FeliCa) to block external
     *   balance-check apps while avoiding slow Kovio NFC-Barcode and ISO-15693 (NFC-V) RF poll stalls.
     * - Skips NDEF parsing and platform sounds for instant tag UID delivery.
     */
    const val EXCLUSIVE_READER_FLAGS: Int =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

    private const val REARM_DEBOUNCE_MS = 400L

    private var activeActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var isReaderModeArmed: Boolean = false

    @Volatile
    private var lastArmedAtMs: Long = 0L

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        onTagDiscovered(tag)
    }

    private val _events = MutableSharedFlow<NfcTagEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<NfcTagEvent> = _events.asSharedFlow()

    @Synchronized
    fun enableExclusiveReaderMode(activity: Activity, forceRearm: Boolean = false) {
        runCatching {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
            if (!adapter.isEnabled) {
                isReaderModeArmed = false
                return
            }
            val now = System.currentTimeMillis()
            // Debounce rapid duplicate calls (e.g. onResume + immediate onWindowFocusChanged)
            // without permanently locking out re-arming after dialogs or RF stalls.
            if (!forceRearm &&
                isReaderModeArmed &&
                activeActivityRef?.get() === activity &&
                (now - lastArmedAtMs) < REARM_DEBOUNCE_MS
            ) {
                return
            }
            // Pass null extras so the OEM NFC HAL uses its safe native presence-check timing
            // and never stalls after the first MIFARE Classic / NFC-A tag.
            adapter.enableReaderMode(
                activity,
                readerCallback,
                EXCLUSIVE_READER_FLAGS,
                null
            )
            activeActivityRef = WeakReference(activity)
            isReaderModeArmed = true
            lastArmedAtMs = now
        }
    }

    fun enableExclusiveReaderModeForContext(context: Context, forceRearm: Boolean = false) {
        var cur: Context? = context
        while (cur is android.content.ContextWrapper) {
            if (cur is Activity) {
                enableExclusiveReaderMode(cur, forceRearm = forceRearm)
                return
            }
            cur = cur.baseContext
        }
        if (cur is Activity) {
            enableExclusiveReaderMode(cur, forceRearm = forceRearm)
        }
    }

    @Synchronized
    fun disableReaderMode(activity: Activity) {
        isReaderModeArmed = false
        lastArmedAtMs = 0L
        activeActivityRef = null
        runCatching {
            NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        }
    }

    @Synchronized
    fun onAdapterDisabled() {
        isReaderModeArmed = false
        lastArmedAtMs = 0L
    }

    /**
     * Converts raw NFC tag UID bytes (e.g. from NXP MIFARE Classic 1k)
     * into uppercase hexadecimal string without colons (e.g. "F722FF90").
     */
    fun formatUidHex(idBytes: ByteArray?): String {
        if (idBytes == null || idBytes.isEmpty()) return ""
        return idBytes.joinToString(separator = "") { b ->
            "%02X".format(b.toInt() and 0xFF)
        }
    }

    /**
     * Normalizes any UID string (removes colons, dashes, spaces; uppercases).
     */
    fun normalizeUidHex(raw: String): String {
        return raw.trim()
            .replace(":", "")
            .replace("-", "")
            .replace(" ", "")
            .uppercase()
    }

    fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        runCatching {
            val uidHex = formatUidHex(tag.id)
            if (uidHex.isBlank()) return

            // Inspect cached techList directly without blocking Binder IPC so UID emits instantaneously
            val techs = tag.techList
            var hasMifareClassic = false
            var hasIsoDep = false
            var hasNfcA = false
            var hasNfcB = false
            var hasNfcF = false
            if (techs != null) {
                for (tech in techs) {
                    when {
                        tech.endsWith("MifareClassic") -> hasMifareClassic = true
                        tech.endsWith("IsoDep") -> hasIsoDep = true
                        tech.endsWith("NfcA") -> hasNfcA = true
                        tech.endsWith("NfcB") -> hasNfcB = true
                        tech.endsWith("NfcF") -> hasNfcF = true
                    }
                }
            }

            val techLabel = when {
                hasMifareClassic -> "MIFARE Classic 1K"
                hasIsoDep -> "ISO-DEP / Smartcard"
                hasNfcA -> "ISO 14443-A (NfcA)"
                hasNfcB -> "ISO 14443-B (NfcB)"
                hasNfcF -> "FeliCa (NfcF)"
                else -> "NFC Tag"
            }

            _events.tryEmit(
                NfcTagEvent(
                    uidHex = uidHex,
                    techLabel = techLabel,
                    isMifareClassic1k = hasMifareClassic,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun getNfcStatus(context: Context): Pair<Boolean, String> {
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        return when {
            adapter == null -> false to "HP ini tidak memiliki sensor NFC (Kamera Barcode tetap aktif)"
            !adapter.isEnabled -> false to "Sensor NFC nonaktif — aktifkan NFC di Pengaturan HP"
            else -> true to "NFC Reader Eksklusif Aktif (MIFARE Classic 1k)"
        }
    }

    fun openOtherNfcServicesSettings(context: Context) {
        val candidates = listOf(
            android.content.Intent(android.provider.Settings.ACTION_NFC_PAYMENT_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }

    @Suppress("DEPRECATION")
    fun triggerHapticFeedback(context: Context, isSuccess: Boolean) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isSuccess) {
                    vibrator.vibrate(VibrationEffect.createOneShot(75L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0L, 65L, 55L, 95L), -1)
                    )
                }
            } else {
                if (isSuccess) {
                    vibrator.vibrate(75L)
                } else {
                    vibrator.vibrate(longArrayOf(0L, 65L, 55L, 95L), -1)
                }
            }
        }
    }
}
