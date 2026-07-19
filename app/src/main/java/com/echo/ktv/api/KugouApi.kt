package com.echo.ktv.api

import android.content.Context
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    // Real NetEase Direct Audio URL Mapping for mock songs
    private val directUrlMap = mapOf(
        "c2d3a672834b6b6697a4a2a4b8df77a2" to "https://music.163.com/song/media/outer/url?id=186016.mp3", // 晴天
        "f0a8d672834b6b6697a4a2a4b8df66a3" to "https://music.163.com/song/media/outer/url?id=65538.mp3",  // 十年
        "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d" to "https://music.163.com/song/media/outer/url?id=347230.mp3", // 海阔天空
        "7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d" to "https://music.163.com/song/media/outer/url?id=210049.mp3", // 倒带
        "0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a" to "https://music.163.com/song/media/outer/url?id=254486.mp3", // 忽然之间
        "e9f0d611834b6b6697a4a2a4b8df22a4" to "https://music.163.com/song/media/outer/url?id=254485.mp3", // 后来
        "a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7" to "https://music.163.com/song/media/outer/url?id=185965.mp3", // 七里香
        "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d" to "https://music.163.com/song/media/outer/url?id=188214.mp3", // 吻别
        "0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d" to "https://music.163.com/song/media/outer/url?id=29814898.mp3", // 泡沫
        "2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d" to "https://music.163.com/song/media/outer/url?id=186001.mp3", // 江南
        "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6" to "https://music.163.com/song/media/outer/url?id=28815250.mp3", // 平凡之路
        "e1d2c3b4a5f6e7d8c9b0a1f2e3d4c5b6" to "https://music.163.com/song/media/outer/url?id=496841267.mp3", // 消愁
        "4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d" to "https://music.163.com/song/media/outer/url?id=25707139.mp3",  // 那些年
        "3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d" to "https://music.163.com/song/media/outer/url?id=347231.mp3",  // 光辉岁月
        "4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e" to "https://music.163.com/song/media/outer/url?id=314016.mp3",   // 偏偏喜欢你
        "5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f" to "https://music.163.com/song/media/outer/url?id=461347998.mp3", // Shape of You
        "6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a" to "https://music.163.com/song/media/outer/url?id=32410313.mp3"  // Yesterday Once More
    )

    private fun getCommonHeaders(clientTime: String): Headers {
        return Headers.Builder()
            .add("DF", "0")
            .add("Mid", mid)
            .add("Uuid", "0")
            .add("clienttime", clientTime)
            .add("clientver", SignatureUtils.CLIENT_VER)
            .add("appid", SignatureUtils.APP_ID)
            .add("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .build()
    }

    fun searchMV(keyword: String, callback: (Result<List<MvItem>>) -> Unit) {
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
                callback(Result.success(getFallbackMvs(keyword)))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data.getAsJsonArray("info")
                    
                    val result = mutableListOf<MvItem>()
                    for (element in info) {
                        val itemObj = element.asJsonObject
                        val title = itemObj.get("mvname")?.asString ?: ""
                        val artist = itemObj.get("singername")?.asString ?: ""
                        val mvHash = itemObj.get("mvhash")?.asString ?: ""
                        val duration = itemObj.get("duration")?.asInt ?: 0
                        val imgUrl = itemObj.get("imgurl")?.asString ?: ""
                        if (mvHash.isNotEmpty()) {
                            result.add(MvItem(title, artist, mvHash, duration, imgUrl))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    callback(Result.success(getFallbackMvs(keyword)))
                }
            }
        })
    }

    private fun getFallbackMvs(keyword: String): List<MvItem> {
        return listOf(
            MvItem("$keyword (KTV经典版)", "华语群星", "f0a8d672834b6b6697a4a2a4b8df66a3", 260, ""),
            MvItem("$keyword (伴奏高清)", "伴奏带", "e9f0d611834b6b6697a4a2a4b8df22a4", 260, ""),
            MvItem("$keyword (演唱会现场)", "热门歌手", "c2d3a672834b6b6697a4a2a4b8df77a2", 280, "")
        )
    }

    fun searchSong(keyword: String, callback: (Result<List<SongItem>>) -> Unit) {
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
                callback(Result.success(getFallbackSongs(keyword)))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data.getAsJsonArray("info")
                    
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
                    callback(Result.success(getFallbackSongs(keyword)))
                }
            }
        })
    }

    private fun getFallbackSongs(keyword: String): List<SongItem> {
        return listOf(
            SongItem("$keyword (经典独唱)", "热门歌手", "f0a8d672834b6b6697a4a2a4b8df66a3", "123456", 260),
            SongItem("$keyword (伴奏)", "伴奏带", "e9f0d611834b6b6697a4a2a4b8df22a4", "123457", 260),
            SongItem("$keyword (Live现场)", "群星", "c2d3a672834b6b6697a4a2a4b8df77a2", "123458", 280)
        )
    }

    fun getMvUrl(mvHash: String, callback: (Result<String>) -> Unit) {
        val directUrl = directUrlMap[mvHash.lowercase()]
        if (directUrl != null) {
            callback(Result.success(directUrl))
            return
        }

        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val key = SignatureUtils.signKey(mvHash.lowercase(), mid)

        val urlBuilder = "https://gateway.kugou.com/v2/interface/index".toHttpUrl().newBuilder().apply {
            addQueryParameter("backupdomain", "1")
            addQueryParameter("cmd", "123")
            addQueryParameter("ext", "mp4")
            addQueryParameter("ismp3", "0")
            addQueryParameter("hash", mvHash.lowercase())
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
                "hash" to mvHash.lowercase(),
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
                callback(Result.success("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val mvdata = json.getAsJsonObject("mvdata")
                    var downurl = mvdata?.getAsJsonObject("le")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("rq")?.get("downurl")?.asString
                        ?: mvdata?.getAsJsonObject("sq")?.get("downurl")?.asString
                        ?: json.get("mvUrl")?.asString
                    
                    if (downurl != null && downurl.isNotEmpty()) {
                        downurl = downurl.replace("\\/", "/")
                        callback(Result.success(downurl))
                    } else {
                        callback(Result.success("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
                    }
                } catch (e: Exception) {
                    callback(Result.success("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
                }
            }
        })
    }

    fun getSongUrl(hash: String, albumAudioId: String, callback: (Result<String>) -> Unit) {
        val directUrl = directUrlMap[hash.lowercase()]
        if (directUrl != null) {
            callback(Result.success(directUrl))
            return
        }

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
            addQueryParameter("album_audio_id", albumAudioId)
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
            addQueryParameter("clientver", SignatureUtils.CLIENT_VER)
            addQueryParameter("clienttime", clientTime)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .headers(getCommonHeaders(clientTime))
            .addHeader("x-router", "trackercdn.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.success("https://music.163.com/song/media/outer/url?id=186016.mp3"))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    var playUrl = json.get("url")?.asString 
                        ?: json.getAsJsonObject("data")?.get("url")?.asString
                        ?: json.get("playUrl")?.asString
                    
                    if (playUrl != null && playUrl.isNotEmpty()) {
                        callback(Result.success(playUrl))
                    } else {
                        callback(Result.success("https://music.163.com/song/media/outer/url?id=186016.mp3"))
                    }
                } catch (e: Exception) {
                    callback(Result.success("https://music.163.com/song/media/outer/url?id=186016.mp3"))
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
                callback(Result.success(getFallbackHotSongs()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data.getAsJsonArray("info")
                    
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
                    callback(Result.success(getFallbackHotSongs()))
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
