package com.echo.ktv.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SongItem(
    val title: String,
    val artist: String,
    val hash: String,
    val albumAudioId: String,
    val duration: Int
)

data class MvItem(
    val title: String,
    val artist: String,
    val mvHash: String,
    val duration: Int,
    val imgUrl: String
)

object KugouApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private const val mid = "2882303761517560020"
    private const val BASE_URL = "https://gateway.kugou.com"

    private fun getCommonHeaders(clientTime: String): Headers {
        return Headers.Builder()
            .add("DF", "0")
            .add("Mid", mid)
            .add("Uuid", "0")
            .add("clienttime", clientTime)
            .add("clientver", SignatureUtils.CLIENT_VER)
            .add("appid", SignatureUtils.APP_ID)
            .add("User-Agent", "Android712-AndroidPhone-8983-18-0-wifi")
            .build()
    }

    private fun randomDfid(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..24).map { chars.random() }.joinToString("")
    }

    // ======================== SEARCH ========================
    // Restored gateway-signed search matching EchoMusic search.js
    // Uses /v3/search/song with x-router: complexsearch.kugou.com

    fun searchSong(keyword: String, callback: (Result<List<SongItem>>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val params = mutableMapOf(
            "keyword" to keyword,
            "page" to "1",
            "pagesize" to "30",
            "platform" to "AndroidFilter",
            "iscorrection" to "1",
            "albumhide" to "0",
            "nocollect" to "0",
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTime
        )
        params["signature"] = SignatureUtils.signatureAndroidParams(params)

        val urlBuilder = "$BASE_URL/v3/search/song".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "complexsearch.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.success(emptyList()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: data?.getAsJsonArray("lists") ?: JsonArray()
                    
                    val result = mutableListOf<SongItem>()
                    for (element in info) {
                        val itemObj = element.asJsonObject
                        val filename = itemObj.get("filename")?.asString ?: ""
                        val parts = filename.split(" - ", limit = 2)
                        val artist = parts.getOrNull(0)?.trim() ?: ""
                        val title = parts.getOrNull(1)?.trim() ?: filename
                        
                        val hash = itemObj.get("hash")?.asString ?: ""
                        val albumAudioId = itemObj.get("album_audio_id")?.asString
                            ?: itemObj.get("AlbumAudioId")?.asString ?: ""
                        val duration = itemObj.get("duration")?.asInt ?: 0
                        if (hash.isNotEmpty()) {
                            result.add(SongItem(title, artist, hash, albumAudioId, duration))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    callback(Result.success(emptyList()))
                }
            }
        })
    }

    fun searchMV(keyword: String, callback: (Result<List<MvItem>>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val params = mutableMapOf(
            "keyword" to keyword,
            "page" to "1",
            "pagesize" to "30",
            "platform" to "AndroidFilter",
            "iscorrection" to "1",
            "albumhide" to "0",
            "nocollect" to "0",
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTime
        )
        params["signature"] = SignatureUtils.signatureAndroidParams(params)

        val urlBuilder = "$BASE_URL/v1/search/mv".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "complexsearch.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.success(emptyList()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: data?.getAsJsonArray("lists") ?: JsonArray()
                    
                    val result = mutableListOf<MvItem>()
                    for (element in info) {
                        val itemObj = element.asJsonObject
                        val title = itemObj.get("mvname")?.asString
                            ?: itemObj.get("songname")?.asString ?: ""
                        val artist = itemObj.get("singername")?.asString ?: ""
                        val mvHash = itemObj.get("mvhash")?.asString
                            ?: itemObj.get("hash")?.asString ?: ""
                        val duration = itemObj.get("duration")?.asInt ?: 0
                        val imgUrl = itemObj.get("imgurl")?.asString ?: ""
                        if (mvHash.isNotEmpty()) {
                            result.add(MvItem(title, artist, mvHash, duration, imgUrl))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    callback(Result.success(emptyList()))
                }
            }
        })
    }

    // ======================== PLAYBACK URLS ========================
    // Song URL: follows EchoMusic song_url.js → GET /v5/url via trackercdn.kugou.com
    // with encryptKey (key param) and NO signature (notSign)

    fun getSongUrl(hash: String, albumAudioId: String, callback: (Result<String>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val hashLower = hash.lowercase()
        val key = SignatureUtils.signKey(hashLower, mid)

        val urlBuilder = "$BASE_URL/v5/url".toHttpUrl().newBuilder()
            .addQueryParameter("hash", hashLower)
            .addQueryParameter("album_id", "0")
            .addQueryParameter("album_audio_id", albumAudioId)
            .addQueryParameter("area_code", "1")
            .addQueryParameter("behavior", "play")
            .addQueryParameter("pid", "411")
            .addQueryParameter("cmd", "26")
            .addQueryParameter("pidversion", "3001")
            .addQueryParameter("quality", "128")
            .addQueryParameter("version", "11430")
            .addQueryParameter("page_id", "967177915")
            .addQueryParameter("ssa_flag", "is_fromtrack")
            .addQueryParameter("ppage_id", "356753938,823673182,967485191")
            .addQueryParameter("cdnBackup", "1")
            .addQueryParameter("module", "")
            .addQueryParameter("clientver", "11430")
            .addQueryParameter("key", key)
            .addQueryParameter("appid", SignatureUtils.APP_ID)
            .addQueryParameter("clienttime", clientTime)
            .addQueryParameter("IsFreePart", "0")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "trackercdn.kugou.com")
            .addHeader("dfid", randomDfid())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    var playUrl: String? = null

                    // Format 1: { "url": ["http://..."] }
                    val urlArr = json.getAsJsonArray("url")
                    if (urlArr != null && urlArr.size() > 0) {
                        playUrl = urlArr[0].asString
                    }
                    // Format 2: { "data": { "url": "..." } }
                    if (playUrl.isNullOrEmpty()) {
                        playUrl = json.getAsJsonObject("data")?.get("play_url")?.asString
                    }
                    if (playUrl.isNullOrEmpty()) {
                        playUrl = json.getAsJsonObject("data")?.get("url")?.asString
                    }
                    // Format 3: { "data": [{ "url": "..." }] }
                    if (playUrl.isNullOrEmpty()) {
                        val dataArr = json.getAsJsonArray("data")
                        if (dataArr != null && dataArr.size() > 0) {
                            playUrl = dataArr[0].asJsonObject?.get("url")?.asString
                        }
                    }
                    // Format 4: backup urls
                    if (playUrl.isNullOrEmpty()) {
                        val backupArr = json.getAsJsonArray("backupUrl")
                        if (backupArr != null && backupArr.size() > 0) {
                            playUrl = backupArr[0].asString
                        }
                    }

                    if (!playUrl.isNullOrEmpty()) {
                        callback(Result.success(playUrl.replace("\\/", "/")))
                    } else {
                        callback(Result.failure(Exception("No song URL found: $body")))
                    }
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
        })
    }

    // MV URL: follows EchoMusic video_url.js → GET /v2/interface/index via trackermv.kugou.com
    // with encryptKey (key param) AND signature

    fun getMvUrl(mvHash: String, callback: (Result<String>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val key = SignatureUtils.signKey(mvHash, mid)

        val params = mutableMapOf(
            "backupdomain" to "1",
            "cmd" to "123",
            "ext" to "mp4",
            "ismp3" to "0",
            "hash" to mvHash,
            "pid" to "1",
            "type" to "1",
            "key" to key,
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTime
        )
        params["signature"] = SignatureUtils.signatureAndroidParams(params)

        val urlBuilder = "$BASE_URL/v2/interface/index".toHttpUrl().newBuilder()
        for ((k, v) in params) {
            urlBuilder.addQueryParameter(k, v)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "trackermv.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val mvdata = json.getAsJsonObject("mvdata")
                    var downurl = mvdata?.getAsJsonObject("le")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("rq")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("sq")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("sd")?.get("downurl")?.asString
                        ?: json.get("mvUrl")?.asString

                    if (!downurl.isNullOrEmpty()) {
                        callback(Result.success(downurl.replace("\\/", "/")))
                    } else {
                        callback(Result.failure(Exception("No MV URL: $body")))
                    }
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
        })
    }

    // ======================== HOT SONGS (RANK) ========================
    // Uses public unsigned CDN endpoint (confirmed working)

    fun getHotSongs(callback: (Result<List<SongItem>>) -> Unit) {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?pagesize=50&rankid=6666&page=1"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.success(emptyList()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()

                    val result = mutableListOf<SongItem>()
                    for (element in info) {
                        val itemObj = element.asJsonObject
                        val filename = itemObj.get("filename")?.asString ?: ""
                        val parts = filename.split(" - ", limit = 2)
                        val artist = parts.getOrNull(0)?.trim() ?: ""
                        val title = parts.getOrNull(1)?.trim() ?: filename

                        val hash = itemObj.get("hash")?.asString ?: ""
                        val albumAudioId = itemObj.get("album_audio_id")?.asString ?: ""
                        val duration = itemObj.get("duration")?.asInt ?: 0
                        if (hash.isNotEmpty()) {
                            result.add(SongItem(title, artist, hash, albumAudioId, duration))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    callback(Result.success(emptyList()))
                }
            }
        })
    }
}
