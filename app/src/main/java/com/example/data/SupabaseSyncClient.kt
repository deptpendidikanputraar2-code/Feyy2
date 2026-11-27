package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class SupabaseStatus(
    val configured: Boolean,
    val urlPreview: String,
    val connected: Boolean = false,
    val recordCount: Int = 0,
    val message: String = ""
)

data class SupabaseFetchPayload(
    val database: MmqaDatabase,
    val revision: String?,
    val recordCount: Int
)

class SupabaseSyncClient {
    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(6, 60, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private fun anonKey(): String = BuildConfig.SUPABASE_ANON_KEY.trim()

    private fun nowIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun Request.Builder.withSupabaseHeaders(): Request.Builder {
        val key = anonKey()
        header("apikey", key)
        if (key.startsWith("eyJ")) {
            header("Authorization", "Bearer $key")
        }
        return this
    }

    fun isConfigured(): Boolean {
        val u = baseUrl()
        val k = anonKey()
        return u.startsWith("https://") &&
            !u.contains("your-project-id", ignoreCase = true) &&
            k.isNotBlank() &&
            !k.contains("YOUR_SUPABASE_ANON_KEY", ignoreCase = true)
    }

    fun getStatusSummary(): SupabaseStatus {
        val configured = isConfigured()
        val preview = if (configured) baseUrl() else "Belum dikonfigurasi"
        return SupabaseStatus(
            configured = configured,
            urlPreview = preview,
            message = if (configured) "Sinkronisasi Cloud Aktif" else "Mode Lokal"
        )
    }

    suspend fun ping(): Result<SupabaseStatus> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Cloud database belum dikonfigurasi.")
            )
        }
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/rest/v1/mmqa_records?select=collection,id&limit=1000")
                .withSupabaseHeaders()
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${resp.code}: ${raw.take(180)}")
                    )
                }
                val arr = JSONArray(raw.ifBlank { "[]" })
                Result.success(
                    SupabaseStatus(
                        configured = true,
                        urlPreview = baseUrl(),
                        connected = true,
                        recordCount = arr.length(),
                        message = "Terhubung (${arr.length()} record)"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lightweight ~50 byte revision fingerprint (latest updated_at + exact row count)
     * so background polling only downloads full payload when another user changed data.
     */
    suspend fun fetchRevision(): Result<String?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase belum dikonfigurasi."))
        }
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/rest/v1/mmqa_records?select=updated_at,id&order=updated_at.desc&limit=1")
                .withSupabaseHeaders()
                .header("Prefer", "count=exact")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Revision error ${resp.code}: ${raw.take(120)}")
                    )
                }
                val rangeHeader = resp.header("Content-Range") ?: ""
                val totalCount = rangeHeader.substringAfter("/", "").toIntOrNull()
                val arr = JSONArray(raw.ifBlank { "[]" })
                val latestAt = if (arr.length() > 0) arr.optJSONObject(0)?.optString("updated_at", "") else ""
                val latestId = if (arr.length() > 0) arr.optJSONObject(0)?.optString("id", "") else ""
                if (totalCount == 0 && latestAt.isNullOrBlank()) {
                    Result.success("EMPTY")
                } else {
                    Result.success("${latestAt.orEmpty()}|${latestId.orEmpty()}|${totalCount ?: arr.length()}")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAll(base: MmqaDatabase): Result<SupabaseFetchPayload> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase belum dikonfigurasi."))
        }
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/rest/v1/mmqa_records?select=collection,id,payload,updated_at")
                .withSupabaseHeaders()
                .get()
                .build()
            val rows = client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Supabase GET error ${resp.code}: ${raw.take(180)}")
                }
                JSONArray(raw.ifBlank { "[]" })
            }

            if (rows.length() == 0) {
                return@withContext Result.success(SupabaseFetchPayload(base, "EMPTY", 0))
            }

            val grouped = mutableMapOf<String, JSONArray>()
            MmqaConstants.SHEET_NAMES.forEach { grouped[it] = JSONArray() }

            var maxUpdatedAt = ""
            var maxUpdatedId = ""
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val col = r.optString("collection", "")
                val payload = r.optJSONObject("payload") ?: continue
                val uAt = r.optString("updated_at", "")
                val id = r.optString("id", "")
                if (uAt > maxUpdatedAt) {
                    maxUpdatedAt = uAt
                    maxUpdatedId = id
                }
                grouped.getOrPut(col) { JSONArray() }.put(payload)
            }

            val combined = JSONObject()
            grouped.forEach { (col, arr) ->
                if (arr.length() > 0) combined.put(col, arr)
            }

            val merged = MmqaJsonCodec.mergeRemoteDataJson(base, combined)
            val rev = "$maxUpdatedAt|$maxUpdatedId|${rows.length()}"
            Result.success(SupabaseFetchPayload(merged, rev, rows.length()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fast delta push: only upserts changed records and explicitly deletes removed records.
     */
    suspend fun pushMutations(
        db: MmqaDatabase,
        mutations: List<PendingMutation>,
        actor: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase belum dikonfigurasi."))
        }
        if (mutations.isEmpty()) return@withContext Result.success(0)

        try {
            val payloadObj = MmqaJsonCodec.encodePayload(db)
            val nowTs = nowIsoUtc()
            val rowsToUpsert = JSONArray()
            val mutatedChats = mutableListOf<ChatMessageItem>()
            val deletedChatIds = mutableListOf<String>()
            var opCount = 0

            val upserts = mutations.filter { it.op != "delete" }
            val deletes = mutations.filter { it.op == "delete" }

            for (m in upserts) {
                val arr = payloadObj.optJSONArray(m.collection) ?: continue
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    if (id == m.itemId || (m.collection == "adminConfig" && i == 0)) {
                        val effectiveId = id.ifBlank { m.itemId }
                        rowsToUpsert.put(
                            JSONObject().apply {
                                put("collection", m.collection)
                                put("id", effectiveId)
                                put("payload", item)
                                put("updated_by", actor)
                                put("updated_at", nowTs)
                            }
                        )
                        opCount++
                        if (m.collection == "chatMessages") {
                            db.chatMessages.firstOrNull { it.id == effectiveId }?.let { mutatedChats.add(it) }
                        }
                        break
                    }
                }
            }

            if (rowsToUpsert.length() > 0) {
                val req = Request.Builder()
                    .url("${baseUrl()}/rest/v1/mmqa_records?on_conflict=collection,id")
                    .withSupabaseHeaders()
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(rowsToUpsert.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Supabase UPSERT error ${resp.code}: ${raw.take(200)}")
                    }
                }
            }

            for (d in deletes) {
                val colEnc = URLEncoder.encode(d.collection, "UTF-8")
                val idEnc = URLEncoder.encode(d.itemId, "UTF-8")
                val delReq = Request.Builder()
                    .url("${baseUrl()}/rest/v1/mmqa_records?collection=eq.$colEnc&id=eq.$idEnc")
                    .withSupabaseHeaders()
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                client.newCall(delReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        throw IllegalStateException("Supabase DELETE error ${resp.code}: ${raw.take(180)}")
                    }
                }
                opCount++
                if (d.collection == "chatMessages") {
                    deletedChatIds.add(d.itemId)
                }
            }

            if (mutatedChats.isNotEmpty()) {
                runCatching { pushChatMirror(mutatedChats) }
            }
            if (deletedChatIds.isNotEmpty()) {
                runCatching { deleteChatMirror(deletedChatIds) }
            }

            Result.success(opCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushAll(db: MmqaDatabase, actor: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase belum dikonfigurasi."))
        }
        try {
            val payloadObj = MmqaJsonCodec.encodePayload(db)
            val nowTs = nowIsoUtc()
            val rowsToUpsert = JSONArray()
            val keys = payloadObj.keys()
            while (keys.hasNext()) {
                val col = keys.next()
                val arr = payloadObj.optJSONArray(col) ?: continue
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "").ifBlank { "${col}_$i" }
                    rowsToUpsert.put(
                        JSONObject().apply {
                            put("collection", col)
                            put("id", id)
                            put("payload", item)
                            put("updated_by", actor)
                            put("updated_at", nowTs)
                        }
                    )
                }
            }

            if (rowsToUpsert.length() == 0) {
                return@withContext Result.success(0)
            }

            val req = Request.Builder()
                .url("${baseUrl()}/rest/v1/mmqa_records?on_conflict=collection,id")
                .withSupabaseHeaders()
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(rowsToUpsert.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Supabase UPSERT error ${resp.code}: ${raw.take(200)}")
                }
            }

            runCatching { pushChatMirror(db.chatMessages) }
            Result.success(rowsToUpsert.length())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun pushChatMirror(messages: List<ChatMessageItem>) {
        if (messages.isEmpty()) return
        val arr = JSONArray()
        messages.forEach { m ->
            val previewText = if (m.attachmentName.isNotBlank() && m.text.isBlank()) {
                "📎 ${m.attachmentName}"
            } else if (m.attachmentName.isNotBlank()) {
                "${m.text} [📎 ${m.attachmentName}]"
            } else m.text
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("channel_id", m.channelId)
                    put("sender_id", m.senderId)
                    put("sender_name", m.senderName)
                    put("sender_role", m.senderRole)
                    put("text", previewText)
                    put("time", m.time)
                    put("pinned", m.pinned)
                }
            )
        }
        val req = Request.Builder()
            .url("${baseUrl()}/rest/v1/chat_messages?on_conflict=id")
            .withSupabaseHeaders()
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(arr.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().close()
    }

    private fun deleteChatMirror(ids: List<String>) {
        ids.forEach { id ->
            val idEnc = URLEncoder.encode(id, "UTF-8")
            val req = Request.Builder()
                .url("${baseUrl()}/rest/v1/chat_messages?id=eq.$idEnc")
                .withSupabaseHeaders()
                .header("Prefer", "return=minimal")
                .delete()
                .build()
            client.newCall(req).execute().close()
        }
    }

    companion object {
        fun isQuotaOrStorageFailure(error: Throwable?): Boolean {
            if (error == null) return false
            val msg = error.message.orEmpty().lowercase(Locale.ROOT)
            if (msg.isBlank()) return false
            val httpQuotaCodes = listOf(" 402", "error 402", "http 402", " 413", "error 413", " 507", "error 507", " 540", "error 540")
            if (httpQuotaCodes.any { msg.contains(it) }) return true
            val keywords = listOf(
                "quota", "storage", "disk full", "no space", "space left",
                "paused", "exceeded", "insufficient", "read-only", "read only",
                "billing", "limit reached", "database size", "payload too large", "too large"
            )
            return keywords.any { msg.contains(it) }
        }

        fun estimateStorageFootprint(db: MmqaDatabase): StorageFootprintSummary {
            val totalJsonBytes = runCatching {
                MmqaJsonCodec.encodeDatabase(db).toByteArray(Charsets.UTF_8).size.toLong()
            }.getOrDefault(0L)
            val chatAttachBytes = db.chatMessages.sumOf { it.attachmentDataUrl.length.toLong() }
            val juknisBytes = db.juknisDocs.sumOf { it.pdfDataUrl.length.toLong() }
            val suratBytes = db.suratTemplates.sumOf {
                it.pdfDataUrl.length.toLong() + it.ttdKetuaDefault.length.toLong() + it.ttdSekretarisDefault.length.toLong()
            } + db.suratArsip.sumOf {
                it.pdfDataUrl.length.toLong() + it.ttdKetuaDataUrl.length.toLong() + it.ttdSekretarisDataUrl.length.toLong()
            }
            val avatarBytes = db.users.sumOf { it.avatarBase64.length.toLong() }
            return StorageFootprintSummary(
                totalBytes = totalJsonBytes,
                attachmentBytes = chatAttachBytes + juknisBytes + suratBytes + avatarBytes,
                chatAttachmentCount = db.chatMessages.count { it.attachmentDataUrl.isNotBlank() },
                juknisPdfCount = db.juknisDocs.count { it.pdfDataUrl.isNotBlank() },
                suratPdfCount = db.suratTemplates.count { it.pdfDataUrl.isNotBlank() } + db.suratArsip.count { it.pdfDataUrl.isNotBlank() },
                avatarCount = db.users.count { it.avatarBase64.isNotBlank() }
            )
        }
    }
}

data class StorageFootprintSummary(
    val totalBytes: Long,
    val attachmentBytes: Long,
    val chatAttachmentCount: Int,
    val juknisPdfCount: Int,
    val suratPdfCount: Int,
    val avatarCount: Int
)

