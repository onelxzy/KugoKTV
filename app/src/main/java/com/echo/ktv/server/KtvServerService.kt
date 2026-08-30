package com.echo.ktv.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.echo.ktv.api.KugouApi
import com.echo.ktv.api.MvItem
import com.echo.ktv.api.SongItem
import com.echo.ktv.playback.KtvPlayerManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch

class KtvServerService : Service() {
    private var server: ApplicationEngine? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startKtorServer()
    }

    private fun startForegroundService() {
        val channelId = "KtvServerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "酷唱 KTV 点歌后台服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("酷唱 KTV 点歌台正在运行")
            .setContentText("扫码或输入局域网地址即可进行手机点歌")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1001, notification)
    }

    private fun startKtorServer() {
        server = embeddedServer(CIO, port = 19985) {
            install(ContentNegotiation) {
                gson()
            }
            routing {
                // Serve mobile client H5 page
                get("/") {
                    call.respondText(MobileWebStatic.INDEX_HTML, ContentType.Text.Html)
                }
                get("/index.html") {
                    call.respondText(MobileWebStatic.INDEX_HTML, ContentType.Text.Html)
                }

                // API: Search MV / Song
                get("/api/search") {
                    val query = call.parameters["q"] ?: ""
                    val type = call.parameters["type"] ?: "mv"
                    val latch = CountDownLatch(1)
                    var error: Throwable? = null
                    var searchResults: Any = emptyList<Any>()

                    if (type == "singer") {
                        KugouApi.searchSinger(query) { result ->
                            result.onSuccess { searchResults = it }
                            result.onFailure { error = it }
                            latch.countDown()
                        }
                    } else {
                        KugouApi.searchSong(query) { result ->
                            result.onSuccess { searchResults = it }
                            result.onFailure { error = it }
                            latch.countDown()
                        }
                    }

                    latch.await()
                    if (error != null) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error?.message))
                    } else {
                        call.respond(searchResults)
                    }
                }

                // API: Get Current Playlist Queue
                get("/api/playlist") {
                    val list = KtvPlayerManager.playlist.value.map { item ->
                        mapOf(
                            "title" to item.title,
                            "artist" to item.artist
                        )
                    }
                    call.respond(list)
                }

                // API: Get Playback Status
                get("/api/status") {
                    val current = KtvPlayerManager.currentPlaying.value
                    val status = mapOf(
                        "isPlaying" to (KtvPlayerManager.getPlayer()?.isPlaying ?: false),
                        "isVocalEliminated" to KtvPlayerManager.isVocalEliminated.value,
                        "accompanimentSource" to KtvPlayerManager.accompanimentSource.value.name,
                        "volume" to KtvPlayerManager.musicVolume.value,
                        "current" to if (current != null) mapOf(
                            "title" to current.title,
                            "artist" to current.artist
                        ) else null
                    )
                    call.respond(status)
                }

                // API: Add Item to Queue
                post("/api/add") {
                    val body = call.receive<JsonObject>()
                    val type = body.get("type")?.asString ?: "mv"
                    val title = body.get("title")?.asString ?: "未知"
                    val artist = body.get("artist")?.asString ?: ""
                    val hash = body.get("hash")?.asString ?: ""
                    val albumAudioId = body.get("albumAudioId")?.asString ?: ""
                    val duration = body.get("duration")?.asInt ?: 0
                    val cover = body.get("cover")?.asString ?: ""

                    if (hash.isNotEmpty()) {
                        scope.launch(Dispatchers.Main) {
                            if (type == "song") {
                                KtvPlayerManager.addSongToQueue(
                                    SongItem(title, artist, hash, albumAudioId, duration)
                                )
                            } else {
                                KtvPlayerManager.addMvToQueue(
                                    MvItem(title, artist, hash, duration, cover)
                                )
                            }
                        }
                        call.respond(mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing hash"))
                    }
                }

                // API: Remote Controls
                post("/api/control") {
                    val body = call.receive<JsonObject>()
                    val action = body.get("action")?.asString ?: ""
                    scope.launch(Dispatchers.Main) {
                        when (action) {
                            "play" -> KtvPlayerManager.getPlayer()?.play()
                            "pause" -> KtvPlayerManager.getPlayer()?.pause()
                            "skip" -> KtvPlayerManager.skipCurrent()
                            "vocal" -> {
                                val value = body.get("value")?.asBoolean ?: false
                                KtvPlayerManager.setVocalElimination(value)
                            }
                            "volume" -> {
                                val value = body.get("value")?.asFloat ?: 1.0f
                                KtvPlayerManager.setVolume(value)
                            }
                        }
                    }
                    call.respond(mapOf("success" to true))
                }
            }
        }.start(wait = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 2000)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
