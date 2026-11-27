package com.example.ui.components

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NfcTagEvent(
    val uidHex: String,
    val techLabel: String,
    val isMifareClassic1k: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

object NfcCardReaderHelper {

    private val _events = MutableSharedFlow<NfcTagEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NfcTagEvent> = _events.asSharedFlow()

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
        val uidHex = formatUidHex(tag.id)
        if (uidHex.isBlank()) return

        val techs = tag.techList?.toList().orEmpty()
        val hasMifareClassic = techs.any { it.contains("MifareClassic", ignoreCase = true) }
        val is1k = if (hasMifareClassic) {
            runCatching {
                val mifare = MifareClassic.get(tag)
                mifare?.type == MifareClassic.TYPE_CLASSIC && mifare.size == MifareClassic.SIZE_1K
            }.getOrDefault(false)
        } else false

        val techLabel = when {
            is1k -> "MIFARE Classic 1K"
            hasMifareClassic -> "MIFARE Classic"
            techs.any { it.contains("NfcA", ignoreCase = true) } -> "ISO 14443-A (NfcA)"
            else -> "NFC Tag"
        }

        _events.tryEmit(
            NfcTagEvent(
                uidHex = uidHex,
                techLabel = techLabel,
                isMifareClassic1k = is1k || hasMifareClassic,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    fun getNfcStatus(context: Context): Pair<Boolean, String> {
        val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        return when {
            adapter == null -> false to "HP ini tidak memiliki sensor NFC (Kamera Barcode tetap aktif)"
            !adapter.isEnabled -> false to "Sensor NFC nonaktif — aktifkan NFC di Pengaturan HP"
            else -> true to "NFC Reader Aktif (Siap MIFARE Classic 1k)"
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
