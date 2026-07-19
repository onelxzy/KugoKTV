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
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data") ?: json
                    val lists = data.getAsJsonArray("lists") ?: JsonArray()
                    val result = mutableListOf<MvItem>()
                    for (element in lists) {
                        val itemObj = element.asJsonObject
                        val title = itemObj.get("FileName")?.asString ?: itemObj.get("mvname")?.asString ?: "未知MV"
                        val artist = itemObj.get("SingerName")?.asString ?: ""
                        val mvHash = itemObj.get("mvhash")?.asString ?: itemObj.get("FileHash")?.asString ?: ""
                        val duration = itemObj.get("Duration")?.asInt ?: 0
                        val cover = itemObj.get("imgurl")?.asString?.replace("{size}", "400") ?: ""
                        if (mvHash.isNotEmpty()) {
                            result.add(MvItem(title, artist, mvHash, duration, cover))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
        })
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
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data") ?: json
                    val lists = data.getAsJsonArray("lists") ?: JsonArray()
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
                    callback(Result.failure(e))
                }
            }
        })
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
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    // Kugou MV Tracker returns array of backup urls or single url under mvdata / mvUrl
                    val url = json.get("mvUrl")?.asString 
                        ?: json.getAsJsonObject("mvdata")?.getAsJsonObject("le")?.get("downurl")?.asString
                        ?: json.getAsJsonObject("mvdata")?.getAsJsonObject("rq")?.get("downurl")?.asString
                        ?: json.getAsJsonObject("mvdata")?.getAsJsonObject("sq")?.get("downurl")?.asString
                    
                    if (url != null && url.isNotEmpty()) {
                        callback(Result.success(url))
                    } else {
                        callback(Result.failure(Exception("无法获取视频播放流链接: $body")))
                    }
                } catch (e: Exception) {
                    callback(Result.failure(e))
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
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val url = json.get("url")?.asString ?: json.getAsJsonObject("data")?.get("url")?.asString
                    if (url != null && url.isNotEmpty()) {
                        callback(Result.success(url))
                    } else {
                        callback(Result.failure(Exception("无法获取音频播放流链接: $body")))
                    }
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
        })
    }

    fun getHotSongs(callback: (Result<List<SongItem>>) -> Unit) {
        val clientTime = (System.currentTimeMillis() / 1000).toString()
        val dataMap = JsonObject().apply {
            addProperty("rank_id", 21608) // 酷狗新歌榜 ID
            addProperty("userid", 0)
            addProperty("page", 1)
            addProperty("pagesize", 30)
            add("tags", JsonArray())
        }
        val dataStr = gson.toJson(dataMap)

        val params = mutableMapOf(
            "appid" to SignatureUtils.APP_ID,
            "clientver" to SignatureUtils.CLIENT_VER,
            "clienttime" to clientTime
        )
        params["signature"] = SignatureUtils.signatureAndroidParams(params, dataStr)

        val urlBuilder = "$BASE_URL/musicadservice/container/v1/newsong_publish".toHttpUrl().newBuilder()
        for ((key, value) in params) {
            urlBuilder.addQueryParameter(key, value)
        }

        val requestBody = dataStr.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(urlBuilder.build())
            .post(requestBody)
            .headers(getCommonHeaders(clientTime))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val data = json.getAsJsonObject("data") ?: json
                    val lists = data.getAsJsonArray("lists") ?: JsonArray()
                    val result = mutableListOf<SongItem>()
                    for (element in lists) {
                        val itemObj = element.asJsonObject
                        val title = itemObj.get("songname")?.asString ?: "未知歌曲"
                        val artist = itemObj.get("singername")?.asString ?: ""
                        val hash = itemObj.get("hash")?.asString ?: ""
                        val albumAudioId = itemObj.get("album_audio_id")?.asString ?: ""
                        val duration = itemObj.get("duration")?.asInt ?: 0
                        if (hash.isNotEmpty()) {
                            result.add(SongItem(title, artist, hash, albumAudioId, duration))
                        }
                    }
                    callback(Result.success(result))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
        })
    }
}
