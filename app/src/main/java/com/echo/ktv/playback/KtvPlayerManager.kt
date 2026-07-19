package com.echo.ktv.playback

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private const val TAG = "KtvPlayerManager"
    private var player: ExoPlayer? = null
    private val vocalEliminator = KtvVocalEliminator()

    private val _playlist = MutableStateFlow<List<PlayableItem>>(emptyList())
    val playlist: StateFlow<List<PlayableItem>> = _playlist

    private val _currentPlaying = MutableStateFlow<PlayableItem?>(null)
    val currentPlaying: StateFlow<PlayableItem?> = _currentPlaying

    private val _isVocalEliminated = MutableStateFlow(false)
    val isVocalEliminated: StateFlow<Boolean> = _isVocalEliminated

    private val _musicVolume = MutableStateFlow(1.0f)
    val musicVolume: StateFlow<Float> = _musicVolume

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val scope = CoroutineScope(Dispatchers.Main)

    fun initialize(context: Context) {
        if (player != null) return

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

        player = ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            playNext()
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                })
            }
    }

    fun getPlayer(): ExoPlayer? = player

    fun addMvToQueue(mv: MvItem) {
        val list = _playlist.value.toMutableList()
        list.add(PlayableItem.Mv(mv))
        _playlist.value = list

        if (_currentPlaying.value == null) {
            playNext()
        }
    }

    fun addSongToQueue(song: SongItem) {
        val list = _playlist.value.toMutableList()
        list.add(PlayableItem.Song(song))
        _playlist.value = list

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

    /**
     * Play the next item from the queue.
     * MUST be called from Main thread or via scope.launch(Dispatchers.Main).
     */
    fun playNext() {
        // Ensure we're on the Main thread for ExoPlayer operations
        scope.launch(Dispatchers.Main) {
            try {
                val list = _playlist.value.toMutableList()
                if (list.isEmpty()) {
                    player?.stop()
                    _currentPlaying.value = null
                    return@launch
                }

                val nextItem = list.removeAt(0)
                _playlist.value = list
                _currentPlaying.value = nextItem

                when (nextItem) {
                    is PlayableItem.Mv -> {
                        KugouApi.getMvUrl(nextItem.mvItem.mvHash) { result ->
                            // OkHttp callback is on background thread!
                            // Must switch to Main for ExoPlayer operations
                            scope.launch(Dispatchers.Main) {
                                result.onSuccess { url ->
                                    Log.d(TAG, "MV URL resolved: ${url.take(80)}")
                                    startPlaybackInternal(url)
                                }
                                result.onFailure { e ->
                                    Log.e(TAG, "MV URL failed, trying audio fallback: ${e.message}")
                                    fallbackToAudioSearch(nextItem.title)
                                }
                            }
                        }
                    }
                    is PlayableItem.Song -> {
                        KugouApi.getSongUrl(nextItem.songItem.hash, nextItem.songItem.albumAudioId) { result ->
                            scope.launch(Dispatchers.Main) {
                                result.onSuccess { url ->
                                    Log.d(TAG, "Song URL resolved: ${url.take(80)}")
                                    startPlaybackInternal(url)
                                }
                                result.onFailure { e ->
                                    Log.e(TAG, "Song URL failed, skipping: ${e.message}")
                                    playNext()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "playNext error", e)
            }
        }
    }

    private fun fallbackToAudioSearch(title: String) {
        KugouApi.searchSong(title) { result ->
            scope.launch(Dispatchers.Main) {
                result.onSuccess { songs ->
                    if (songs.isNotEmpty()) {
                        val firstSong = songs[0]
                        KugouApi.getSongUrl(firstSong.hash, firstSong.albumAudioId) { urlResult ->
                            scope.launch(Dispatchers.Main) {
                                urlResult.onSuccess { url ->
                                    Log.d(TAG, "Fallback URL resolved: ${url.take(80)}")
                                    startPlaybackInternal(url)
                                }
                                urlResult.onFailure {
                                    Log.e(TAG, "Fallback also failed, skipping")
                                    playNext()
                                }
                            }
                        }
                    } else {
                        playNext()
                    }
                }
                result.onFailure {
                    playNext()
                }
            }
        }
    }

    /**
     * Internal playback start — MUST be called from Main thread.
     */
    private fun startPlaybackInternal(url: String) {
        try {
            player?.let { p ->
                p.stop()
                p.clearMediaItems()
                val mediaItem = MediaItem.fromUri(url)
                p.setMediaItem(mediaItem)
                p.volume = _musicVolume.value
                p.prepare()
                p.play()
                setVocalElimination(_isVocalEliminated.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startPlayback error", e)
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
}
