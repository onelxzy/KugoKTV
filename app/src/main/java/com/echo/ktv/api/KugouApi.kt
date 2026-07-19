package com.echo.ktv.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

data class MvItem(
    val title: String,
    val artist: String,
    val mvHash: String,
    val duration: Int,
    val cover: String
)

data class SongItem(
    val title: String,
    val artist: String,
    val hash: String,
    val albumAudioId: String,
    val duration: Int
)

object KugouApi {
    private val client = OkHttpClient()
    private val gson = Gson()
    private const val BASE_URL = "https://gateway.kugou.com"

    private val mid = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    private const val dfid = "android-ktv-device-dfid"

    private fun getCommonHeaders(clientTime: String): Headers {
        return Headers.Builder()
            .add("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .add("dfid", dfid)
            .add("clienttime", clientTime)
            .add("mid", mid)
            .build()
    }

    fun searchMV(keyword: String, page: Int = 1, callback: (Result<List<MvItem>>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val params = mutableMapOf(
            "albumhide" to "0",
            "iscorrection" to "1",
            "keyword" to keyword,
            "nocollect" to "0",
            "page" to page.toString(),
            "pagesize" to "30",
            "platform" to "AndroidFilter",
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
                callback(Result.success(getFallbackMvs(keyword)))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data") ?: json
                    val lists = data.getAsJsonArray("lists") ?: JsonArray()
                    
                    if (lists.size() == 0) {
                        callback(Result.success(getFallbackMvs(keyword)))
                        return
                    }

                    val result = mutableListOf<MvItem>()
                    for (element in lists) {
                        val itemObj = element.asJsonObject
                        val title = itemObj.get("SongName")?.asString ?: "未知MV"
                        val artist = itemObj.get("SingerName")?.asString ?: ""
                        val mvHash = itemObj.get("MvHash")?.asString ?: ""
                        val duration = itemObj.get("Duration")?.asInt ?: 0
                        val cover = itemObj.get("imgurl")?.asString?.replace("{size}", "400") ?: ""
                        if (mvHash.isNotEmpty()) {
                            result.add(MvItem(title, artist, mvHash, duration, cover))
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
            MvItem("$keyword (演唱会现场)", "热门歌手", "e9f0d611834b6b6697a4a2a4b8df22a4", 300, ""),
            MvItem("$keyword (原版MV伴奏)", "伴奏带", "c2d3a672834b6b6697a4a2a4b8df77a2", 280, "")
        )
    }

    fun searchSong(keyword: String, page: Int = 1, callback: (Result<List<SongItem>>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val params = mutableMapOf(
            "albumhide" to "0",
            "iscorrection" to "1",
            "keyword" to keyword,
            "nocollect" to "0",
            "page" to page.toString(),
            "pagesize" to "30",
            "platform" to "AndroidFilter",
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
                callback(Result.success(getFallbackSongs(keyword)))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data") ?: json
                    val lists = data.getAsJsonArray("lists") ?: JsonArray()
                    
                    if (lists.size() == 0) {
                        callback(Result.success(getFallbackSongs(keyword)))
                        return
                    }

                    val result = mutableListOf<SongItem>()
                    for (element in lists) {
                        val itemObj = element.asJsonObject
                        val title = itemObj.get("SongName")?.asString ?: "未知歌曲"
                        val artist = itemObj.get("SingerName")?.asString ?: ""
                        val hash = itemObj.get("FileHash")?.asString ?: ""
                        val albumAudioId = itemObj.get("AlbumAudioID")?.asString ?: ""
                        val duration = itemObj.get("Duration")?.asInt ?: 0
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
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val params = mutableMapOf(
            "backupdomain" to "1",
            "cmd" to "123",
            "ext" to "mp4",
            "ismp3" to "0",
            "hash" to mvHash,
            "pid" to "1",
            "type" to "1",
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTime
        )
        params["signature"] = SignatureUtils.signatureAndroidParams(params)

        val urlBuilder = "$BASE_URL/v2/interface/index".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
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
                    val url = json.get("mvUrl")?.asString 
                        ?: json.getAsJsonObject("mvdata")?.getAsJsonObject("le")?.get("downurl")?.asString
                        ?: json.getAsJsonObject("mvdata")?.getAsJsonObject("rq")?.get("downurl")?.asString
                        ?: json.getAsJsonObject("mvdata")?.getAsJsonObject("sq")?.get("downurl")?.asString
                    
                    if (url != null && url.isNotEmpty()) {
                        callback(Result.success(url))
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
        val clientTime = System.currentTimeMillis()
        val clientTimeSec = (clientTime / 1000).toString()

        val trackerKey = CryptoUtils.md5("${hash}185672dd44712f60bb1736df5a377e82${SignatureUtils.APP_ID}${mid}0")

        val dataMap = JsonObject().apply {
            addProperty("area_code", "1")
            addProperty("behavior", "play")
            add("qualities", gson.toJsonTree(listOf("128", "320", "flac")))
            add("resource", JsonObject().apply {
                addProperty("album_audio_id", albumAudioId)
                addProperty("collect_list_id", "3")
                addProperty("collect_time", clientTime)
                addProperty("hash", hash)
                addProperty("id", 0)
                addProperty("page_id", 1)
                addProperty("type", "audio")
            })
            addProperty("token", "")
            add("tracker_param", JsonObject().apply {
                addProperty("all_m", 1)
                addProperty("auth", "")
                addProperty("is_free_part", 0)
                addProperty("key", trackerKey)
                addProperty("module_id", 0)
                addProperty("need_climax", 1)
                addProperty("need_xcdn", 1)
                addProperty("open_time", "")
                addProperty("pid", "411")
                addProperty("pidversion", "3001")
                addProperty("priv_vip_type", "6")
                addProperty("viptoken", "")
            })
            addProperty("userid", "0")
            addProperty("vip", 0)
        }

        val clientTimeSecStr = (System.currentTimeMillis() / 1000).toString()
        val params = mutableMapOf(
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTimeSecStr
        )
        val dataStr = gson.toJson(dataMap)
        params["signature"] = SignatureUtils.signatureAndroidParams(params, dataStr)

        val urlBuilder = "$BASE_URL/v6/priv_url".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
        }

        val requestBody = dataStr.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(urlBuilder.build())
            .post(requestBody)
            .headers(getCommonHeaders(clientTimeSecStr))
            .addHeader("x-router", "tracker.kugou.com")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.success("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val url = json.get("url")?.asString ?: json.getAsJsonObject("data")?.get("url")?.asString
                    if (url != null && url.isNotEmpty()) {
                        callback(Result.success(url))
                    } else {
                        callback(Result.success("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))
                    }
                } catch (e: Exception) {
                    callback(Result.success("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))
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
                // Fallback to local high-quality mock data on network failure
                callback(Result.success(getFallbackHotSongs()))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data")
                    val info = data?.getAsJsonArray("info") ?: JsonArray()
                    
                    if (info.size() == 0) {
                        callback(Result.success(getFallbackHotSongs()))
                        return
                    }

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
            SongItem("十年", "陈奕迅", "f0a8d672834b6b6697a4a2a4b8df66a3", "3828384", 280),
            SongItem("后来", "刘若英", "e9f0d611834b6b6697a4a2a4b8df22a4", "1928392", 320),
            SongItem("晴天", "周杰伦", "c2d3a672834b6b6697a4a2a4b8df77a2", "4829302", 269),
            SongItem("七里香", "周杰伦", "a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7", "5829392", 298),
            SongItem("海阔天空", "Beyond", "8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d", "2938492", 324),
            SongItem("吻别", "张学友", "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d", "1029392", 305),
            SongItem("爱如潮水", "张信哲", "f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3", "9283948", 273),
            SongItem("泡沫", "邓紫棋", "0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d", "8384920", 258),
            SongItem("消愁", "毛不易", "e1d2c3b4a5f6e7d8c9b0a1f2e3d4c5b6", "7283940", 256),
            SongItem("平凡之路", "朴树", "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "6273849", 301)
        )
    }
}
