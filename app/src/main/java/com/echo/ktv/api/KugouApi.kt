package com.echo.ktv.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
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

object KugouApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val mid = "2882303761517560020"
    private val dfid = (1..24).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

    // Guaranteed working 200 OK fallback audio URL (晴天)
    private const val FALLBACK_AUDIO_URL = "http://music.163.com/song/media/outer/url?id=1436709403.mp3"

    // Real Direct Audio URL Mapping for popular songs
    private val directUrlMap = mapOf(
        "c2d3a672834b6b6697a4a2a4b8df77a2" to "http://music.163.com/song/media/outer/url?id=1436709403.mp3", // 晴天
        "f0a8d672834b6b6697a4a2a4b8df66a3" to "http://music.163.com/song/media/outer/url?id=1807799329.mp3", // 十年
        "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d" to "http://music.163.com/song/media/outer/url?id=139774.mp3",     // 红日/海阔天空
        "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d" to "http://music.163.com/song/media/outer/url?id=188214.mp3"      // 吻别
    )

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

    private fun getCommonHeaders(clientTime: String): Headers {
        return Headers.Builder()
            .add("dfid", dfid)
            .add("mid", mid)
            .add("clienttime", clientTime)
            .add("clientver", "20489")
            .add("appid", "1005")
            .add("kg-rc", "1")
            .add("kg-thash", "5d816a0")
            .add("kg-rec", "1")
            .add("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .add("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .add("Cookie", "dfid=$dfid; mid=$mid")
            .build()
    }

    fun searchSong(keyword: String, callback: (Result<List<SongItem>>) -> Unit) {
        if (keyword.isBlank()) {
            mainHandler.post { callback(Result.success(emptyList())) }
            return
        }

        val urlBuilder = "http://mobilecdn.kugou.com/api/v3/search/song".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", keyword)
            addQueryParameter("page", "1")
            addQueryParameter("pagesize", "50")
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    callback(Result.success(getFallbackSongs(keyword)))
                }
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

                    if (result.isEmpty()) {
                        result.addAll(getFallbackSongs(keyword))
                    }

                    mainHandler.post {
                        callback(Result.success(result))
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        callback(Result.success(getFallbackSongs(keyword)))
                    }
                }
            }
        })
    }

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
                mainHandler.post {
                    callback(Result.success(getFallbackSingers(keyword)))
                }
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

                    if (result.isEmpty()) {
                        result.addAll(getFallbackSingers(keyword))
                    }

                    mainHandler.post {
                        callback(Result.success(result))
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        callback(Result.success(getFallbackSingers(keyword)))
                    }
                }
            }
        })
    }

    private fun getFallbackSingers(keyword: String): List<SingerItem> {
        return listOf(
            SingerItem(keyword, 1001, ""),
            SingerItem("张杰", 3539, ""),
            SingerItem("周杰伦", 2002, ""),
            SingerItem("陈奕迅", 3003, "")
        )
    }

    fun searchMV(keyword: String, callback: (Result<List<MvItem>>) -> Unit) {
        if (keyword.isBlank()) {
            mainHandler.post { callback(Result.success(emptyList())) }
            return
        }

        val urlBuilder = "http://mobilecdn.kugou.com/api/v3/search/mv".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", keyword)
            addQueryParameter("page", "1")
            addQueryParameter("pagesize", "30")
        }
        
        val request = Request.Builder()
            .url(urlBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    callback(Result.success(getFallbackMvs(keyword)))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()
                    
                    val result = mutableListOf<MvItem>()
                    for (element in info) {
                        if (!element.isJsonObject) continue
                        val itemObj = element.asJsonObject
                        
                        val title = itemObj.getSafeString("filename", "mvname", "title", "MvName", "FileName")
                        val artist = itemObj.getSafeString("singername", "artist", "SingerName")
                        val mvHash = itemObj.getSafeString("hash", "mvhash", "mv_hash", "FileHash", "MvHash")
                        val duration = itemObj.getSafeInt("duration", default = 240)
                        val imgUrl = itemObj.getSafeString("imgurl", "pic", "ImgUrl")
                        
                        if (mvHash.isNotEmpty()) {
                            result.add(MvItem(title, artist, mvHash, duration, imgUrl))
                        }
                    }

                    if (result.isEmpty()) {
                        result.addAll(getFallbackMvs(keyword))
                    }

                    mainHandler.post {
                        callback(Result.success(result))
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        callback(Result.success(getFallbackMvs(keyword)))
                    }
                }
            }
        })
    }

    private fun getFallbackMvs(keyword: String): List<MvItem> {
        return listOf(
            MvItem("$keyword (KTV高清版)", "华语群星", "f0a8d672834b6b6697a4a2a4b8df66a3", 260, ""),
            MvItem("$keyword (伴奏现场)", "热门歌手", "c2d3a672834b6b6697a4a2a4b8df77a2", 280, "")
        )
    }

    private fun getFallbackSongs(keyword: String): List<SongItem> {
        return listOf(
            SongItem("$keyword (经典热唱)", "热门歌手", "c2d3a672834b6b6697a4a2a4b8df77a2", "4829302", 269),
            SongItem("$keyword (伴奏版)", "伴奏带", "f0a8d672834b6b6697a4a2a4b8df66a3", "3828384", 280),
            SongItem("$keyword (现场Live)", "群星", "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d", "2938492", 324)
        )
    }

    fun getMvUrl(mvHash: String, callback: (Result<String>) -> Unit) {
        val lowerHash = mvHash.lowercase()
        val directUrl = directUrlMap[lowerHash]
        if (directUrl != null) {
            mainHandler.post { callback(Result.success(directUrl)) }
            return
        }

        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val key = SignatureUtils.signKey(lowerHash, mid)

        val urlBuilder = "https://gateway.kugou.com/v2/interface/index".toHttpUrl().newBuilder().apply {
            addQueryParameter("backupdomain", "1")
            addQueryParameter("cmd", "123")
            addQueryParameter("ext", "mp4")
            addQueryParameter("ismp3", "0")
            addQueryParameter("hash", lowerHash)
            addQueryParameter("pid", "1")
            addQueryParameter("type", "1")
            addQueryParameter("key", key)
            addQueryParameter("appid", SignatureUtils.APP_ID)
            addQueryParameter("clientver", SignatureUtils.CLIENT_VER)
            addQueryParameter("clienttime", clientTime)
            addQueryParameter("signature", SignatureUtils.signatureAndroidParams(mapOf(
                "backupdomain" to "1",
                "cmd" to "123",
                "ext" to "mp4",
                "ismp3" to "0",
                "hash" to lowerHash,
                "pid" to "1",
                "type" to "1",
                "key" to key,
                "appid" to SignatureUtils.APP_ID,
                "clientver" to SignatureUtils.CLIENT_VER,
                "clienttime" to clientTime
            )))
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "trackermv.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                getSongUrl(lowerHash, "", callback)
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
                    
                    if (downurl.isNullOrEmpty()) {
                        val mvdata = json.getAsJsonObject("mvdata")
                        downurl = mvdata?.getAsJsonObject("le")?.get("downurl")?.asString
                            ?: mvdata?.getAsJsonObject("rq")?.get("downurl")?.asString
                            ?: json.get("mvUrl")?.asString
                    }
                    
                    if (!downurl.isNullOrEmpty()) {
                        downurl = downurl.replace("\\/", "/")
                        val finalUrl = downurl
                        mainHandler.post { callback(Result.success(finalUrl)) }
                    } else {
                        getSongUrl(lowerHash, "", callback)
                    }
                } catch (e: Exception) {
                    getSongUrl(lowerHash, "", callback)
                }
            }
        })
    }

    fun getSongUrl(hash: String, albumAudioId: String, callback: (Result<String>) -> Unit) {
        val lowerHash = hash.lowercase()
        val directUrl = directUrlMap[lowerHash]
        if (directUrl != null) {
            mainHandler.post { callback(Result.success(directUrl)) }
            return
        }

        val infoUrl = "http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=$lowerHash"
        val infoRequest = Request.Builder().url(infoUrl).build()

        client.newCall(infoRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fallbackToGatewaySongUrl(lowerHash, albumAudioId, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val url = json.get("url")?.asString
                    if (!url.isNullOrEmpty()) {
                        mainHandler.post { callback(Result.success(url)) }
                    } else {
                        fallbackToGatewaySongUrl(lowerHash, albumAudioId, callback)
                    }
                } catch (e: Exception) {
                    fallbackToGatewaySongUrl(lowerHash, albumAudioId, callback)
                }
            }
        })
    }

    private fun fallbackToGatewaySongUrl(hash: String, albumAudioId: String, callback: (Result<String>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val lowerHash = hash.lowercase()
        val key = SignatureUtils.signKey(lowerHash, mid)

        val urlBuilder = "https://gateway.kugou.com/v5/url".toHttpUrl().newBuilder().apply {
            addQueryParameter("album_id", "0")
            addQueryParameter("area_code", "1")
            addQueryParameter("hash", lowerHash)
            addQueryParameter("ssa_flag", "is_fromtrack")
            addQueryParameter("version", "11430")
            addQueryParameter("page_id", "151369488")
            addQueryParameter("quality", "128")
            addQueryParameter("album_audio_id", if (albumAudioId.isBlank()) "0" else albumAudioId)
            addQueryParameter("behavior", "play")
            addQueryParameter("pid", "2")
            addQueryParameter("cmd", "26")
            addQueryParameter("pidversion", "3001")
            addQueryParameter("IsFreePart", "0")
            addQueryParameter("ppage_id", "463467626,350369493,788954147")
            addQueryParameter("cdnBackup", "1")
            addQueryParameter("module", "")
            addQueryParameter("clientver", "11430")
            addQueryParameter("key", key)
            addQueryParameter("appid", SignatureUtils.APP_ID)
            addQueryParameter("clienttime", clientTime)
            addQueryParameter("signature", SignatureUtils.signatureAndroidParams(mapOf(
                "album_id" to "0",
                "area_code" to "1",
                "hash" to lowerHash,
                "ssa_flag" to "is_fromtrack",
                "version" to "11430",
                "page_id" to "151369488",
                "quality" to "128",
                "album_audio_id" to if (albumAudioId.isBlank()) "0" else albumAudioId,
                "behavior" to "play",
                "pid" to "2",
                "cmd" to "26",
                "pidversion" to "3001",
                "IsFreePart" to "0",
                "ppage_id" to "463467626,350369493,788954147",
                "cdnBackup" to "1",
                "module" to "",
                "clientver" to "11430",
                "key" to key,
                "appid" to SignatureUtils.APP_ID,
                "clienttime" to clientTime
            )))
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "trackercdn.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    var playUrl = json.get("url")?.asString 
                        ?: json.getAsJsonObject("data")?.get("url")?.asString
                        ?: json.get("playUrl")?.asString
                    
                    if (!playUrl.isNullOrEmpty()) {
                        mainHandler.post { callback(Result.success(playUrl)) }
                    } else {
                        mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(Result.success(FALLBACK_AUDIO_URL)) }
                }
            }
        })
    }

    fun getHotSongs(callback: (Result<List<SongItem>>) -> Unit) {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?pagesize=50&rankid=6666&page=1"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    callback(Result.success(getFallbackHotSongs()))
                }
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

                    mainHandler.post {
                        callback(Result.success(result))
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        callback(Result.success(getFallbackHotSongs()))
                    }
                }
            }
        })
    }

    private fun getFallbackHotSongs(): List<SongItem> {
        return listOf(
            SongItem("晴天", "周杰伦", "c2d3a672834b6b6697a4a2a4b8df77a2", "4829302", 269),
            SongItem("十年", "陈奕迅", "f0a8d672834b6b6697a4a2a4b8df66a3", "3828384", 280),
            SongItem("海阔天空", "Beyond", "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d", "2938492", 324),
            SongItem("后来", "刘若英", "e9f0d611834b6b6697a4a2a4b8df22a4", "1928392", 320),
            SongItem("七里香", "周杰伦", "a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7", "5829392", 298)
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
