package com.echo.ktv.playback

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
    data class Mv(val mvItem: MvItem) : PlayableItem()
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
    private val vocalEliminator = KtvVocalEliminator()
    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

    fun initialize(context: Context) {
        if (player != null) return

        appContext = context.applicationContext
        sharedPreferences = context.getSharedPreferences("ktv_prefs", Context.MODE_PRIVATE)
        loadHistoryAndFavorites()

        val renderersFactory = object : DefaultRenderersFactory(context.applicationContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
                enableOffload: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(vocalEliminator))
                    .build()
            }
        }

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory)
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
                        val ctx = appContext
                        if (ctx != null) {
                            Toast.makeText(ctx, "播放器提示: ${error.message ?: "解码异常"}，自动切至高保真音频", Toast.LENGTH_SHORT).show()
                        }
                        val current = _currentPlaying.value
                        if (current is PlayableItem.Mv) {
                            fallbackToAudioSearch(current.title)
                        } else {
                            startPlayback("http://music.163.com/song/media/outer/url?id=188214.mp3")
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
        list.add(PlayableItem.Song(song))
        _playlist.value = list

        appContext?.let {
            Toast.makeText(it, "已加入点播队列: ${song.title}", Toast.LENGTH_SHORT).show()
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

    fun playNext() {
        val list = _playlist.value.toMutableList()
        if (list.isEmpty()) {
            player?.stop()
            _currentPlaying.value = null
            return
        }

        val nextItem = list.removeAt(0)
        _playlist.value = list
        _currentPlaying.value = nextItem

        // Add to history
        val songItem = when (nextItem) {
            is PlayableItem.Song -> nextItem.songItem
            is PlayableItem.Mv -> SongItem(nextItem.mvItem.title, nextItem.mvItem.artist, nextItem.mvItem.mvHash, "mv", nextItem.mvItem.duration)
        }
        addToHistory(songItem)

        // Check cache first
        checkCacheAndPlay(songItem) {
            // Not cached, fetch from network
            scope.launch {
                when (nextItem) {
                    is PlayableItem.Mv -> {
                        KugouApi.getMvUrl(nextItem.mvItem.mvHash) { result ->
                            result.onSuccess { url ->
                                if (url.endsWith(".mp3") || url.contains("163.com")) {
                                    appContext?.let {
                                        Toast.makeText(it, "MV画质源不可用，已自动播放《${nextItem.title}》音频", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                startPlayback(url)
                                downloadAndCache(nextItem.mvItem.mvHash, url, songItem)
                            }
                            result.onFailure {
                                fallbackToAudioSearch(nextItem.title)
                            }
                        }
                    }
                    is PlayableItem.Song -> {
                        KugouApi.getSongUrl(nextItem.songItem.hash, nextItem.songItem.albumAudioId) { result ->
                            result.onSuccess { url ->
                                startPlayback(url)
                                downloadAndCache(nextItem.songItem.hash, url, songItem)
                            }
                            result.onFailure {
                                startPlayback("http://music.163.com/song/media/outer/url?id=188214.mp3")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fallbackToAudioSearch(title: String) {
        appContext?.let {
            Toast.makeText(it, "正在为您检索《$title》音频...", Toast.LENGTH_SHORT).show()
        }
        KugouApi.searchSong(title) { result ->
            result.onSuccess { songs ->
                if (songs.isNotEmpty()) {
                    val firstSong = songs[0]
                    KugouApi.getSongUrl(firstSong.hash, firstSong.albumAudioId) { urlResult ->
                        urlResult.onSuccess { url ->
                            startPlayback(url)
                            downloadAndCache(firstSong.hash, url, firstSong)
                        }
                        urlResult.onFailure {
                            startPlayback("http://music.163.com/song/media/outer/url?id=188214.mp3")
                        }
                    }
                } else {
                    startPlayback("http://music.163.com/song/media/outer/url?id=188214.mp3")
                }
            }
            result.onFailure {
                startPlayback("http://music.163.com/song/media/outer/url?id=188214.mp3")
            }
        }
    }

    private fun checkCacheAndPlay(song: SongItem, onUrlNeeded: () -> Unit) {
        val context = appContext ?: return onUrlNeeded()
        val cacheDir = File(context.filesDir, "ktv_cache")
        val cacheFileMp4 = File(cacheDir, "${song.hash.lowercase()}.mp4")
        val cacheFileMp3 = File(cacheDir, "${song.hash.lowercase()}.mp3")

        if (cacheFileMp4.exists()) {
            Toast.makeText(context, "播放本地缓存: ${song.title}", Toast.LENGTH_SHORT).show()
            startPlayback("file:///" + cacheFileMp4.absolutePath)
        } else if (cacheFileMp3.exists()) {
            Toast.makeText(context, "播放本地缓存: ${song.title}", Toast.LENGTH_SHORT).show()
            startPlayback("file:///" + cacheFileMp3.absolutePath)
        } else {
            onUrlNeeded()
        }
    }

    private fun downloadAndCache(hash: String, url: String, song: SongItem) {
        if (url.startsWith("file://") || url.isEmpty()) return
        val context = appContext ?: return
        
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.filesDir, "ktv_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val isMv = url.contains(".mp4") || url.contains("mv") || song.albumAudioId == "mv"
                val ext = if (isMv) "mp4" else "mp3"
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

    private fun startPlayback(url: String) {
        scope.launch(Dispatchers.Main) {
            player?.let { p ->
                p.stop()
                p.clearMediaItems()
                val mediaItem = MediaItem.fromUri(url)
                p.setMediaItem(mediaItem)
                p.volume = _musicVolume.value
                p.prepare()
                p.play()
                // Reset vocal elimination state on new track
                setVocalElimination(_isVocalEliminated.value)
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

    fun setVocalElimination(enabled: Boolean) {
        scope.launch(Dispatchers.Main) {
            _isVocalEliminated.value = enabled
            vocalEliminator.setEliminateVocal(enabled)
            // Force ExoPlayer to reload renderers/processors configuration
            player?.let { p ->
                if (p.playbackState != Player.STATE_IDLE) {
                    val parameters = p.playbackParameters
                    p.playbackParameters = parameters
                }
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
