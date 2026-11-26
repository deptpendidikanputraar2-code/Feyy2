package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object MmqaJsonCodec {

    fun encodeDatabase(db: MmqaDatabase): String {
        val root = JSONObject()
        root.put("gasUrl", db.gasUrl)
        root.put("sheetId", db.sheetId)
        if (db.lastSync != null) root.put("lastSync", db.lastSync) else root.put("lastSync", JSONObject.NULL)
        root.put("pendingSync", db.pendingSync)
        val payload = encodePayload(db)
        val keys = payload.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            root.put(k, payload.get(k))
        }
        return root.toString()
    }

    fun decodeDatabase(json: String): MmqaDatabase {
        val defaults = MmqaDefaults.createDefaultDatabase()
        val obj = JSONObject(json)
        val remote = mergeRemoteDataJson(defaults, obj)
        val gasUrl = obj.optString("gasUrl", "").ifBlank { MmqaConstants.DEFAULT_GAS_WEBAPP_URL }
        val sheetId = obj.optString("sheetId", "").ifBlank { MmqaConstants.GOOGLE_SHEET_ID }
        val lastSync = if (obj.isNull("lastSync")) null else obj.optString("lastSync", "").ifBlank { null }
        val pendingSync = obj.optBoolean("pendingSync", false)
        return remote.copy(
            gasUrl = gasUrl,
            sheetId = sheetId,
            lastSync = lastSync,
            pendingSync = pendingSync
        )
    }

    fun encodePayload(db: MmqaDatabase): JSONObject {
        val p = JSONObject()
        p.put("users", JSONArray().apply { db.users.forEach { put(encodeUser(it)) } })
        p.put("santri", JSONArray().apply { db.santri.forEach { put(encodeSantri(it)) } })
        p.put("muallim", JSONArray().apply { db.muallim.forEach { put(encodeMuallim(it)) } })
        p.put("dewanHarian", JSONArray().apply { db.dewanHarian.forEach { put(encodeDewan(it)) } })
        p.put("firqoh", JSONArray().apply { db.firqoh.forEach { put(encodeFirqoh(it)) } })
        p.put("absensi", JSONArray().apply { db.absensi.forEach { put(encodeAbsensi(it)) } })
        p.put("materi", JSONArray().apply { db.materi.forEach { put(encodeMateri(it)) } })
        p.put("ujianMateri", JSONArray().apply { db.ujianMateri.forEach { put(encodeUjianMateri(it)) } })
        p.put("ujianFormat", JSONArray().apply { db.ujianFormat.forEach { put(encodeUjianFormat(it)) } })
        p.put("nilaiUjian", JSONArray().apply { db.nilaiUjian.forEach { put(encodeNilaiUjian(it)) } })
        p.put("keuangan", JSONArray().apply { db.keuangan.forEach { put(encodeKeuangan(it)) } })
        p.put("suratTemplates", JSONArray().apply { db.suratTemplates.forEach { put(encodeSuratTemplate(it)) } })
        p.put("suratArsip", JSONArray().apply { db.suratArsip.forEach { put(encodeSuratArsip(it)) } })
        p.put("inventaris", JSONArray().apply { db.inventaris.forEach { put(encodeInventaris(it)) } })
        p.put("chatChannels", JSONArray().apply { db.chatChannels.forEach { put(encodeChatChannel(it)) } })
        p.put("chatMessages", JSONArray().apply { db.chatMessages.forEach { put(encodeChatMessage(it)) } })
        p.put("adminConfig", JSONArray().apply {
            put(JSONObject().apply {
                put("id", "ADMIN_CFG_MAIN")
                put("nama", "Konfigurasi Admin MMQA AR2")
                put("config", encodeAdminConfig(db.adminConfig))
            })
        })
        return p
    }

    fun isRemoteDataEmpty(remoteObj: JSONObject): Boolean {
        val users = remoteObj.optJSONArray("users")
        val santri = remoteObj.optJSONArray("santri")
        val firqoh = remoteObj.optJSONArray("firqoh")
        return (users == null || users.length() == 0) &&
            (santri == null || santri.length() == 0) &&
            (firqoh == null || firqoh.length() == 0)
    }

    fun mergeRemoteDataJson(base: MmqaDatabase, remote: JSONObject): MmqaDatabase {
        val usersArr = remote.optJSONArray("users")
        val santriArr = remote.optJSONArray("santri")
        val muallimArr = remote.optJSONArray("muallim")
        val dewanArr = remote.optJSONArray("dewanHarian")
        val firqohArr = remote.optJSONArray("firqoh")
        val absensiArr = remote.optJSONArray("absensi")
        val materiArr = remote.optJSONArray("materi")
        val ujianMateriArr = remote.optJSONArray("ujianMateri")
        val ujianFormatArr = remote.optJSONArray("ujianFormat")
        val nilaiUjianArr = remote.optJSONArray("nilaiUjian")
        val keuanganArr = remote.optJSONArray("keuangan")
        val suratTplArr = remote.optJSONArray("suratTemplates")
        val suratArsipArr = remote.optJSONArray("suratArsip")
        val inventarisArr = remote.optJSONArray("inventaris")
        val chatChArr = remote.optJSONArray("chatChannels")
        val chatMsgArr = remote.optJSONArray("chatMessages")

        val adminCfgRaw = remote.opt("adminConfig")
        val parsedAdminConfig = when (adminCfgRaw) {
            is JSONArray -> if (adminCfgRaw.length() > 0) {
                val first = adminCfgRaw.optJSONObject(0)
                val cfg = first?.optJSONObject("config") ?: first
                if (cfg != null) decodeAdminConfig(cfg, base.adminConfig) else base.adminConfig
            } else base.adminConfig
            is JSONObject -> decodeAdminConfig(adminCfgRaw, base.adminConfig)
            else -> base.adminConfig
        }

        return base.copy(
            users = if (usersArr != null && usersArr.length() > 0) decodeList(usersArr, ::decodeUser) else base.users,
            santri = if (santriArr != null && santriArr.length() > 0) decodeList(santriArr, ::decodeSantri) else base.santri,
            muallim = if (muallimArr != null && muallimArr.length() > 0) decodeList(muallimArr, ::decodeMuallim) else base.muallim,
            dewanHarian = if (dewanArr != null && dewanArr.length() > 0) decodeList(dewanArr, ::decodeDewan) else base.dewanHarian,
            firqoh = if (firqohArr != null && firqohArr.length() > 0) decodeList(firqohArr, ::decodeFirqoh) else base.firqoh,
            absensi = if (absensiArr != null && absensiArr.length() > 0) decodeList(absensiArr, ::decodeAbsensi) else base.absensi,
            materi = if (materiArr != null && materiArr.length() > 0) decodeList(materiArr, ::decodeMateri) else base.materi,
            ujianMateri = if (ujianMateriArr != null && ujianMateriArr.length() > 0) decodeList(ujianMateriArr, ::decodeUjianMateri) else base.ujianMateri,
            ujianFormat = if (ujianFormatArr != null && ujianFormatArr.length() > 0) decodeList(ujianFormatArr, ::decodeUjianFormat) else base.ujianFormat,
            nilaiUjian = if (nilaiUjianArr != null && nilaiUjianArr.length() > 0) decodeList(nilaiUjianArr, ::decodeNilaiUjian) else base.nilaiUjian,
            keuangan = if (keuanganArr != null && keuanganArr.length() > 0) decodeList(keuanganArr, ::decodeKeuangan) else base.keuangan,
            suratTemplates = if (suratTplArr != null && suratTplArr.length() > 0) decodeList(suratTplArr, ::decodeSuratTemplate) else base.suratTemplates,
            suratArsip = if (suratArsipArr != null && suratArsipArr.length() > 0) decodeList(suratArsipArr, ::decodeSuratArsip) else base.suratArsip,
            inventaris = if (inventarisArr != null && inventarisArr.length() > 0) decodeList(inventarisArr, ::decodeInventaris) else base.inventaris,
            chatChannels = if (chatChArr != null && chatChArr.length() > 0) decodeList(chatChArr, ::decodeChatChannel) else base.chatChannels,
            chatMessages = if (chatMsgArr != null && chatMsgArr.length() > 0) decodeList(chatMsgArr, ::decodeChatMessage) else base.chatMessages,
            adminConfig = parsedAdminConfig
        )
    }

    fun encodeMutations(list: List<PendingMutation>): String {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("collection", m.collection)
                put("itemId", m.itemId)
                put("op", m.op)
                put("timestamp", m.timestamp)
            })
        }
        return arr.toString()
    }

    fun decodeMutations(json: String): List<PendingMutation> {
        val arr = JSONArray(json)
        val out = mutableListOf<PendingMutation>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                PendingMutation(
                    collection = o.optString("collection"),
                    itemId = o.optString("itemId"),
                    op = o.optString("op"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        return out
    }

    private inline fun <T> decodeList(arr: JSONArray, mapper: (JSONObject) -> T): List<T> {
        val result = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(mapper(obj))
        }
        return result
    }

    private fun encodeStringList(items: List<String>) = JSONArray().apply {
        items.forEach { put(it) }
    }

    private fun decodeStringList(arr: JSONArray?): List<String> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }

    private fun encodeUser(u: UserAccount) = JSONObject().apply {
        put("id", u.id)
        put("username", u.username)
        put("password", u.password)
        put("name", u.name)
        put("nama", u.name)
        put("role", u.role)
        put("jabatan", u.jabatan)
        put("firqohId", u.firqohId)
        put("failedAttempts", u.failedAttempts)
        if (u.lockedUntil != null) put("lockedUntil", u.lockedUntil) else put("lockedUntil", JSONObject.NULL)
        put("passwordUpdatedAt", u.passwordUpdatedAt)
        put("avatarBase64", u.avatarBase64)
        put("deniedMenus", encodeStringList(u.deniedMenus))
        put("deniedSubmenus", encodeStringList(u.deniedSubmenus))
        put("deniedAnakSub", encodeStringList(u.deniedAnakSub))
    }

    private fun decodeUser(o: JSONObject) = UserAccount(
        id = o.optString("id"),
        username = o.optString("username"),
        password = o.optString("password"),
        name = o.optString("name", o.optString("nama")),
        role = o.optString("role", "muallim"),
        jabatan = o.optString("jabatan"),
        firqohId = o.optString("firqohId"),
        failedAttempts = o.optInt("failedAttempts", 0),
        lockedUntil = if (o.isNull("lockedUntil")) null else o.optLong("lockedUntil"),
        passwordUpdatedAt = o.optString("passwordUpdatedAt"),
        avatarBase64 = o.optString("avatarBase64", ""),
        deniedMenus = decodeStringList(o.optJSONArray("deniedMenus")),
        deniedSubmenus = decodeStringList(o.optJSONArray("deniedSubmenus")),
        deniedAnakSub = decodeStringList(o.optJSONArray("deniedAnakSub"))
    )

    private fun encodeFirqoh(f: FirqohItem) = JSONObject().apply {
        put("id", f.id)
        put("kode", f.kode)
        put("nama", f.nama)
        put("tingkat", f.tingkat)
        put("jadwal", f.jadwal)
        put("ruangan", f.ruangan)
    }

    private fun decodeFirqoh(o: JSONObject) = FirqohItem(
        id = o.optString("id"),
        kode = o.optString("kode"),
        nama = o.optString("nama"),
        tingkat = o.optString("tingkat"),
        jadwal = o.optString("jadwal"),
        ruangan = o.optString("ruangan")
    )

    private fun encodeSantri(s: SantriItem) = JSONObject().apply {
        put("id", s.id)
        put("nis", s.nis)
        put("nama", s.nama)
        put("firqohId", s.firqohId)
        put("angkatan", s.angkatan)
        put("wali", s.wali)
        put("hpWali", s.hpWali)
        put("statusSyahriah", s.statusSyahriah)
        put("pesertaUjian", s.pesertaUjian)
    }

    private fun decodeSantri(o: JSONObject) = SantriItem(
        id = o.optString("id"),
        nis = o.optString("nis"),
        nama = o.optString("nama"),
        firqohId = o.optString("firqohId"),
        angkatan = o.optString("angkatan"),
        wali = o.optString("wali"),
        hpWali = o.optString("hpWali"),
        statusSyahriah = o.optString("statusSyahriah", "Belum Lunas"),
        pesertaUjian = if (o.has("pesertaUjian")) o.optBoolean("pesertaUjian", true) else true
    )

    private fun encodeMuallim(m: MuallimItem) = JSONObject().apply {
        put("id", m.id)
        put("niy", m.niy)
        put("nama", m.nama)
        put("firqohId", m.firqohId)
        put("bidang", m.bidang)
        put("hp", m.hp)
    }

    private fun decodeMuallim(o: JSONObject) = MuallimItem(
        id = o.optString("id"),
        niy = o.optString("niy"),
        nama = o.optString("nama"),
        firqohId = o.optString("firqohId"),
        bidang = o.optString("bidang"),
        hp = o.optString("hp")
    )

    private fun encodeDewan(d: DewanHarianItem) = JSONObject().apply {
        put("id", d.id)
        put("kode", d.kode)
        put("nama", d.nama)
        put("jabatan", d.jabatan)
        put("hp", d.hp)
    }

    private fun decodeDewan(o: JSONObject) = DewanHarianItem(
        id = o.optString("id"),
        kode = o.optString("kode"),
        nama = o.optString("nama"),
        jabatan = o.optString("jabatan"),
        hp = o.optString("hp")
    )

    private fun encodeAbsensi(a: AbsensiRecord) = JSONObject().apply {
        put("id", a.id)
        put("tanggal", a.tanggal)
        put("sesi", a.sesi)
        put("tipe", a.tipe)
        put("targetId", a.targetId)
        put("firqohId", a.firqohId)
        put("status", a.status)
        put("catatan", a.catatan)
    }

    private fun decodeAbsensi(o: JSONObject) = AbsensiRecord(
        id = o.optString("id"),
        tanggal = o.optString("tanggal"),
        sesi = o.optString("sesi"),
        tipe = o.optString("tipe"),
        targetId = o.optString("targetId"),
        firqohId = o.optString("firqohId"),
        status = o.optString("status"),
        catatan = o.optString("catatan")
    )

    private fun encodeMateri(m: MateriItem) = JSONObject().apply {
        put("id", m.id)
        put("firqohId", m.firqohId)
        put("kitab", m.kitab)
        put("nama", m.kitab)
        put("bab", m.bab)
        put("target", m.target)
        put("progress", m.progress)
        put("pengatur", m.pengatur)
    }

    private fun decodeMateri(o: JSONObject) = MateriItem(
        id = o.optString("id"),
        firqohId = o.optString("firqohId"),
        kitab = o.optString("kitab"),
        bab = o.optString("bab"),
        target = o.optString("target"),
        progress = o.optInt("progress", 0),
        pengatur = o.optString("pengatur", "Dewan Harian")
    )

    private fun encodeUjianMateri(u: UjianMateriItem) = JSONObject().apply {
        put("id", u.id)
        put("firqohId", u.firqohId)
        put("namaUjian", u.namaUjian)
        put("nama", u.namaUjian)
        put("kitab", u.kitab)
        put("kkm", u.kkm)
        put("keterangan", u.keterangan)
    }

    private fun decodeUjianMateri(o: JSONObject) = UjianMateriItem(
        id = o.optString("id"),
        firqohId = o.optString("firqohId"),
        namaUjian = o.optString("namaUjian"),
        kitab = o.optString("kitab"),
        kkm = o.optInt("kkm", 75),
        keterangan = o.optString("keterangan")
    )

    private fun encodeUjianFormat(f: UjianFormatItem) = JSONObject().apply {
        put("id", f.id)
        put("aspek", f.aspek)
        put("nama", f.aspek)
        put("bobot", f.bobot)
        put("deskripsi", f.deskripsi)
    }

    private fun decodeUjianFormat(o: JSONObject) = UjianFormatItem(
        id = o.optString("id"),
        aspek = o.optString("aspek"),
        bobot = o.optInt("bobot", 20),
        deskripsi = o.optString("deskripsi")
    )

    private fun encodeNilaiUjian(n: NilaiUjianItem) = JSONObject().apply {
        put("id", n.id)
        put("santriId", n.santriId)
        put("firqohId", n.firqohId)
        put("skorAspek", JSONObject().apply {
            n.skorAspek.forEach { (k, v) -> put(k, v) }
        })
        put("nilaiAkhir", n.nilaiAkhir)
        put("predikat", n.predikat)
        put("catatan", n.catatan)
    }

    private fun decodeNilaiUjian(o: JSONObject): NilaiUjianItem {
        val skorObj = o.optJSONObject("skorAspek")
        val skorMap = mutableMapOf<String, Double>()
        if (skorObj != null) {
            val keys = skorObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                skorMap[k] = skorObj.optDouble(k, 0.0)
            }
        }
        return NilaiUjianItem(
            id = o.optString("id"),
            santriId = o.optString("santriId"),
            firqohId = o.optString("firqohId"),
            skorAspek = skorMap,
            nilaiAkhir = o.optDouble("nilaiAkhir", 0.0),
            predikat = o.optString("predikat"),
            catatan = o.optString("catatan")
        )
    }

    private fun encodeKeuangan(k: KeuanganItem) = JSONObject().apply {
        put("id", k.id)
        put("tanggal", k.tanggal)
        put("jenis", k.jenis)
        put("kategori", k.kategori)
        put("nama", k.kategori)
        put("nominal", k.nominal)
        put("keterangan", k.keterangan)
        put("pencatat", k.pencatat)
    }

    private fun decodeKeuangan(o: JSONObject) = KeuanganItem(
        id = o.optString("id"),
        tanggal = o.optString("tanggal"),
        jenis = o.optString("jenis", "pemasukan"),
        kategori = o.optString("kategori"),
        nominal = o.optLong("nominal", 0L),
        keterangan = o.optString("keterangan"),
        pencatat = o.optString("pencatat")
    )

    private fun encodeSuratTemplate(t: SuratTemplateItem) = JSONObject().apply {
        put("id", t.id)
        put("kodeSurat", t.kodeSurat)
        put("nama", t.nama)
        put("perihalDefault", t.perihalDefault)
        put("isiDefault", t.isiDefault)
        put("pdfName", t.pdfName)
        put("pdfDataUrl", t.pdfDataUrl)
    }

    private fun decodeSuratTemplate(o: JSONObject) = SuratTemplateItem(
        id = o.optString("id"),
        kodeSurat = o.optString("kodeSurat", "A"),
        nama = o.optString("nama"),
        perihalDefault = o.optString("perihalDefault"),
        isiDefault = o.optString("isiDefault"),
        pdfName = o.optString("pdfName"),
        pdfDataUrl = o.optString("pdfDataUrl")
    )

    private fun encodeSuratArsip(s: SuratArsipItem) = JSONObject().apply {
        put("id", s.id)
        put("kodeSurat", s.kodeSurat)
        put("nomorSurat", s.nomorSurat)
        put("nama", s.perihal)
        put("tanggal", s.tanggal)
        put("lampiran", s.lampiran)
        put("perihal", s.perihal)
        put("tujuan", s.tujuan)
        put("isi", s.isi)
        put("penandatangan", s.penandatangan)
        put("jabatanPenandatangan", s.jabatanPenandatangan)
        put("templateId", s.templateId)
        put("pdfName", s.pdfName)
        put("pdfDataUrl", s.pdfDataUrl)
    }

    private fun decodeSuratArsip(o: JSONObject) = SuratArsipItem(
        id = o.optString("id"),
        kodeSurat = o.optString("kodeSurat", "A"),
        nomorSurat = o.optString("nomorSurat"),
        tanggal = o.optString("tanggal"),
        lampiran = o.optString("lampiran", "-"),
        perihal = o.optString("perihal"),
        tujuan = o.optString("tujuan"),
        isi = o.optString("isi"),
        penandatangan = o.optString("penandatangan"),
        jabatanPenandatangan = o.optString("jabatanPenandatangan"),
        templateId = o.optString("templateId"),
        pdfName = o.optString("pdfName"),
        pdfDataUrl = o.optString("pdfDataUrl")
    )

    private fun encodeInventaris(i: InventarisItem) = JSONObject().apply {
        put("id", i.id)
        put("kodeAset", i.kodeAset)
        put("nama", i.nama)
        put("kategori", i.kategori)
        put("jumlah", i.jumlah)
        put("satuan", i.satuan)
        put("lokasi", i.lokasi)
        put("kondisi", i.kondisi)
        put("nilaiEstimasi", i.nilaiEstimasi)
        put("pj", i.pj)
        put("keterangan", i.keterangan)
    }

    private fun decodeInventaris(o: JSONObject) = InventarisItem(
        id = o.optString("id"),
        kodeAset = o.optString("kodeAset"),
        nama = o.optString("nama"),
        kategori = o.optString("kategori"),
        jumlah = o.optInt("jumlah", 1),
        satuan = o.optString("satuan", "Unit"),
        lokasi = o.optString("lokasi"),
        kondisi = o.optString("kondisi", "Baik"),
        nilaiEstimasi = o.optLong("nilaiEstimasi", 0L),
        pj = o.optString("pj"),
        keterangan = o.optString("keterangan")
    )

    private fun encodeChatChannel(c: ChatChannelItem) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("nama", c.name)
        put("desc", c.desc)
    }

    private fun decodeChatChannel(o: JSONObject) = ChatChannelItem(
        id = o.optString("id"),
        name = o.optString("name", o.optString("nama")),
        desc = o.optString("desc")
    )

    private fun encodeChatMessage(m: ChatMessageItem) = JSONObject().apply {
        put("id", m.id)
        put("channelId", m.channelId)
        put("senderId", m.senderId)
        put("senderName", m.senderName)
        put("nama", m.senderName)
        put("senderRole", m.senderRole)
        put("text", m.text)
        put("time", m.time)
        if (m.replyTo != null) {
            put("replyTo", JSONObject().apply {
                put("id", m.replyTo.id)
                put("senderName", m.replyTo.senderName)
                put("text", m.replyTo.text)
            })
        } else {
            put("replyTo", JSONObject.NULL)
        }
        put("reactions", JSONObject().apply {
            m.reactions.forEach { (k, v) -> put(k, v) }
        })
        put("pinned", m.pinned)
        put("attachmentName", m.attachmentName)
        put("attachmentMime", m.attachmentMime)
        put("attachmentSize", m.attachmentSize)
        put("attachmentDataUrl", m.attachmentDataUrl)
    }

    private fun decodeChatMessage(o: JSONObject): ChatMessageItem {
        val replyObj = if (o.isNull("replyTo")) null else o.optJSONObject("replyTo")
        val reactObj = o.optJSONObject("reactions")
        val reactMap = mutableMapOf<String, Int>()
        if (reactObj != null) {
            val keys = reactObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                reactMap[k] = reactObj.optInt(k, 0)
            }
        }
        return ChatMessageItem(
            id = o.optString("id"),
            channelId = o.optString("channelId", "CHN-UMUM"),
            senderId = o.optString("senderId"),
            senderName = o.optString("senderName"),
            senderRole = o.optString("senderRole"),
            text = o.optString("text"),
            time = o.optString("time"),
            replyTo = replyObj?.let {
                ChatReplyRef(it.optString("id"), it.optString("senderName"), it.optString("text"))
            },
            reactions = reactMap,
            pinned = o.optBoolean("pinned", false),
            attachmentName = o.optString("attachmentName", ""),
            attachmentMime = o.optString("attachmentMime", ""),
            attachmentSize = o.optLong("attachmentSize", 0L),
            attachmentDataUrl = o.optString("attachmentDataUrl", "")
        )
    }

    private fun encodeAdminConfig(cfg: AdminConfig) = JSONObject().apply {
        put("menus", JSONArray().apply {
            cfg.menus.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("label", m.label)
                    put("icon", m.icon)
                    put("roles", m.roles)
                    put("enabled", m.enabled)
                    put("order", m.order)
                })
            }
        })
        put("submenus", JSONObject().apply {
            put("absensi", encodeSubmenuList(cfg.submenus.absensi))
            put("ujian", encodeSubmenuList(cfg.submenus.ujian))
            put("keuangan", encodeSubmenuList(cfg.submenus.keuangan))
            put("surat", encodeSubmenuList(cfg.submenus.surat))
            put("induk", encodeSubmenuList(cfg.submenus.induk))
        })
        put("anakSub", JSONArray().apply {
            cfg.anakSub.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("menuId", a.menuId)
                    put("submenuId", a.submenuId)
                    put("label", a.label)
                    put("roles", a.roles)
                    put("enabled", a.enabled)
                })
            }
        })
        put("permissions", JSONObject().apply {
            put("muallimCanManageMateri", cfg.permissions.muallimCanManageMateri)
            put("muallimCanManageUjianMateri", cfg.permissions.muallimCanManageUjianMateri)
            put("muallimCanManageRubrik", cfg.permissions.muallimCanManageRubrik)
            put("muallimCanInputKeuangan", cfg.permissions.muallimCanInputKeuangan)
            put("muallimCanManageSurat", cfg.permissions.muallimCanManageSurat)
            put("muallimCanManageInventaris", cfg.permissions.muallimCanManageInventaris)
            put("muallimCanManageDataInduk", cfg.permissions.muallimCanManageDataInduk)
            put("allowDeleteRecords", cfg.permissions.allowDeleteRecords)
            put("syahriahNominal", cfg.permissions.syahriahNominal)
        })
        put("passwordRule", JSONObject().apply {
            put("minLength", cfg.passwordRule.minLength)
            put("requireUppercase", cfg.passwordRule.requireUppercase)
            put("requireNumber", cfg.passwordRule.requireNumber)
            put("requireSpecial", cfg.passwordRule.requireSpecial)
            put("maxLoginAttempts", cfg.passwordRule.maxLoginAttempts)
            put("expireDays", cfg.passwordRule.expireDays)
        })
    }

    private fun encodeSubmenuList(list: List<SubmenuConfigItem>) = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id)
                put("label", s.label)
                put("enabled", s.enabled)
                put("roles", s.roles)
            })
        }
    }

    private fun decodeAdminConfig(o: JSONObject, fallback: AdminConfig): AdminConfig {
        val menusArr = o.optJSONArray("menus")
        val menus = if (menusArr != null && menusArr.length() > 0) {
            decodeList(menusArr) { m ->
                MenuConfigItem(
                    id = m.optString("id"),
                    label = m.optString("label"),
                    icon = m.optString("icon", "📌"),
                    roles = m.optString("roles", "all"),
                    enabled = m.optBoolean("enabled", true),
                    order = m.optInt("order", 99)
                )
            }
        } else fallback.menus

        val subObj = o.optJSONObject("submenus")
        val submenus = if (subObj != null) {
            SubmenuGroups(
                absensi = decodeSubmenuList(subObj.optJSONArray("absensi"), fallback.submenus.absensi),
                ujian = decodeSubmenuList(subObj.optJSONArray("ujian"), fallback.submenus.ujian),
                keuangan = decodeSubmenuList(subObj.optJSONArray("keuangan"), fallback.submenus.keuangan),
                surat = decodeSubmenuList(subObj.optJSONArray("surat"), fallback.submenus.surat),
                induk = decodeSubmenuList(subObj.optJSONArray("induk"), fallback.submenus.induk)
            )
        } else fallback.submenus

        val anakSubArr = o.optJSONArray("anakSub")
        val anakSub = if (anakSubArr != null && anakSubArr.length() > 0) {
            val decoded = decodeList(anakSubArr) { a ->
                AnakSubConfigItem(
                    id = a.optString("id"),
                    menuId = a.optString("menuId"),
                    submenuId = a.optString("submenuId"),
                    label = a.optString("label"),
                    roles = a.optString("roles", "all"),
                    enabled = a.optBoolean("enabled", true)
                )
            }
            val existingIds = decoded.map { it.id }.toSet()
            val missingDefaults = MmqaDefaults.defaultAnakSub().filter { it.id !in existingIds }
            decoded + missingDefaults
        } else fallback.anakSub

        val permObj = o.optJSONObject("permissions")
        val permissions = if (permObj != null) {
            AdminPermissions(
                muallimCanManageMateri = permObj.optBoolean("muallimCanManageMateri", true),
                muallimCanManageUjianMateri = permObj.optBoolean("muallimCanManageUjianMateri", false),
                muallimCanManageRubrik = permObj.optBoolean("muallimCanManageRubrik", false),
                muallimCanInputKeuangan = permObj.optBoolean("muallimCanInputKeuangan", false),
                muallimCanManageSurat = permObj.optBoolean("muallimCanManageSurat", false),
                muallimCanManageInventaris = permObj.optBoolean("muallimCanManageInventaris", false),
                muallimCanManageDataInduk = permObj.optBoolean("muallimCanManageDataInduk", false),
                allowDeleteRecords = permObj.optBoolean("allowDeleteRecords", true),
                syahriahNominal = permObj.optLong("syahriahNominal", 150000L)
            )
        } else fallback.permissions

        val prObj = o.optJSONObject("passwordRule")
        val passwordRule = if (prObj != null) {
            PasswordRule(
                minLength = prObj.optInt("minLength", 3),
                requireUppercase = prObj.optBoolean("requireUppercase", false),
                requireNumber = prObj.optBoolean("requireNumber", false),
                requireSpecial = prObj.optBoolean("requireSpecial", false),
                maxLoginAttempts = prObj.optInt("maxLoginAttempts", 5),
                expireDays = prObj.optInt("expireDays", 0)
            )
        } else fallback.passwordRule

        return AdminConfig(menus, submenus, anakSub, permissions, passwordRule)
    }

    private fun decodeSubmenuList(arr: JSONArray?, fallback: List<SubmenuConfigItem>): List<SubmenuConfigItem> {
        if (arr == null || arr.length() == 0) return fallback
        return decodeList(arr) { s ->
            SubmenuConfigItem(
                id = s.optString("id"),
                label = s.optString("label"),
                enabled = s.optBoolean("enabled", true),
                roles = s.optString("roles", "all")
            )
        }
    }
}
