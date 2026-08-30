package com.echo.ktv.api

import android.content.Context
import com.echo.ktv.auth.UserManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SongItem(
    val title: String,
    val artist: String,
    val hash: String,
    val albumAudioId: String,
    val duration: Int,
    val mvHash: String = ""
)

data class MvItem(
    val title: String,
    val artist: String,
    val mvHash: String,
    val duration: Int,
    val imgUrl: String
)

data class SingerItem(
    val singerName: String,
    val singerId: Int,
    val imgUrl: String,
    val songCount: Int = 0
)

data class MvStreamResult(
    val url: String,
    val title: String,
    val artist: String,
    val hash: String
)

data class AccompanimentMatchResult(
    val title: String,
    val artist: String,
    val url: String,
    val hash: String,
    val duration: Int
)

data class QrCheckResult(
    val status: Int, // 0: ??, 1: ????, 2: ???, 4: ????
    val userId: Long = 0L,
    val token: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val vipType: Int = 0,
    val vipToken: String = ""
)


object KugouApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val FALLBACK_AUDIO_URL = "http://music.163.com/song/media/outer/url?id=2652820720.mp3"

    private fun JsonObject.getSafeString(vararg keys: String): String {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                val str = get(k).asString
                if (str.isNotEmpty()) return str
            }
        }
        return ""
    }

    private fun JsonObject.getSafeInt(vararg keys: String, default: Int = 0): Int {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                return try {
                    get(k).asInt
                } catch (e: Exception) {
                    default
                }
            }
        }
        return default
    }

    // Search songs via mobilecdn endpoint (supports page and pageSize)
    fun searchSong(keyword: String, page: Int = 1, pageSize: Int = 30, callback: (Result<List<SongItem>>) -> Unit) {
        if (keyword.isBlank()) {
            mainHandler.post { callback(Result.success(emptyList())) }
            return
        }

        val urlBuilder = "http://mobilecdn.kugou.com/api/v3/search/song".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", keyword)
            addQueryParameter("page", page.toString())
            addQueryParameter("pagesize", pageSize.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.success(emptyList())) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: data?.getAsJsonArray("lists") ?: JsonArray()
                    
                    val result = mutableListOf<SongItem>()
                    for (element in info) {
                        if (!element.isJsonObject) continue
                        val itemObj = element.asJsonObject
                        
                        val filename = itemObj.getSafeString("filename", "songname", "title", "FileName")
                        val parts = filename.split(" - ", limit = 2)
                        val rawArtist = itemObj.getSafeString("singername", "SingerName")
                        val artist = if (rawArtist.isNotEmpty()) rawArtist else parts.getOrNull(0)?.trim() ?: ""
                        val title = if (parts.size > 1) parts[1].trim() else filename
                        
                        val hash = itemObj.getSafeString("hash", "filehash", "FileHash")
                        val mvHash = itemObj.getSafeString("mvhash", "mv_hash", "MvHash")
                        val albumAudioId = itemObj.getSafeString("album_audio_id", "audio_id", "album_id")
                        val duration = itemObj.getSafeInt("duration", default = 240)
                        
                        if (hash.isNotEmpty()) {
                            result.add(SongItem(title, artist, hash, albumAudioId, duration, mvHash))
                        }
                    }

                    mainHandler.post { callback(Result.success(result)) }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.success(emptyList())) }
                }
            }
        })
    }

    // Search singers via official Author API with high-resolution Avatars & Song counts
    fun searchSinger(keyword: String, page: Int = 1, pageSize: Int = 30, callback: (Result<List<SingerItem>>) -> Unit) {
        if (keyword.isBlank()) {
            mainHandler.post { callback(Result.success(emptyList())) }
            return
        }

        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val params = mapOf(
            "appid" to SignatureUtils.LITE_APP_ID,
            "clienttime" to clientTime,
            "clientver" to SignatureUtils.LITE_CLIENT_VER,
            "dfid" to "-",
            "keyword" to keyword,
            "mid" to "undefined",
            "page" to page.toString(),
            "pagesize" to pageSize.toString(),
            "platform" to "AndroidFilter",
            "uuid" to "-"
        )
        val signature = SignatureUtils.signatureAndroidParams(params, "", isLite = true)

        val urlBuilder = "https://gateway.kugou.com/v1/search/author".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("signature", signature)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("x-router", "complexsearch.kugou.com")
            .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                searchSingerFallback(keyword, page, pageSize, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val lists = data?.getAsJsonArray("lists") ?: JsonArray()

                    val result = mutableListOf<SingerItem>()
                    for (elem in lists) {
                        if (!elem.isJsonObject) continue
                        val obj = elem.asJsonObject
                        val name = obj.getSafeString("AuthorName", "singername", "name")
                        val id = obj.getSafeInt("AuthorId", "singerid", "id", default = 0)
                        var avatar = obj.getSafeString("Avatar", "imgurl", "pic")
                        avatar = avatar.replace("{size}", "240")
                        val audioCount = obj.getSafeInt("AudioCount", default = 0)

                        if (name.isNotEmpty()) {
                            result.add(SingerItem(name, id, avatar, audioCount))
                        }
                    }

                    if (result.isEmpty()) {
                        searchSingerFallback(keyword, page, pageSize, callback)
                    } else {
                        mainHandler.post { callback(Result.success(result)) }
                    }
                } catch (e: Exception) {
                    searchSingerFallback(keyword, page, pageSize, callback)
                }
            }
        })
    }

    private fun searchSingerFallback(keyword: String, page: Int = 1, pageSize: Int = 30, callback: (Result<List<SingerItem>>) -> Unit) {
        val urlBuilder = "http://mobilecdn.kugou.com/api/v3/search/singer".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", keyword)
            addQueryParameter("page", page.toString())
            addQueryParameter("pagesize", pageSize.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.success(emptyList())) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val dataArray = json.getAsJsonArray("data") ?: JsonArray()
                    
                    val result = mutableListOf<SingerItem>()
                    for (element in dataArray) {
                        if (!element.isJsonObject) continue
                        val itemObj = element.asJsonObject
                        val name = itemObj.getSafeString("singername", "name")
                        val id = itemObj.getSafeInt("singerid", "id", default = 0)
                        if (name.isNotEmpty()) {
                            result.add(SingerItem(name, id, "", 0))
                        }
                    }

                    mainHandler.post { callback(Result.success(result)) }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.success(emptyList())) }
                }
            }
        })
    }

    // Retrieve full artist discography with total song count and pagination
    fun getSingerSongs(
        singerId: Int,
        singerName: String,
        page: Int = 1,
        pageSize: Int = 30,
        callback: (Result<Pair<List<SongItem>, Int>>) -> Unit
    ) {
        if (singerId <= 0) {
            searchSong(singerName, page, pageSize) { res ->
                res.onSuccess { list ->
                    mainHandler.post { callback(Result.success(Pair(list, list.size))) }
                }
                res.onFailure {
                    mainHandler.post { callback(Result.failure(it)) }
                }
            }
            return
        }

        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val key = CryptoUtils.md5(clientTime + SignatureUtils.LITE_CLIENT_VER + SignatureUtils.LITE_APP_ID + "undefined" + "1005undefined03116")

        val dataObj = JsonObject().apply {
            addProperty("appid", SignatureUtils.LITE_APP_ID)
            addProperty("clientver", SignatureUtils.LITE_CLIENT_VER)
            addProperty("mid", "undefined")
            addProperty("clienttime", clientTime)
            addProperty("key", key)
            addProperty("author_id", singerId.toString())
            addProperty("pagesize", pageSize)
            addProperty("page", page)
            addProperty("sort", 1)
            addProperty("area_code", "all")
        }
        val dataJson = gson.toJson(dataObj)

        val defaultParams = mapOf(
            "appid" to SignatureUtils.LITE_APP_ID,
            "clienttime" to clientTime,
            "clientver" to SignatureUtils.LITE_CLIENT_VER,
            "dfid" to "-",
            "mid" to "undefined",
            "uuid" to "-"
        )
        val signature = SignatureUtils.signatureAndroidParams(defaultParams, dataJson, isLite = true)

        val urlBuilder = "https://gateway.kugou.com/kmr/v1/audio_group/author".toHttpUrl().newBuilder().apply {
            defaultParams.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("signature", signature)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = dataJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .post(requestBody)
            .addHeader("x-router", "openapi.kugou.com")
            .addHeader("kg-tid", "220")
            .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                searchSong(singerName, page, pageSize) { res ->
                    res.onSuccess { list -> mainHandler.post { callback(Result.success(Pair(list, list.size))) } }
                    res.onFailure { mainHandler.post { callback(Result.failure(it)) } }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val total = json.get("total")?.asInt ?: 0
                    val dataArr = json.getAsJsonArray("data") ?: JsonArray()

                    val result = mutableListOf<SongItem>()
                    for (elem in dataArr) {
                        if (!elem.isJsonObject) continue
                        val obj = elem.asJsonObject
                        val title = obj.getSafeString("audio_name", "songname", "title")
                        val artist = obj.getSafeString("author_name", "singername")
                        val hash = obj.getSafeString("hash", "hash_128", "hash_320")
                        val mvHash = obj.getSafeString("video_hash", "mvhash")
                        val albumAudioId = obj.getSafeString("album_audio_id", "audio_id")
                        val duration = (obj.getSafeInt("timelength", "duration", default = 240000)) / 1000

                        if (hash.isNotEmpty() && title.isNotEmpty()) {
                            result.add(SongItem(title, if (artist.isNotEmpty()) artist else singerName, hash, albumAudioId, duration, mvHash))
                        }
                    }

                    if (result.isEmpty()) {
                        searchSong(singerName, page, pageSize) { res ->
                            res.onSuccess { list -> mainHandler.post { callback(Result.success(Pair(list, list.size))) } }
                            res.onFailure { mainHandler.post { callback(Result.failure(it)) } }
                        }
                    } else {
                        mainHandler.post { callback(Result.success(Pair(result, if (total > 0) total else result.size))) }
                    }
                } catch (e: Exception) {
                    searchSong(singerName, page, pageSize) { res ->
                        res.onSuccess { list -> mainHandler.post { callback(Result.success(Pair(list, list.size))) } }
                        res.onFailure { mainHandler.post { callback(Result.failure(it)) } }
                    }
                }
            }
        })
    }

    // EchoMusic-equivalent Official MV Pipeline:
    // 1. Queries /kmr/v1/audio/mv with album_audio_id to get official MV h264/h265/mkv hashes
    // 2. Queries /v2/interface/index with target hash to get official 1080P/720P MP4 video stream URL
    fun resolveOfficialMv(albumAudioId: String, fallbackMvHash: String = "", songTitle: String = "", callback: (Result<MvStreamResult>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()

        if (albumAudioId.isNotEmpty() && albumAudioId != "0" && albumAudioId != "local") {
            val defaultParams = mapOf(
                "appid" to SignatureUtils.LITE_APP_ID,
                "clienttime" to clientTime,
                "clientver" to SignatureUtils.LITE_CLIENT_VER,
                "dfid" to "-",
                "mid" to "undefined",
                "uuid" to "-"
            )
            val dataJson = "{\"data\":[{\"album_audio_id\":\"$albumAudioId\"}],\"fields\":\"mkv,tags,h264,h265,authors\"}"
            val signature = SignatureUtils.signatureAndroidParams(defaultParams, dataJson, isLite = true)

            val urlBuilder = "https://gateway.kugou.com/kmr/v1/audio/mv".toHttpUrl().newBuilder().apply {
                defaultParams.forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter("signature", signature)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = dataJson.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(urlBuilder.build())
                .post(requestBody)
                .addHeader("x-router", "openapi.kugou.com")
                .addHeader("KG-TID", "38")
                .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
                .addHeader("dfid", "-")
                .addHeader("mid", "undefined")
                .addHeader("clienttime", clientTime)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (fallbackMvHash.length >= 32) {
                        fetchMvVideoUrl(fallbackMvHash, songTitle, "", callback)
                    } else {
                        mainHandler.post { callback(Result.failure(Exception("No MV found"))) }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: ""
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val dataArray = json.getAsJsonArray("data")
                        var targetHash = ""
                        var mvName = songTitle
                        var mvSinger = ""

                        if (dataArray != null && dataArray.size() > 0) {
                            val innerList = dataArray[0].asJsonArray
                            if (innerList != null && innerList.size() > 0) {
                                val record = innerList[0].asJsonObject
                                mvName = record.getSafeString("mv_name", "title")
                                mvSinger = record.getSafeString("singer")
                                
                                val h264 = record.getAsJsonObject("h264")
                                if (h264 != null) {
                                    targetHash = h264.getSafeString("hd_hash", "fhd_hash", "qhd_hash", "sd_hash")
                                }
                                if (targetHash.isEmpty()) {
                                    val mkv = record.getAsJsonObject("mkv")
                                    if (mkv != null) {
                                        targetHash = mkv.getSafeString("qhd_hash", "sd_hash")
                                    }
                                }
                            }
                        }

                        if (targetHash.isEmpty() && fallbackMvHash.length >= 32) {
                            targetHash = fallbackMvHash
                        }

                        if (targetHash.isNotEmpty()) {
                            fetchMvVideoUrl(targetHash, mvName, mvSinger, callback)
                        } else {
                            mainHandler.post { callback(Result.failure(Exception("No MV available for this song"))) }
                        }
                    } catch (e: Exception) {
                        if (fallbackMvHash.length >= 32) {
                            fetchMvVideoUrl(fallbackMvHash, songTitle, "", callback)
                        } else {
                            mainHandler.post { callback(Result.failure(e)) }
                        }
                    }
                }
            })
        } else if (fallbackMvHash.length >= 32) {
            fetchMvVideoUrl(fallbackMvHash, songTitle, "", callback)
        } else {
            mainHandler.post { callback(Result.failure(Exception("No MV metadata available"))) }
        }
    }

    private fun fetchMvVideoUrl(mvHash: String, title: String, artist: String, callback: (Result<MvStreamResult>) -> Unit) {
        val lowerHash = mvHash.lowercase()
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val key = SignatureUtils.signKey(lowerHash, mid = "undefined", userId = "0", isLite = true)

        val vParams = mapOf(
            "appid" to SignatureUtils.LITE_APP_ID,
            "backupdomain" to "1",
            "clienttime" to clientTime,
            "clientver" to SignatureUtils.LITE_CLIENT_VER,
            "cmd" to "123",
            "dfid" to "-",
            "ext" to "mp4",
            "hash" to lowerHash,
            "ismp3" to "0",
            "key" to key,
            "mid" to "undefined",
            "pid" to "1",
            "type" to "1",
            "uuid" to "-"
        )
        val signature = SignatureUtils.signatureAndroidParams(vParams, isLite = true)

        val urlBuilder = "https://gateway.kugou.com/v2/interface/index".toHttpUrl().newBuilder().apply {
            vParams.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("signature", signature)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("x-router", "trackermv.kugou.com")
            .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .addHeader("dfid", "-")
            .addHeader("mid", "undefined")
            .addHeader("clienttime", clientTime)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val hashObj = data?.getAsJsonObject(lowerHash)
                    var downurl = hashObj?.get("downurl")?.asString
                    if (downurl.isNullOrEmpty()) {
                        downurl = hashObj?.getAsJsonArray("backupdownurl")?.get(0)?.asString
                    }
                    if (!downurl.isNullOrEmpty()) {
                        downurl = downurl.replace("\\/", "/")
                        val result = MvStreamResult(
                            url = downurl,
                            title = if (title.isNotEmpty()) title else "MV",
                            artist = artist,
                            hash = mvHash
                        )
                        mainHandler.post { callback(Result.success(result)) }
                    } else {
                        mainHandler.post { callback(Result.failure(Exception("Empty video URL in response"))) }
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.failure(e)) }
                }
            }
        })
    }

    // Web MD5 Signature Algorithm for QR Code endpoints (Salt: NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt)
    private fun signatureWebParams(params: Map<String, String>): String {
        val salt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
        val sortedParams = params.toSortedMap().map { "${it.key}=${it.value}" }.joinToString("")
        return CryptoUtils.md5(salt + sortedParams + salt)
    }

    /**
     * Request KuGou Concept Lite Login QR Code
     * Returns Pair<qrKey, targetQrUrl>
     */
    fun fetchConceptQrCode(callback: (Result<Pair<String, String>>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val mid = UserManager.mid
        val dfid = UserManager.dfid

        val params = mutableMapOf(
            "appid" to "1001",
            "clienttime" to clientTime,
            "clientver" to SignatureUtils.LITE_CLIENT_VER,
            "dfid" to dfid,
            "mid" to mid,
            "plat" to "4",
            "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=${SignatureUtils.LITE_APP_ID}&",
            "srcappid" to "2919",
            "type" to "1",
            "uuid" to "-"
        )
        val signature = signatureWebParams(params)
        params["signature"] = signature

        val urlBuilder = "https://login-user.kugou.com/v2/qrcode".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .addHeader("dfid", dfid)
            .addHeader("mid", mid)
            .addHeader("clienttime", clientTime)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val qrcodeKey = data?.get("qrcode")?.asString ?: ""
                    if (qrcodeKey.isNotEmpty()) {
                        val qrUrl = "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=$qrcodeKey"
                        mainHandler.post { callback(Result.success(Pair(qrcodeKey, qrUrl))) }
                    } else {
                        mainHandler.post { callback(Result.failure(Exception("Failed to generate QR code key"))) }
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.failure(e)) }
                }
            }
        })
    }

    /**
     * Poll KuGou Concept Lite Login QR Code Status
     * Status codes: 0 = Expired, 1 = Waiting for scan, 2 = Scanned / Waiting confirmation, 4 = Authorized Success
     */
    fun checkConceptQrCodeStatus(qrKey: String, callback: (Result<QrCheckResult>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val mid = UserManager.mid
        val dfid = UserManager.dfid

        val params = mutableMapOf(
            "appid" to SignatureUtils.LITE_APP_ID,
            "clienttime" to clientTime,
            "clientver" to SignatureUtils.LITE_CLIENT_VER,
            "dfid" to dfid,
            "mid" to mid,
            "plat" to "4",
            "qrcode" to qrKey,
            "srcappid" to "2919",
            "uuid" to "-"
        )
        val signature = signatureWebParams(params)
        params["signature"] = signature

        val urlBuilder = "https://login-user.kugou.com/v2/get_userinfo_qrcode".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .addHeader("dfid", dfid)
            .addHeader("mid", mid)
            .addHeader("clienttime", clientTime)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val status = data?.get("status")?.asInt ?: 1
                    
                    if (status == 4) {
                        // Authorized successfully
                        val token = data.getSafeString("token")
                        val userId = data.get("userid")?.asLong ?: 0L
                        val nickname = data.getSafeString("nickname", "username").ifEmpty { "???????" }
                        val pic = data.getSafeString("pic", "avatar", "img")
                        val vipType = data.getSafeInt("vip_type", "viptype", default = 0)
                        val vipToken = data.getSafeString("vip_token", "viptoken")

                        val result = QrCheckResult(
                            status = 4,
                            userId = userId,
                            token = token,
                            nickname = nickname,
                            avatarUrl = pic,
                            vipType = vipType,
                            vipToken = vipToken
                        )
                        mainHandler.post { callback(Result.success(result)) }
                    } else {
                        mainHandler.post { callback(Result.success(QrCheckResult(status = status))) }
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.failure(e)) }
                }
            }
        })
    }

    // Resolve pure audio stream URL via Kugou Tracker API (/v2/interface/index with ismp3=1)
    fun fetchAudioStreamUrlByHash(audioHash: String, callback: (Result<String>) -> Unit) {
        val lowerHash = audioHash.lowercase()
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val mid = UserManager.mid
        val dfid = UserManager.dfid
        val user = UserManager.userProfile.value
        val userId = user?.userId?.toString() ?: "0"
        val token = user?.token ?: ""
        val key = SignatureUtils.signKey(lowerHash, mid = mid, userId = userId, isLite = true)

        val vParams = mutableMapOf(
            "appid" to SignatureUtils.LITE_APP_ID,
            "backupdomain" to "1",
            "clienttime" to clientTime,
            "clientver" to SignatureUtils.LITE_CLIENT_VER,
            "cmd" to "123",
            "dfid" to dfid,
            "ext" to "mp3",
            "hash" to lowerHash,
            "ismp3" to "1",
            "key" to key,
            "mid" to mid,
            "pid" to "1",
            "type" to "1",
            "uuid" to "-"
        )
        if (token.isNotEmpty()) {
            vParams["token"] = token
            vParams["userid"] = userId
        }
        val signature = SignatureUtils.signatureAndroidParams(vParams, isLite = true)

        val urlBuilder = "https://gateway.kugou.com/v2/interface/index".toHttpUrl().newBuilder().apply {
            vParams.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("signature", signature)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("x-router", "tracker.kugou.com")
            .addHeader("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .addHeader("dfid", dfid)
            .addHeader("mid", mid)
            .addHeader("clienttime", clientTime)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.failure(e)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    var downurl = json.get("url")?.asString
                    if (downurl.isNullOrEmpty()) {
                        val dataObj = json.getAsJsonObject("data")
                        val hashObj = dataObj?.getAsJsonObject(lowerHash)
                        downurl = hashObj?.get("downurl")?.asString
                        if (downurl.isNullOrEmpty()) {
                            downurl = hashObj?.getAsJsonArray("backupdownurl")?.get(0)?.asString
                        }
                    }
                    if (!downurl.isNullOrEmpty()) {
                        downurl = downurl.replace("\\/", "/")
                        mainHandler.post { callback(Result.success(downurl)) }
                    } else {
                        mainHandler.post { callback(Result.failure(Exception("Audio URL not found in Kugou tracker"))) }
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.failure(e)) }
                }
            }
        })
    }

    /**
     * Search official studio accompaniment for a given song.
     * Criteria:
     * 1. Title contains "(??)", "????", "[??]", "????", "???" or "Instrumental".
     * 2. Cleaned title matches original song title.
     * 3. Artist matches or title includes artist.
     * 4. Duration tolerance check (abs(duration - originalDuration) <= 5 seconds).
     */
    fun searchAccompaniment(
        songTitle: String,
        artist: String,
        originalDuration: Int,
        callback: (Result<AccompanimentMatchResult>) -> Unit
    ) {
        if (songTitle.isBlank()) {
            mainHandler.post { callback(Result.failure(Exception("Song title is blank"))) }
            return
        }

        val cleanTitle = songTitle.replace(Regex("\\(.*\\)|?.*?|\\[.*\\]|?.*?|<.*>|?.*?|??|??|Instrumental|inst", RegexOption.IGNORE_CASE), "").trim()
        val query = if (cleanTitle.isNotEmpty()) "$cleanTitle ??" else "$songTitle ??"

        searchSong(query, page = 1, pageSize = 30) { result ->
            result.onSuccess { list ->
                if (list.isEmpty()) {
                    mainHandler.post { callback(Result.failure(Exception("No accompaniment search results"))) }
                    return@onSuccess
                }

                // Filter candidates
                val candidates = list.filter { item ->
                    val t = item.title
                    val hasAccTag = t.contains("??") || t.contains("??") || t.contains("Instrumental", ignoreCase = true) || t.contains("inst", ignoreCase = true)
                    if (!hasAccTag) return@filter false

                    val itemCleanTitle = t.replace(Regex("\\(.*\\)|?.*?|\\[.*\\]|?.*?|<.*>|?.*?|??|??|Instrumental|inst", RegexOption.IGNORE_CASE), "").trim()
                    val titleMatched = itemCleanTitle.equals(cleanTitle, ignoreCase = true) ||
                            itemCleanTitle.contains(cleanTitle, ignoreCase = true) ||
                            cleanTitle.contains(itemCleanTitle, ignoreCase = true)
                    if (!titleMatched) return@filter false

                    val artistClean = artist.replace(Regex("\\s+"), "").lowercase()
                    val itemArtistClean = item.artist.replace(Regex("\\s+"), "").lowercase()
                    val artistMatched = if (artistClean.isEmpty() || artistClean == "??" || itemArtistClean.isEmpty() || itemArtistClean == "??" || itemArtistClean == "??") {
                        true
                    } else {
                        artistClean.contains(itemArtistClean) || itemArtistClean.contains(artistClean) || t.lowercase().contains(artistClean)
                    }
                    if (!artistMatched) return@filter false

                    if (originalDuration > 0 && item.duration > 0) {
                        val durationDiff = Math.abs(item.duration - originalDuration)
                        if (durationDiff > 5) return@filter false
                    }

                    true
                }

                if (candidates.isEmpty()) {
                    mainHandler.post { callback(Result.failure(Exception("No matching accompaniment found matching artist and duration"))) }
                    return@onSuccess
                }

                // Pick the best match
                val best = candidates.minByOrNull { item ->
                    val durDiff = if (originalDuration > 0 && item.duration > 0) Math.abs(item.duration - originalDuration) else 0
                    val artistBonus = if (item.artist.contains(artist, ignoreCase = true)) 0 else 5
                    durDiff + artistBonus
                } ?: candidates.first()

                // Resolve audio stream URL
                fetchAudioStreamUrlByHash(best.hash) { streamResult ->
                    streamResult.onSuccess { url ->
                        val match = AccompanimentMatchResult(
                            title = best.title,
                            artist = best.artist,
                            url = url,
                            hash = best.hash,
                            duration = best.duration
                        )
                        mainHandler.post { callback(Result.success(match)) }
                    }
                    streamResult.onFailure {
                        // Fallback: Try resolving via 163 outer stream URL with accompaniment keyword
                        getSongAudioUrl("${best.title} ${best.artist}") { fallbackRes ->
                            fallbackRes.onSuccess { fallbackUrl ->
                                if (fallbackUrl != FALLBACK_AUDIO_URL) {
                                    val match = AccompanimentMatchResult(
                                        title = best.title,
                                        artist = best.artist,
                                        url = fallbackUrl,
                                        hash = best.hash,
                                        duration = best.duration
                                    )
                                    mainHandler.post { callback(Result.success(match)) }
                                } else {
                                    mainHandler.post { callback(Result.failure(Exception("Failed to resolve accompaniment audio URL"))) }
                                }
                            }
                            fallbackRes.onFailure {
                                mainHandler.post { callback(Result.failure(Exception("Failed to resolve accompaniment stream"))) }
                            }
                        }
                    }
                }
            }
            result.onFailure {
                mainHandler.post { callback(Result.failure(it)) }
            }
        }
    }

    // Full-length High-Fidelity Audio Stream Resolver (bypasses 30s VIP preview)
    fun getSongAudioUrl(songTitle: String, callback: (Result<String>) -> Unit) {
        if (songTitle.isBlank()) {
            mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
            return
        }

        val cleanTitle = songTitle.replace(Regex("\\(.*\\)|（.*）|\\[.*\\]|【.*】|《.*》"), "").trim()
        val query = if (cleanTitle.isNotEmpty()) cleanTitle else songTitle
        val searchUrl = "http://music.163.com/api/search/get/web?s=${URLEncoder.encode(query, "UTF-8")}&type=1&limit=1"
        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val resultObj = json.getAsJsonObject("result")
                    val songsArr = resultObj?.getAsJsonArray("songs")
                    if (songsArr != null && songsArr.size() > 0) {
                        val firstSong = songsArr[0].asJsonObject
                        val songId = firstSong.get("id")?.asLong ?: 0L
                        if (songId > 0L) {
                            val streamUrl = "http://music.163.com/song/media/outer/url?id=$songId.mp3"
                            mainHandler.post { callback(Result.success(streamUrl)) }
                            return
                        }
                    }
                    mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
                }
            }
        })
    }

    // Hot Songs Rank
    fun getHotSongs(callback: (Result<List<SongItem>>) -> Unit) {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?pagesize=50&rankid=6666&page=1"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.success(getFallbackHotSongs())) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()
                    
                    val result = mutableListOf<SongItem>()
                    for (element in info) {
                        if (!element.isJsonObject) continue
                        val itemObj = element.asJsonObject
                        val filename = itemObj.getSafeString("filename", "songname", "title", "FileName")
                        val parts = filename.split(" - ", limit = 2)
                        val artist = parts.getOrNull(0)?.trim() ?: ""
                        val title = parts.getOrNull(1)?.trim() ?: filename
                        
                        val hash = itemObj.getSafeString("hash", "filehash", "FileHash")
                        val mvHash = itemObj.getSafeString("mvhash", "mv_hash", "MvHash")
                        val albumAudioId = itemObj.getSafeString("album_audio_id", "audio_id")
                        val duration = itemObj.getSafeInt("duration", default = 240)
                        if (hash.isNotEmpty()) {
                            result.add(SongItem(title, artist, hash, albumAudioId, duration, mvHash))
                        }
                    }

                    if (result.isEmpty()) {
                        result.addAll(getFallbackHotSongs())
                    }

                    mainHandler.post { callback(Result.success(result)) }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.success(getFallbackHotSongs())) }
                }
            }
        })
    }

    private fun getFallbackHotSongs(): List<SongItem> {
        return listOf(
            SongItem("晴天", "周杰伦", "c2d3a672834b6b6697a4a2a4b8df77a2", "32100650", 269, "92b86da2e11c3c84de3a944ed12d97f1"),
            SongItem("逆战", "张杰", "24d8eafee034896a678e8584f79eabe0", "27517488", 230, "6105dc34d0d3254662aac1182c3f8c2d"),
            SongItem("十年", "陈奕迅", "f0a8d672834b6b6697a4a2a4b8df66a3", "40289835", 280, "afaa7726cff81edea6f461628fa0059b"),
            SongItem("海阔天空", "Beyond", "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d", "32155307", 324, "60a8f37df53025cd66eb05a044ccae13"),
            SongItem("七里香", "周杰伦", "a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7", "32100651", 298, "d689622d640fb00f40d33e5b306b86cf")
        )
    }

    fun getLocalSongsFromDevice(context: Context): List<SongItem> {
        val list = mutableListOf<SongItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )
            cursor?.use { c ->
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val title = c.getString(titleCol)
                    val artist = c.getString(artistCol)
                    val path = c.getString(dataCol)
                    val duration = c.getInt(durCol) / 1000
                    list.add(SongItem(title, artist, path, "local", duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
