package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GasPingResult(
    val success: Boolean,
    val spreadsheetName: String = "",
    val totalSheets: Int = 0,
    val errorMessage: String = ""
)

sealed class RemoteFetchResult {
    data class Success(val data: JSONObject, val source: String) : RemoteFetchResult()
    data class Error(val message: String) : RemoteFetchResult()
}

class GoogleSheetsSyncClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun ping(gasUrl: String, sheetId: String): GasPingResult = withContext(Dispatchers.IO) {
        try {
            val sep = if (gasUrl.contains("?")) "&" else "?"
            val encodedSheetId = URLEncoder.encode(sheetId, "UTF-8")
            val url = "${gasUrl.trim()}${sep}action=ping&sheetId=$encodedSheetId&_t=${System.currentTimeMillis()}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful || body.isBlank()) {
                    return@withContext GasPingResult(false, errorMessage = "HTTP ${res.code}")
                }
                val json = JSONObject(body)
                if (json.optString("status") == "success") {
                    GasPingResult(
                        success = true,
                        spreadsheetName = json.optString("spreadsheetName", "MMQA AR2"),
                        totalSheets = json.optInt("totalSheets", MmqaConstants.SHEET_NAMES.size)
                    )
                } else {
                    GasPingResult(false, errorMessage = json.optString("message", "Ping gagal"))
                }
            }
        } catch (e: Exception) {
            GasPingResult(false, errorMessage = e.message ?: "Koneksi gagal")
        }
    }

    suspend fun fetchAllFromGas(gasUrl: String, sheetId: String): RemoteFetchResult = withContext(Dispatchers.IO) {
        try {
            val sep = if (gasUrl.contains("?")) "&" else "?"
            val encodedSheetId = URLEncoder.encode(sheetId, "UTF-8")
            val url = "${gasUrl.trim()}${sep}action=getAllData&sheetId=$encodedSheetId&_t=${System.currentTimeMillis()}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful || body.isBlank()) {
                    return@withContext RemoteFetchResult.Error("HTTP ${res.code} dari GAS")
                }
                val json = JSONObject(body)
                if (json.optString("status") == "success" && json.has("data")) {
                    RemoteFetchResult.Success(json.getJSONObject("data"), "GAS")
                } else {
                    RemoteFetchResult.Error(json.optString("message", "Respons GAS tidak valid"))
                }
            }
        } catch (e: Exception) {
            RemoteFetchResult.Error(e.message ?: "Gagal membaca GAS")
        }
    }

    /**
     * Fallback ultra-cepat: membaca 17 sheet Google Sheet secara paralel via endpoint GViz
     * jika Google Sheet dibagikan publik (Viewer) dan GAS sedang sibuk/lambat.
     */
    suspend fun fetchAllFromGoogleSheetDirect(sheetId: String): RemoteFetchResult = coroutineScope {
        try {
            val root = JSONObject()
            val results = MmqaConstants.SHEET_NAMES.map { sheetName ->
                async(Dispatchers.IO) {
                    sheetName to fetchSingleSheetGviz(sheetId, sheetName)
                }
            }.awaitAll()

            var anyParsed = false
            results.forEach { (name, arr) ->
                if (arr != null) {
                    root.put(name, arr)
                    if (arr.length() > 0) anyParsed = true
                } else {
                    root.put(name, JSONArray())
                }
            }

            if (anyParsed) {
                RemoteFetchResult.Success(root, "GoogleSheet-Direct")
            } else {
                RemoteFetchResult.Error("Google Sheet kosong atau belum dibagikan untuk pembacaan langsung.")
            }
        } catch (e: Exception) {
            RemoteFetchResult.Error(e.message ?: "Gagal membaca Google Sheet langsung")
        }
    }

    private fun fetchSingleSheetGviz(sheetId: String, sheetName: String): JSONArray? {
        return try {
            val encSheet = URLEncoder.encode(sheetName, "UTF-8")
            val url = "https://docs.google.com/spreadsheets/d/${sheetId.trim()}/gviz/tq?tqx=out:json&sheet=$encSheet"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val raw = res.body?.string().orEmpty()
                val startIdx = raw.indexOf("{")
                val endIdx = raw.lastIndexOf("}")
                if (startIdx < 0 || endIdx <= startIdx) return null
                val gviz = JSONObject(raw.substring(startIdx, endIdx + 1))
                val table = gviz.optJSONObject("table") ?: return null
                val rows = table.optJSONArray("rows") ?: return JSONArray()
                val out = JSONArray()
                for (i in 0 until rows.length()) {
                    val rowObj = rows.optJSONObject(i) ?: continue
                    val cells = rowObj.optJSONArray("c") ?: continue
                    // Column 2 (index 2) holds json_payload per Code.gs; also scan fallback cells
                    var parsedPayload: JSONObject? = null
                    val col2 = cells.optJSONObject(2)?.opt("v")?.toString()
                    if (!col2.isNullOrBlank() && col2.trim().startsWith("{")) {
                        parsedPayload = runCatching { JSONObject(col2) }.getOrNull()
                    }
                    if (parsedPayload == null) {
                        for (cIdx in 0 until cells.length()) {
                            val cellVal = cells.optJSONObject(cIdx)?.opt("v")?.toString()?.trim()
                            if (!cellVal.isNullOrEmpty() && cellVal.startsWith("{") && cellVal.endsWith("}")) {
                                parsedPayload = runCatching { JSONObject(cellVal) }.getOrNull()
                                if (parsedPayload != null) break
                            }
                        }
                    }
                    if (parsedPayload != null) out.put(parsedPayload)
                }
                out
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pushSyncAll(
        gasUrl: String,
        sheetId: String,
        actor: String,
        payload: JSONObject
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("action", "syncAll")
                put("sheetId", sheetId)
                put("actor", actor)
                put("payload", payload)
            }
            val requestBody = bodyJson.toString().toRequestBody("text/plain; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(gasUrl.trim())
                .post(requestBody)
                .build()

            client.newCall(req).execute().use { res ->
                val respStr = res.body?.string().orEmpty()
                if (!res.isSuccessful || respStr.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${res.code}"))
                }
                val respJson = JSONObject(respStr)
                if (respJson.optString("status") == "success") {
                    Result.success(respJson.optString("serverTimeWib", MmqaConstants.nowTimeHms()))
                } else {
                    Result.failure(IllegalStateException(respJson.optString("message", "Push gagal")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        val UPGRADED_CODE_GS: String = """
// BACKEND GOOGLE APPS SCRIPT (Code.gs) — MMQA AL RIFAI 02 (OPTIMASI MULTI-USER & CACHE)
const DEFAULT_SHEET_ID = "${MmqaConstants.GOOGLE_SHEET_ID}";
const SHEET_NAMES = [
  "users", "santri", "muallim", "dewanHarian", "firqoh",
  "absensi", "materi", "ujianMateri", "ujianFormat",
  "nilaiUjian", "keuangan", "suratTemplates", "suratArsip",
  "inventaris", "chatChannels", "chatMessages", "adminConfig"
];
const CACHE_KEY = "MMQA_AR2_ALL_DATA_V1";

function getTargetSpreadsheet(sheetIdParam) {
  const id = (sheetIdParam || DEFAULT_SHEET_ID || "").trim();
  if (id) {
    try { return SpreadsheetApp.openById(id); } catch (e) {}
  }
  return SpreadsheetApp.getActiveSpreadsheet();
}

function setupSheets() {
  const ss = getTargetSpreadsheet(DEFAULT_SHEET_ID);
  SHEET_NAMES.forEach(function(name) {
    let sheet = ss.getSheetByName(name) || ss.insertSheet(name);
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["id", "ringkasan_label", "json_payload", "detail_terbaca", "updated_at", "updated_by"]);
      sheet.getRange(1, 1, 1, 6).setBackground("#065f46").setFontColor("#ffffff").setFontWeight("bold");
      sheet.setFrozenRows(1);
    }
  });
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  try {
    const params = (e && e.parameter) || {};
    const ss = getTargetSpreadsheet(params.sheetId);
    if (params.action === "ping") {
      return jsonResponse({ status: "success", spreadsheetName: ss.getName(), totalSheets: SHEET_NAMES.length });
    }
    const cache = CacheService.getScriptCache();
    const cached = cache.get(CACHE_KEY);
    if (cached && !params.nocache) {
      return ContentService.createTextOutput(cached).setMimeType(ContentService.MimeType.JSON);
    }
    const data = {};
    SHEET_NAMES.forEach(function(name) {
      const sheet = ss.getSheetByName(name);
      data[name] = [];
      if (sheet && sheet.getLastRow() > 1) {
        const rows = sheet.getRange(2, 3, sheet.getLastRow() - 1, 1).getValues();
        rows.forEach(function(r) { try { if (r[0]) data[name].push(JSON.parse(r[0])); } catch (err) {} });
      }
    });
    const outStr = JSON.stringify({ status: "success", data: data });
    try { if (outStr.length < 95000) cache.put(CACHE_KEY, outStr, 30); } catch (ce) {}
    return ContentService.createTextOutput(outStr).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(25000);
    const body = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    const ss = getTargetSpreadsheet(body.sheetId);
    const nowWib = Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy-MM-dd HH:mm:ss 'WIB'");
    const actor = body.actor || "MMQA AR2 APK";
    if (body.action === "syncAll" && body.payload) {
      SHEET_NAMES.forEach(function(name) {
        const items = body.payload[name];
        if (Array.isArray(items)) {
          let sheet = ss.getSheetByName(name) || ss.insertSheet(name);
          sheet.clearContents();
          sheet.appendRow(["id", "ringkasan_label", "json_payload", "detail_terbaca", "updated_at", "updated_by"]);
          if (items.length > 0) {
            const rows = items.map(function(item) {
              return [item.id || "", item.nama || item.name || item.perihal || item.id || "", JSON.stringify(item), "", nowWib, actor];
            });
            sheet.getRange(2, 1, rows.length, 6).setValues(rows);
          }
        }
      });
      try { CacheService.getScriptCache().remove(CACHE_KEY); } catch (ce) {}
    }
    return jsonResponse({ status: "success", serverTimeWib: nowWib });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  } finally {
    try { lock.releaseLock(); } catch (e2) {}
  }
}
        """.trimIndent()
    }
}
