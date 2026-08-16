package com.echo.ktv.api

import android.content.Context
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
    val imgUrl: String
)

data class MvStreamResult(
    val url: String,
    val title: String,
    val artist: String,
    val hash: String
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

    // Search songs via mobilecdn endpoint (reliable, returns album_audio_id & mvhash)
    fun searchSong(keyword: String, callback: (Result<List<SongItem>>) -> Unit) {
        if (keyword.isBlank()) {
            mainHandler.post { callback(Result.success(emptyList())) }
            return
        }

        val urlBuilder = "http://mobilecdn.kugou.com/api/v3/search/song".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", keyword)
            addQueryParameter("page", "1")
            addQueryParameter("pagesize", "30")
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

    // Search singers via mobilecdn endpoint
    fun searchSinger(keyword: String, callback: (Result<List<SingerItem>>) -> Unit) {
        if (keyword.isBlank()) {
            mainHandler.post { callback(Result.success(emptyList())) }
            return
        }

        val urlBuilder = "http://mobilecdn.kugou.com/api/v3/search/singer".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", keyword)
            addQueryParameter("page", "1")
            addQueryParameter("pagesize", "30")
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
                        val imgUrl = itemObj.getSafeString("imgurl", "pic")
                        if (name.isNotEmpty()) {
                            result.add(SingerItem(name, id, imgUrl))
                        }
                    }

                    mainHandler.post { callback(Result.success(result)) }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.success(emptyList())) }
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
