package com.echo.ktv.playback

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.echo.ktv.api.AccompanimentMatchResult
import com.echo.ktv.api.KugouApi
import com.echo.ktv.api.MvItem
import com.echo.ktv.api.MvStreamResult
import com.echo.ktv.api.SongItem
import com.echo.ktv.auth.UserManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class PlayableItem {
    data class Mv(val mvItem: MvItem, val videoStreamUrl: String, val fallbackSongHash: String = "") : PlayableItem()
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

enum class AccompanimentSource {
    NONE,           // ???????????
    OFFICIAL,       // ????????????
    DSP_FALLBACK    // ??DSP??????
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

        // Active playback URLs for seamless ?? / ?? switching
    private var activeOriginalUrl: String = ""
    private var activeAccFileUrl: String = ""
    private var activeOfficialAccUrl: String = ""
    private var activeOfficialAccDuration: Int = 0
    private var activeHash: String = ""

    // Accompaniment Source State
    private val _accompanimentSource = MutableStateFlow(AccompanimentSource.NONE)
    val accompanimentSource: StateFlow<AccompanimentSource> = _accompanimentSource


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

    // DSP Tunable Audio Settings Flow
    private val _dspSettings = MutableStateFlow(DspSettings.DEFAULT)
    val dspSettings: StateFlow<DspSettings> = _dspSettings

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

    init {
        // Observe login & VIP status changes to auto-upgrade currently playing song's accompaniment in real-time
        scope.launch(Dispatchers.Main) {
            UserManager.userProfile.collect { profile ->
                if (profile?.isVip == true && activeOfficialAccUrl.isEmpty()) {
                    val current = _currentPlaying.value ?: return@collect
                    val songItem = when (current) {
                        is PlayableItem.Song -> current.songItem
                        is PlayableItem.Mv -> SongItem(current.mvItem.title, current.mvItem.artist, current.mvItem.mvHash, "mv", current.mvItem.duration, current.mvItem.mvHash)
                    }
                    KugouApi.searchAccompaniment(songItem.title, songItem.artist, songItem.duration) { accResult ->
                        accResult.onSuccess { match ->
                            activeOfficialAccUrl = match.url
                            activeOfficialAccDuration = match.duration
                            _accompanimentSource.value = AccompanimentSource.OFFICIAL
                            if (_isVocalEliminated.value) {
                                applyVocalEliminationSwitch(true)
                            }
                        }
                    }
                }
            }
        }
    }
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

                    override fun onTracksChanged(tracks: Tracks) {
                        // When in accompaniment mode with MergingMediaSource on MV, ensure the accompaniment audio track is selected
                        if (_isVocalEliminated.value && _currentPlaying.value is PlayableItem.Mv) {
                            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                            if (audioGroups.size >= 2) {
                                val accGroup = audioGroups.last()
                                if (!accGroup.isSelected) {
                                    player?.trackSelectionParameters = player?.trackSelectionParameters?.buildUpon()
                                        ?.setOverrideForType(TrackSelectionOverride(accGroup.mediaTrackGroup, 0))
                                        ?.build() ?: return
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        error.printStackTrace()
                        if (isHandlingPlayerError) return
                        isHandlingPlayerError = true

                        val ctx = appContext
                        val current = _currentPlaying.value
                        if (current != null) {
                            if (ctx != null) {
                                Toast.makeText(ctx, "正在为您切换备用音源: 《${current.title}》", Toast.LENGTH_SHORT).show()
                            }
                            KugouApi.getSongAudioUrl(current.title) { res ->
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
        list.add(PlayableItem.Mv(mv, ""))
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
        val item = PlayableItem.Song(song)
        list.add(item)
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

    fun moveToTop(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in 1 until list.size) {
            val item = list.removeAt(index)
            list.add(0, item)
            _playlist.value = list
            appContext?.let {
                Toast.makeText(it, "🔝 已将《${item.title}》置顶为下一首播放", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playNow(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) {
            val item = list.removeAt(index)
            _playlist.value = list
            appContext?.let {
                Toast.makeText(it, "▶ 正在立即播放《${item.title}》", Toast.LENGTH_SHORT).show()
            }
            playItem(item)
        }
    }

    fun playNext() {
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
        playItem(nextItem)
    }

    private fun playItem(item: PlayableItem) {
        isHandlingPlayerError = false
        activeAccFileUrl = ""
        activeOfficialAccUrl = ""
        activeOfficialAccDuration = 0
        _accompanimentSource.value = AccompanimentSource.NONE

        val songItem = when (item) {
            is PlayableItem.Song -> item.songItem
            is PlayableItem.Mv -> SongItem(item.mvItem.title, item.mvItem.artist, item.mvItem.mvHash, "mv", item.mvItem.duration, item.mvItem.mvHash)
        }
        activeHash = songItem.hash.ifEmpty { item.title }
        addToHistory(songItem)

        appContext?.let {
            Toast.makeText(it, "🔍 正在为您检索《${songItem.title}》片源...", Toast.LENGTH_SHORT).show()
        }

        // Parallel Task 1: Check official studio accompaniment FIRST
        KugouApi.searchAccompaniment(songItem.title, songItem.artist, songItem.duration) { accResult ->
            accResult.onSuccess { match ->
                activeOfficialAccUrl = match.url
                activeOfficialAccDuration = match.duration
                _accompanimentSource.value = AccompanimentSource.OFFICIAL
                if (_isVocalEliminated.value) {
                    applyVocalEliminationSwitch(true)
                }
            }
            accResult.onFailure {
                // If official accompaniment not found, fallback to DSP generation in background
                _accompanimentSource.value = AccompanimentSource.DSP_FALLBACK
                val context = appContext
                if (context != null && activeOriginalUrl.isNotEmpty() && activeHash.isNotEmpty()) {
                    KtvVocalEliminationGenerator.generateAccompaniment(context, activeOriginalUrl, activeHash, _dspSettings.value) { dspRes ->
                        dspRes.onSuccess { accFile ->
                            activeAccFileUrl = "file:///" + accFile.absolutePath
                            if (_isVocalEliminated.value) {
                                applyVocalEliminationSwitch(true)
                            }
                        }
                    }
                }
            }
        }

        // Parallel Task 2: Resolve Video MV / Audio stream
        scope.launch {
            KugouApi.resolveOfficialMv(songItem.albumAudioId, songItem.mvHash, songItem.title) { result ->
                result.onSuccess { mvResult ->
                    val mvItem = MvItem(
                        title = if (mvResult.title.isNotEmpty()) mvResult.title else songItem.title,
                        artist = if (mvResult.artist.isNotEmpty()) mvResult.artist else songItem.artist,
                        mvHash = mvResult.hash,
                        duration = songItem.duration,
                        imgUrl = ""
                    )
                    _currentPlaying.value = PlayableItem.Mv(mvItem, mvResult.url, songItem.hash)
                    appContext?.let {
                        Toast.makeText(it, "🎬 成功加载高清 MV 视频: ${mvItem.title}", Toast.LENGTH_SHORT).show()
                    }
                    startPlayback(mvResult.url, mvResult.hash)
                    downloadAndCache(mvResult.hash, mvResult.url, songItem)
                }
                result.onFailure {
                    // No official MV -> Load high-fidelity full-length audio stream
                    playAudioFallback(songItem)
                }
            }
        }
    }

    private fun playAudioFallback(songItem: SongItem) {
        _currentPlaying.value = PlayableItem.Song(songItem)
        appContext?.let {
            Toast.makeText(it, "🎵 加载原唱高保真音频: ${songItem.title}", Toast.LENGTH_SHORT).show()
        }
        KugouApi.getSongAudioUrl(songItem.title) { res ->
            res.onSuccess { url ->
                startPlayback(url, songItem.hash)
                downloadAndCache(songItem.hash, url, songItem)
            }
        }
    }

    private fun downloadAndCache(hash: String, url: String, song: SongItem) {
        if (url.startsWith("file://") || url.isEmpty() || hash.isEmpty()) return
        val context = appContext ?: return
        
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.filesDir, "ktv_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val isMv = url.contains(".mp4") || url.contains(".mkv") || url.contains("mv") || song.albumAudioId == "mv"
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

    private fun startPlayback(url: String, hash: String = "") {
        activeOriginalUrl = url
        activeHash = hash
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
                if (_isVocalEliminated.value) {
                    applyVocalEliminationSwitch(true)
                }
            }
        }

        // Only trigger background DSP generation if official studio accompaniment is not available
        if (context != null && hash.isNotEmpty() && activeOfficialAccUrl.isEmpty()) {
            KtvVocalEliminationGenerator.generateAccompaniment(context, url, hash, _dspSettings.value) { result ->
                result.onSuccess { accFile ->
                    activeAccFileUrl = "file:///" + accFile.absolutePath
                    if (_accompanimentSource.value != AccompanimentSource.OFFICIAL) {
                        _accompanimentSource.value = AccompanimentSource.DSP_FALLBACK
                    }
                    if (_isVocalEliminated.value) {
                        applyVocalEliminationSwitch(true)
                    }
                }
            }
        }
    }

    fun updateDspSettings(newSettings: DspSettings) {
        _dspSettings.value = newSettings
        sharedPreferences?.edit()?.putString("dsp_settings", gson.toJson(newSettings))?.apply()
        regenerateAccompaniment()
    }

    fun resetDspSettingsToDefault() {
        updateDspSettings(DspSettings.DEFAULT)
        appContext?.let {
            Toast.makeText(it, "🔄 已恢复最佳默认消音参数", Toast.LENGTH_SHORT).show()
        }
    }

    fun regenerateAccompaniment() {
        val context = appContext ?: return
        val hash = activeHash
        val url = activeOriginalUrl
        if (hash.isEmpty() || url.isEmpty()) return

        KtvVocalEliminationGenerator.generateAccompaniment(context, url, hash, _dspSettings.value) { result ->
            result.onSuccess { accFile ->
                activeAccFileUrl = "file:///" + accFile.absolutePath
                _accompanimentSource.value = AccompanimentSource.DSP_FALLBACK
                if (_isVocalEliminated.value) {
                    applyVocalEliminationSwitch(true)
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
            val livePlayerDurationSec = if (p.duration > 0 && p.duration != androidx.media3.common.C.TIME_UNSET) {
                (p.duration / 1000).toInt()
            } else {
                0
            }

            // Real-Time Ground-Truth Timeline Alignment Check:
            // If livePlayerDurationSec > 0 and activeOfficialAccDuration > 0, verify they align within 4 seconds.
            // For cinematic/game MVs like 逆战 (video 265s vs audio 229s), diff is 36s > 4s -> reject external studio audio!
            val durationDiff = if (livePlayerDurationSec > 0 && activeOfficialAccDuration > 0) {
                Math.abs(livePlayerDurationSec - activeOfficialAccDuration)
            } else {
                0
            }

            val isOfficialAccSuitable = activeOfficialAccUrl.isNotEmpty() && durationDiff <= 4

            val targetAccUrl = when {
                isOfficialAccSuitable -> activeOfficialAccUrl
                activeAccFileUrl.isNotEmpty() -> activeAccFileUrl
                else -> ""
            }

            if (targetAccUrl.isNotEmpty()) {
                val current = _currentPlaying.value
                val isMv = current is PlayableItem.Mv
                if (isMv && activeOriginalUrl.isNotEmpty()) {
                    // MV Video: Combine MV Video stream + Accompaniment Audio stream with MergingMediaSource
                    val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent("Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
                        .setAllowCrossProtocolRedirects(true)
                    val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context!!, httpDataSourceFactory)

                    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(activeOriginalUrl))
                    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(targetAccUrl))

                    val mergedSource = MergingMediaSource(true, true, videoSource, audioSource)
                    p.setMediaSource(mergedSource)
                } else {
                    // Audio Song: Switch to Accompaniment audio stream / WAV file
                    p.setMediaItem(MediaItem.fromUri(targetAccUrl))
                }
                p.prepare()
                p.seekTo(currentPos)
                if (wasPlaying) p.play()

                val hint = if (targetAccUrl == activeOfficialAccUrl) {
                    "🎉 已自动切换至酷狗官方高保真原版伴奏"
                } else if (isMv && activeOfficialAccUrl.isNotEmpty() && !isOfficialAccSuitable) {
                    "🎙️ 该 MV 含长剧情片头，已自动启用精准音画同步消音"
                } else {
                    "🎙️ 暂无官方伴奏，已自动启用实时消音算法"
                }
                context?.let {
                    Toast.makeText(it, hint, Toast.LENGTH_SHORT).show()
                }
            } else {
                context?.let {
                    Toast.makeText(it, "⏳ 正在检索官方伴奏与消音流，请稍候...", Toast.LENGTH_SHORT).show()
                }
                // Trigger fallback DSP generation if not already started
                if (context != null && activeOriginalUrl.isNotEmpty() && activeHash.isNotEmpty() && activeAccFileUrl.isEmpty()) {
                    KtvVocalEliminationGenerator.generateAccompaniment(context, activeOriginalUrl, activeHash, _dspSettings.value) { dspRes ->
                        dspRes.onSuccess { accFile ->
                            activeAccFileUrl = "file:///" + accFile.absolutePath
                            if (_accompanimentSource.value != AccompanimentSource.OFFICIAL) {
                                _accompanimentSource.value = AccompanimentSource.DSP_FALLBACK
                            }
                            if (_isVocalEliminated.value) {
                                applyVocalEliminationSwitch(true)
                            }
                        }
                    }
                }
            }
        } else {
            // Revert to original vocal
            if (activeOriginalUrl.isNotEmpty()) {
                p.setMediaItem(MediaItem.fromUri(activeOriginalUrl))
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverrides().build()
                p.prepare()
                p.seekTo(currentPos)
                if (wasPlaying) p.play()
                context?.let {
                    Toast.makeText(it, "🎤 已切换为原唱模式", Toast.LENGTH_SHORT).show()
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
            val dspJson = prefs.getString("dsp_settings", null)
            if (dspJson != null) {
                try {
                    _dspSettings.value = gson.fromJson(dspJson, DspSettings::class.java)
                } catch (e: Exception) {
                    _dspSettings.value = DspSettings.DEFAULT
                }
            }
        }
    }

    private fun addToHistory(song: SongItem) {
        val list = _history.value.toMutableList()
        list.removeAll { it.title == song.title && it.artist == song.artist }
        list.add(0, song)
        if (list.size > 50) {
            list.removeAt(list.size - 1)
        }
        _history.value = list
        saveList("history", list)
    }

    fun isSongFavorited(song: SongItem): Boolean {
        return _favorites.value.any { 
            (it.hash.isNotEmpty() && it.hash == song.hash) || 
            (it.title == song.title && it.artist == song.artist) 
        }
    }

    fun isCurrentFavorited(): Boolean {
        val current = _currentPlaying.value ?: return false
        return when (current) {
            is PlayableItem.Song -> isSongFavorited(current.songItem)
            is PlayableItem.Mv -> _favorites.value.any { it.title == current.mvItem.title }
        }
    }

    fun toggleCurrentFavorite() {
        val current = _currentPlaying.value ?: return
        val songItem = when (current) {
            is PlayableItem.Song -> current.songItem
            is PlayableItem.Mv -> SongItem(current.mvItem.title, current.mvItem.artist, current.mvItem.mvHash, "mv", current.mvItem.duration, current.mvItem.mvHash)
        }
        toggleFavorite(songItem)
    }

    fun toggleFavorite(song: SongItem) {
        val list = _favorites.value.toMutableList()
        val existing = list.firstOrNull { 
            (it.hash.isNotEmpty() && it.hash == song.hash) || 
            (it.title == song.title && it.artist == song.artist) 
        }
        if (existing != null) {
            list.remove(existing)
            appContext?.let {
                Toast.makeText(it, "已取消收藏: ${song.title}", Toast.LENGTH_SHORT).show()
            }
        } else {
            list.add(0, song)
            appContext?.let {
                Toast.makeText(it, "❤️ 已添加到收藏: ${song.title}", Toast.LENGTH_SHORT).show()
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
