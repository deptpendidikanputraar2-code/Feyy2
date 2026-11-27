package com.example.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Host-Based Card Emulation (HCE) service under CardEmulation.CATEGORY_OTHER
 * so Android registers MMQA AR2 under "Layanan NFC lainnya" (Other NFC services)
 * in device NFC settings.
 */
class MmqaNfcCardService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED
        }
        // Check ISO 7816-4 SELECT APDU header (00 A4 04 00)
        val isSelect = commandApdu[0] == 0x00.toByte() &&
            commandApdu[1] == 0xA4.toByte() &&
            commandApdu[2] == 0x04.toByte() &&
            commandApdu[3] == 0x00.toByte()
        if (isSelect) {
            val payload = "MMQAAR2-NFC".toByteArray(Charsets.UTF_8)
            return payload + STATUS_SUCCESS
        }
        return STATUS_UNSUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        // No persistent HCE session state required
    }

    companion object {
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_UNSUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }
}
