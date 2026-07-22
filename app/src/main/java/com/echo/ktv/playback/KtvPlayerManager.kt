package com.echo.ktv.playback

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.echo.ktv.api.KugouApi
import com.echo.ktv.api.MvItem
import com.echo.ktv.api.SongItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class PlayableItem {
    data class Mv(val mvItem: MvItem, val fallbackSongHash: String = "") : PlayableItem()
    data class Song(val songItem: SongItem) : PlayableItem()

    val title: String
        get() = when (this) {
            is Mv -> mvItem.title
            is Song -> songItem.title
        }

    val artist: String
        get() = when (this) {
            is Mv -> mvItem.artist
            is Song -> songItem.artist
        }
}

object KtvPlayerManager {
    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Active playback URLs for seamless 原唱 / 伴奏 switching
    private var activeOriginalUrl: String = ""
    private var activeAccFileUrl: String = ""
    private var activeHash: String = ""

    // Playlist Queue
    private val _playlist = MutableStateFlow<List<PlayableItem>>(emptyList())
    val playlist: StateFlow<List<PlayableItem>> = _playlist

    // Current Playing Item
    private val _currentPlaying = MutableStateFlow<PlayableItem?>(null)
    val currentPlaying: StateFlow<PlayableItem?> = _currentPlaying

    // Vocal elimination state
    private val _isVocalEliminated = MutableStateFlow(false)
    val isVocalEliminated: StateFlow<Boolean> = _isVocalEliminated

    // Music playback volume (0.0 to 1.0)
    private val _musicVolume = MutableStateFlow(1.0f)
    val musicVolume: StateFlow<Float> = _musicVolume

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    // History, Favorites and Local Cached songs
    private val _history = MutableStateFlow<List<SongItem>>(emptyList())
    val history: StateFlow<List<SongItem>> = _history

    private val _favorites = MutableStateFlow<List<SongItem>>(emptyList())
    val favorites: StateFlow<List<SongItem>> = _favorites

    private val _localSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val localSongs: StateFlow<List<SongItem>> = _localSongs

    private val scope = CoroutineScope(Dispatchers.Main)
    private var isHandlingPlayerError = false

    fun initialize(context: Context) {
        if (player != null) return

        appContext = context.applicationContext
        sharedPreferences = context.getSharedPreferences("ktv_prefs", Context.MODE_PRIVATE)
        loadHistoryAndFavorites()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
        }
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context.applicationContext, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            playNext()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        error.printStackTrace()
                        if (isHandlingPlayerError) return
                        isHandlingPlayerError = true

