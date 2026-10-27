package com.github.damontecres.wholphin.ui.youtube

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.services.youtube.NewPipeDownloader
import com.github.damontecres.wholphin.services.youtube.YouTubePlayable
import com.github.damontecres.wholphin.services.youtube.YouTubeService
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

data class YouTubePlayerState(
    val loading: Boolean = true,
    val player: Player? = null,
    val error: String? = null,
)

/**
 * Resolves a YouTube video and plays it in a simple ExoPlayer
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class YouTubePlayerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
        private val youTubeService: YouTubeService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(YouTubePlayerState())
        val state: StateFlow<YouTubePlayerState> = _state

        private var player: ExoPlayer? = null

        fun load(url: String) {
            if (player != null) return
            viewModelScope.launch {
                try {
                    val playable = youTubeService.resolve(url)
                    withContext(WholphinDispatchers.Main) {
                        val newPlayer = ExoPlayer.Builder(context).build()
                        newPlayer.setMediaSource(buildSource(playable))
                        newPlayer.prepare()
                        newPlayer.playWhenReady = true
                        player = newPlayer
                        _state.update { YouTubePlayerState(loading = false, player = newPlayer) }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error playing YouTube video")
                    _state.update {
                        YouTubePlayerState(loading = false, error = ex.localizedMessage ?: ex.javaClass.simpleName)
                    }
                }
            }
        }

        private fun buildSource(playable: YouTubePlayable): MediaSource {
            val factory =
                OkHttpDataSource.Factory(okHttpClient).setUserAgent(NewPipeDownloader.USER_AGENT)
            fun progressive(url: String) = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
            return when {
                playable.hlsUrl != null -> {
                    HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(playable.hlsUrl))
                }

                playable.videoUrl != null && playable.audioUrl != null -> {
                    MergingMediaSource(progressive(playable.videoUrl), progressive(playable.audioUrl))
                }

                else -> {
                    progressive(playable.videoUrl!!)
                }
            }
        }

        override fun onCleared() {
            player?.release()
            player = null
        }
    }
