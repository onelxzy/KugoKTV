package com.echo.ktv.api

import android.util.Log
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
    private const val TAG = "KugouApi"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private const val mid = "2882303761517560020"
    private const val dfid = "-"
    private const val uuid = "-"
    private const val GATEWAY = "https://gateway.kugou.com"

    private fun getGatewayHeaders(clientTime: String): Headers {
        return Headers.Builder()
            .add("dfid", dfid)
            .add("mid", mid)
            .add("clienttime", clientTime)
            .add("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .build()
    }

    // ======================== SEARCH ========================
    // Uses public CDN endpoint — no signature needed, confirmed working format

    fun searchSong(keyword: String, callback: (Result<List<SongItem>>) -> Unit) {
        val url = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}&page=1&pagesize=30&showtype=1"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "searchSong failed", e)
                callback(Result.success(emptyList()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "searchSong response: ${body.take(200)}")
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()
                    val result = parseSongList(info)
                    callback(Result.success(result))
                } catch (e: Exception) {
                    Log.e(TAG, "searchSong parse error", e)
                    callback(Result.success(emptyList()))
                }
            }
        })
    }

    fun searchMV(keyword: String, callback: (Result<List<MvItem>>) -> Unit) {
        val url = "http://mobilecdn.kugou.com/api/v3/search/mv?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}&page=1&pagesize=30"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "searchMV failed", e)
                callback(Result.success(emptyList()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "searchMV response: ${body.take(200)}")
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()
                    val result = mutableListOf<MvItem>()
                    for (element in info) {
                        val obj = element.asJsonObject
                        val filename = obj.get("filename")?.asString ?: ""
                        val parts = filename.split(" - ", limit = 2)
                        val artist = parts.getOrNull(0)?.trim() ?: ""
                        val title = parts.getOrNull(1)?.trim() ?: filename
                        val mvHash = obj.get("mvhash")?.asString
                            ?: obj.get("hash")?.asString ?: ""
                        val duration = obj.get("duration")?.asInt ?: 0
                        val imgUrl = obj.get("imgurl")?.asString ?: ""
                        if (mvHash.isNotEmpty()) {
                            result.add(MvItem(title, artist, mvHash, duration, imgUrl))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    Log.e(TAG, "searchMV parse error", e)
                    callback(Result.success(emptyList()))
                }
            }
        })
    }

    // ======================== PLAYBACK URLs ========================
    // getSongUrl: follows EchoMusic song_url.js flow
    // Gateway /v5/url with signature (notSign in song_url.js doesn't match notSignature in request.js,
    // so signature IS generated) and encryptKey (key param added)

    fun getSongUrl(hash: String, albumAudioId: String, callback: (Result<String>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val hashLower = hash.lowercase()

        // Build params matching EchoMusic's song_url.js + request.js defaultParams merge
        val params = mutableMapOf(
            "hash" to hashLower,
            "album_id" to "0",
            "album_audio_id" to albumAudioId,
            "area_code" to "1",
            "behavior" to "play",
            "pid" to "411",
            "cmd" to "26",
            "pidversion" to "3001",
            "quality" to "128",
            "version" to "11430",
            "page_id" to "967177915",
            "ssa_flag" to "is_fromtrack",
            "ppage_id" to "356753938,823673182,967485191",
            "cdnBackup" to "1",
            "module" to "",
            "clientver" to "11430",
            "IsFreePart" to "0",
            // Default params that request.js merges in
            "dfid" to dfid,
            "mid" to mid,
            "uuid" to uuid,
            "appid" to SignatureUtils.APP_ID,
            "clienttime" to clientTime
        )
        // encryptKey: add key param
        params["key"] = SignatureUtils.signKey(hashLower, mid)
        // signature is computed after all params are merged
        params["signature"] = SignatureUtils.signatureAndroidParams(params)

        val urlBuilder = "$GATEWAY/v5/url".toHttpUrl().newBuilder()
        for ((k, v) in params) urlBuilder.addQueryParameter(k, v)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getGatewayHeaders(clientTime))
            .addHeader("x-router", "trackercdn.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "getSongUrl failed", e)
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "getSongUrl response: ${body.take(300)}")
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val playUrl = extractUrlFromResponse(json)
                    if (playUrl != null) {
                        callback(Result.success(playUrl))
                    } else {
                        callback(Result.failure(Exception("No song URL in response")))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "getSongUrl parse error", e)
                    callback(Result.failure(e))
                }
            }
        })
    }

    // getMvUrl: follows EchoMusic video_url.js → /v2/interface/index via trackermv.kugou.com
    fun getMvUrl(mvHash: String, callback: (Result<String>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()

        val params = mutableMapOf(
            "backupdomain" to "1",
            "cmd" to "123",
            "ext" to "mp4",
            "ismp3" to "0",
            "hash" to mvHash,
            "pid" to "1",
            "type" to "1",
            // Default params from request.js
            "dfid" to dfid,
            "mid" to mid,
            "uuid" to uuid,
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTime
        )
        // encryptKey
        params["key"] = SignatureUtils.signKey(mvHash, mid)
        // signature
        params["signature"] = SignatureUtils.signatureAndroidParams(params)

        val urlBuilder = "$GATEWAY/v2/interface/index".toHttpUrl().newBuilder()
        for ((k, v) in params) urlBuilder.addQueryParameter(k, v)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getGatewayHeaders(clientTime))
            .addHeader("x-router", "trackermv.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "getMvUrl failed", e)
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "getMvUrl response: ${body.take(300)}")
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val mvdata = json.getAsJsonObject("mvdata")
                    val downurl = mvdata?.getAsJsonObject("le")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("rq")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("sq")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("sd")?.get("downurl")?.asString
                        ?: json.get("mvUrl")?.asString

                    if (!downurl.isNullOrEmpty()) {
                        callback(Result.success(downurl.replace("\\/", "/")))
                    } else {
                        callback(Result.failure(Exception("No MV URL found")))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "getMvUrl parse error", e)
                    callback(Result.failure(e))
                }
            }
        })
    }

    // ======================== HOT SONGS (RANK) ========================
    fun getHotSongs(callback: (Result<List<SongItem>>) -> Unit) {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?pagesize=50&rankid=6666&page=1"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "getHotSongs failed", e)
                callback(Result.success(emptyList()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()
                    callback(Result.success(parseSongList(info)))
                } catch (e: Exception) {
                    Log.e(TAG, "getHotSongs parse error", e)
                    callback(Result.success(emptyList()))
                }
            }
        })
    }

    // ======================== Helpers ========================
    private fun parseSongList(info: JsonArray): List<SongItem> {
        val result = mutableListOf<SongItem>()
        for (element in info) {
            val obj = element.asJsonObject
            val filename = obj.get("filename")?.asString ?: ""
            val parts = filename.split(" - ", limit = 2)
            val artist = parts.getOrNull(0)?.trim() ?: ""
            val title = parts.getOrNull(1)?.trim() ?: filename
            val hash = obj.get("hash")?.asString ?: ""
            val albumAudioId = obj.get("album_audio_id")?.asString ?: ""
            val duration = obj.get("duration")?.asInt ?: 0
            if (hash.isNotEmpty()) {
                result.add(SongItem(title, artist, hash, albumAudioId, duration))
            }
        }
        return result
    }

    private fun extractUrlFromResponse(json: JsonObject): String? {
        // Try multiple response formats
        // Format: { "url": ["http://..."] }
        json.getAsJsonArray("url")?.let { arr ->
            if (arr.size() > 0) arr[0].asString?.takeIf { it.isNotEmpty() }?.let { return it.replace("\\/", "/") }
        }
        // Format: { "data": { "play_url": "..." } }
        json.getAsJsonObject("data")?.let { data ->
            data.get("play_url")?.asString?.takeIf { it.isNotEmpty() }?.let { return it.replace("\\/", "/") }
            data.get("url")?.asString?.takeIf { it.isNotEmpty() }?.let { return it.replace("\\/", "/") }
        }
        // Format: { "data": [{ "url": "..." }] }
        json.getAsJsonArray("data")?.let { arr ->
            if (arr.size() > 0) {
                arr[0].asJsonObject?.get("url")?.asString?.takeIf { it.isNotEmpty() }?.let { return it.replace("\\/", "/") }
                arr[0].asJsonObject?.get("play_url")?.asString?.takeIf { it.isNotEmpty() }?.let { return it.replace("\\/", "/") }
            }
        }
        // Format: { "backupUrl": ["http://..."] }
        json.getAsJsonArray("backupUrl")?.let { arr ->
            if (arr.size() > 0) arr[0].asString?.takeIf { it.isNotEmpty() }?.let { return it.replace("\\/", "/") }
        }
        return null
    }
}