                        val ctx = appContext
                        val current = _currentPlaying.value
                        if (current != null) {
                            if (ctx != null) {
                                Toast.makeText(ctx, "正在为您检索《${current.title}》高保真音频", Toast.LENGTH_SHORT).show()
                            }
                            KugouApi.getSongUrlByTitle(current.title) { res ->
                                res.onSuccess { url -> startPlayback(url, current.title) }
                            }
                        }
                    }
                })
            }
    }

    fun getPlayer(): ExoPlayer? = player

    fun addMvToQueue(mv: MvItem) {
        val list = _playlist.value.toMutableList()
        list.add(PlayableItem.Mv(mv))
        _playlist.value = list

        appContext?.let {
            Toast.makeText(it, "已加入点播队列: ${mv.title}", Toast.LENGTH_SHORT).show()
        }

        if (_currentPlaying.value == null) {
            playNext()
        }
    }

    fun addSongToQueue(song: SongItem) {
        val list = _playlist.value.toMutableList()
        val item = if (song.mvHash.isNotEmpty()) {
            PlayableItem.Mv(MvItem(song.title, song.artist, song.mvHash, song.duration, ""), song.hash)
        } else {
            PlayableItem.Song(song)
        }
        list.add(item)
        _playlist.value = list

        appContext?.let {
            val typeStr = if (song.mvHash.isNotEmpty()) "MV" else "歌曲"
            Toast.makeText(it, "已加入点播队列($typeStr): ${song.title}", Toast.LENGTH_SHORT).show()
        }

        if (_currentPlaying.value == null) {
            playNext()
        }
    }

    fun removeAt(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _playlist.value = list
        }
    }

    private fun cleanSearchQuery(text: String): String {
        return text.replace(Regex("\\(.*\\)|（.*）|\\[.*\\]"), "").trim()
    }

    fun playNext() {
        isHandlingPlayerError = false
        val list = _playlist.value.toMutableList()
        if (list.isEmpty()) {
            player?.stop()
            _currentPlaying.value = null
            activeOriginalUrl = ""
            activeAccFileUrl = ""
            return
        }

        val nextItem = list.removeAt(0)
        _playlist.value = list
        _currentPlaying.value = nextItem
        activeAccFileUrl = ""

        val songItem = when (nextItem) {
            is PlayableItem.Song -> nextItem.songItem
            is PlayableItem.Mv -> SongItem(nextItem.mvItem.title, nextItem.mvItem.artist, nextItem.mvItem.mvHash, "mv", nextItem.mvItem.duration)
        }
        activeHash = songItem.hash.ifEmpty { nextItem.title }
        addToHistory(songItem)

        val cleanTitle = cleanSearchQuery(nextItem.title)
        val cleanArtist = cleanSearchQuery(nextItem.artist)
        val searchKeyword = if (cleanArtist.isNotEmpty()) "$cleanTitle $cleanArtist" else cleanTitle

        appContext?.let {
            Toast.makeText(it, "🔍 正在为您检索《$cleanTitle》1080P 高清 MV...", Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            KugouApi.searchMV(searchKeyword) { result ->
                result.onSuccess { mvList ->
                    val matchedMv = mvList.firstOrNull { it.mvHash.length >= 32 }
                    if (matchedMv != null) {
                        _currentPlaying.value = PlayableItem.Mv(matchedMv, songItem.hash)
                        KugouApi.getMvUrl(matchedMv.mvHash, titleFallback = cleanTitle) { mvUrlRes ->
                            mvUrlRes.onSuccess { videoUrl ->
                                appContext?.let {
                                    Toast.makeText(it, "🎬 成功加载 1080P 高清 MV: $cleanTitle", Toast.LENGTH_SHORT).show()
                                }
                                startPlayback(videoUrl, activeHash)
                                downloadAndCache(matchedMv.mvHash, videoUrl, songItem)
                            }
                            mvUrlRes.onFailure {
                                playAudioFallback(cleanTitle, songItem)
                            }
                        }
                    } else {
                        playAudioFallback(cleanTitle, songItem)
                    }
                }
                result.onFailure {
                    playAudioFallback(cleanTitle, songItem)
                }
            }
        }
    }

    private fun playAudioFallback(cleanTitle: String, songItem: SongItem) {
        _currentPlaying.value = PlayableItem.Song(songItem)
        appContext?.let {
            Toast.makeText(it, "🎵 已加载全长原唱音频: $cleanTitle", Toast.LENGTH_SHORT).show()
        }
        KugouApi.getSongUrlByTitle(cleanTitle) { res ->
            res.onSuccess { url -> startPlayback(url, activeHash) }
        }
    }

    private fun downloadAndCache(hash: String, url: String, song: SongItem) {
        if (url.startsWith("file://") || url.isEmpty()) return
        val context = appContext ?: return
        
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.filesDir, "ktv_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val isMv = url.contains(".mp4") || url.contains(".mkv") || url.contains("mv") || song.albumAudioId == "mv"
                val ext = if (isMv) "mkv" else "mp3"
                val cacheFile = File(cacheDir, "${hash.lowercase()}.$ext")

                if (cacheFile.exists()) return@launch

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.use { body ->
                        val outputStream = cacheFile.outputStream()
                        try {
                            body.byteStream().copyTo(outputStream)
                        } finally {
                            outputStream.close()
                        }
                        launch(Dispatchers.Main) {
                            addToLocalSongs(song)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startPlayback(url: String, hash: String = "") {
        activeOriginalUrl = url
        activeAccFileUrl = ""
        val context = appContext

        scope.launch(Dispatchers.Main) {
            player?.let { p ->
                p.stop()
                p.clearMediaItems()
                val mediaItem = MediaItem.fromUri(url)
                p.setMediaItem(mediaItem)
                p.volume = _musicVolume.value
                p.prepare()
                p.play()
            }
        }

        // Trigger background accompaniment generation
        if (context != null && hash.isNotEmpty()) {
            KtvVocalEliminationGenerator.generateAccompaniment(context, url, hash) { result ->
                result.onSuccess { accFile ->
                    activeAccFileUrl = "file:///" + accFile.absolutePath
                    if (_isVocalEliminated.value) {
                        applyVocalEliminationSwitch(true)
                    }
                }
            }
        }
    }

    fun setVocalElimination(enabled: Boolean) {
        scope.launch(Dispatchers.Main) {
            _isVocalEliminated.value = enabled
            applyVocalEliminationSwitch(enabled)
        }
    }

    private fun applyVocalEliminationSwitch(enabled: Boolean) {
        val p = player ?: return
        val currentPos = p.currentPosition
        val wasPlaying = p.isPlaying
        val context = appContext

        if (enabled) {
            if (activeAccFileUrl.isNotEmpty()) {
                val current = _currentPlaying.value
                if (current is PlayableItem.Mv && activeOriginalUrl.isNotEmpty()) {
                    // MV: Combine MV Video stream + Accompaniment Audio stream with MergingMediaSource
                    val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent("Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
                        .setAllowCrossProtocolRedirects(true)
                    val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context!!, httpDataSourceFactory)

                    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(activeOriginalUrl))
                    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(activeAccFileUrl))

                    val mergedSource = MergingMediaSource(videoSource, audioSource)
                    p.setMediaSource(mergedSource)
                } else {
                    // Audio Song: Switch to Accompaniment WAV audio file
                    p.setMediaItem(MediaItem.fromUri(activeAccFileUrl))
                }
                p.prepare()
                p.seekTo(currentPos)
                if (wasPlaying) p.play()
                context?.let {
                    Toast.makeText(it, "🎤 已切至【伴奏模式】", Toast.LENGTH_SHORT).show()
                }
            } else {
                context?.let {
                    Toast.makeText(it, "⏳ 正在合成伴奏中，稍后将自动切换...", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Revert to 原唱
            if (activeOriginalUrl.isNotEmpty()) {
                p.setMediaItem(MediaItem.fromUri(activeOriginalUrl))
                p.prepare()
                p.seekTo(currentPos)
                if (wasPlaying) p.play()
                context?.let {
                    Toast.makeText(it, "🎤 已切至【原唱模式】", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun togglePlayPause() {
        scope.launch(Dispatchers.Main) {
            player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
        }
    }

    fun setVolume(vol: Float) {
        scope.launch(Dispatchers.Main) {
            val coerced = vol.coerceIn(0.0f, 1.0f)
            _musicVolume.value = coerced
            player?.volume = coerced
        }
    }

    fun skipCurrent() {
        scope.launch(Dispatchers.Main) {
            playNext()
        }
    }

    fun release() {
        scope.launch(Dispatchers.Main) {
            player?.release()
            player = null
        }
    }

    // History and Favorites Persistence Helpers
    private fun loadHistoryAndFavorites() {
        sharedPreferences?.let { prefs ->
            val historyJson = prefs.getString("history", null)
            if (historyJson != null) {
                val listType = object : TypeToken<List<SongItem>>() {}.type
                _history.value = gson.fromJson(historyJson, listType)
            }
            val favoritesJson = prefs.getString("favorites", null)
            if (favoritesJson != null) {
                val listType = object : TypeToken<List<SongItem>>() {}.type
                _favorites.value = gson.fromJson(favoritesJson, listType)
            }
            val localJson = prefs.getString("local_songs", null)
            if (localJson != null) {
                val listType = object : TypeToken<List<SongItem>>() {}.type
                _localSongs.value = gson.fromJson(localJson, listType)
            }
        }
    }

    private fun addToHistory(song: SongItem) {
        val list = _history.value.toMutableList()
        list.remove(song)
        list.add(0, song)
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        _history.value = list
        saveList("history", list)
    }

    fun toggleFavorite(song: SongItem) {
        val list = _favorites.value.toMutableList()
        if (list.contains(song)) {
            list.remove(song)
            appContext?.let {
                Toast.makeText(it, "已取消收藏: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        } else {
            list.add(song)
            appContext?.let {
                Toast.makeText(it, "已添加到收藏: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        }
        _favorites.value = list
        saveList("favorites", list)
    }

    private fun addToLocalSongs(song: SongItem) {
        val list = _localSongs.value.toMutableList()
        if (!list.contains(song)) {
            list.add(song)
            _localSongs.value = list
            saveList("local_songs", list)
        }
    }

    private fun saveList(key: String, list: List<SongItem>) {
        sharedPreferences?.edit()?.putString(key, gson.toJson(list))?.apply()
    }
}
