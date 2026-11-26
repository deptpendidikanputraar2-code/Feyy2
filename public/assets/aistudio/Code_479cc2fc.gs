// BACKEND GOOGLE APPS SCRIPT (Code.gs) — PWA MMQA AL RIFAI 02
const SHEET_NAMES = [
  "users", "santri", "muallim", "dewanHarian", "firqoh",
  "absensi", "materi", "ujianMateri", "ujianFormat",
  "nilaiUjian", "keuangan", "suratTemplates", "suratArsip",
  "inventaris", "chatChannels", "chatMessages", "adminConfig"
];
const DRIVE_PDF_FOLDER_NAME = "ARSIP_PDF_MMQA_AR2";
const DRIVE_BACKUP_FOLDER_NAME = "BACKUP_DB_MMQA_AR2";

function setupSheets() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
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
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    if (params.action === "ping") {
      return jsonResponse({ status: "success", spreadsheetName: ss.getName(), totalSheets: SHEET_NAMES.length });
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
    return jsonResponse({ status: "success", data: data });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(20000);
    const body = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const nowWib = Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyy-MM-dd HH:mm:ss 'WIB'");
    const actor = body.actor || "PWA User";
    if (body.action === "syncAll" && body.payload) {
      SHEET_NAMES.forEach(function(name) {
        const items = body.payload[name];
        if (Array.isArray(items)) {
          let sheet = ss.getSheetByName(name) || ss.insertSheet(name);
          sheet.clearContents();
          sheet.appendRow(["id", "ringkasan_label", "json_payload", "detail_terbaca", "updated_at", "updated_by"]);
          if (items.length > 0) {
            const rows = items.map(function(item) {
              return [item.id || "", item.nama || item.id || "", JSON.stringify(item), "", nowWib, actor];
            });
            sheet.getRange(2, 1, rows.length, 6).setValues(rows);
          }
        }
      });
    }
    return jsonResponse({ status: "success", serverTimeWib: nowWib });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  } finally {
    try { lock.releaseLock(); } catch (e2) {}
  }
}