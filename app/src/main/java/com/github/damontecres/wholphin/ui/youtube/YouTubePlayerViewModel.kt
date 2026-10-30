package com.github.damontecres.wholphin.ui.youtube

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.services.youtube.NewPipeDownloader
import com.github.damontecres.wholphin.services.youtube.YouTubePlayable
import com.github.damontecres.wholphin.services.youtube.YouTubeService
import com.github.damontecres.wholphin.services.youtube.YouTubeSettings
import com.github.damontecres.wholphin.services.youtube.YouTubeSubtitle
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
    val subtitles: List<YouTubeSubtitle> = emptyList(),
    val selectedSubtitleId: String? = null,
    /** Resolutions available, highest first */
    val heights: List<Int> = emptyList(),
    /** Resolution being played, 0 if unknown */
    val height: Int = 0,
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
        private val settings: YouTubeSettings,
    ) : ViewModel() {
        private val _state = MutableStateFlow(YouTubePlayerState())
        val state: StateFlow<YouTubePlayerState> = _state

        private var player: ExoPlayer? = null
        private var url: String? = null

        /** Caption track to turn on as soon as the player reports its tracks */
        private var pendingSubtitleId: String? = null

        private val tracksListener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    pendingSubtitleId?.let { id -> if (selectSubtitleTrack(id)) pendingSubtitleId = null }
                }
            }

        fun load(url: String) {
            if (player != null) return
            this.url = url
            viewModelScope.launch {
                try {
                    val playable = youTubeService.resolve(url)
                    withContext(WholphinDispatchers.Main) {
                        val newPlayer =
                            ExoPlayer
                                .Builder(
                                    context,
                                    DefaultRenderersFactory(context).setEnableDecoderFallback(true),
                                ).setLoadControl(
                                    // Smaller buffers than the default: separate video+audio at 1080p adds up on a low-RAM stick
                                    DefaultLoadControl
                                        .Builder()
                                        .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
                                        .setTargetBufferBytes(24 * 1024 * 1024)
                                        .setPrioritizeTimeOverSizeThresholds(false)
                                        .build(),
                                ).build()
                        newPlayer.addListener(tracksListener)
                        player = newPlayer
                        startPlayback(newPlayer, playable, startPositionMs = 0, initial = true)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error playing YouTube video")
                    _state.update {
                        YouTubePlayerState(loading = false, error = ex.localizedMessage ?: ex.javaClass.simpleName)
                    }
                }
            }
        }

        /**
         * Switch to a different resolution, staying at the same spot in the video
         */
        fun changeQuality(height: Int) {
            val url = url ?: return
            val player = player ?: return
            if (height == _state.value.height) return
            viewModelScope.launch {
                try {
                    val position = withContext(WholphinDispatchers.Main) { player.currentPosition }
                    val playable = youTubeService.resolve(url, maxHeight = height)
                    withContext(WholphinDispatchers.Main) {
                        startPlayback(player, playable, startPositionMs = position, keepSubtitle = _state.value.selectedSubtitleId)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error changing YouTube quality")
                }
            }
        }

        /**
         * Show the caption track with this [id], or turn captions off if null
         */
        fun selectSubtitle(id: String?) {
            val player = player ?: return
            if (id == null) {
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                _state.update { it.copy(selectedSubtitleId = null) }
            } else if (selectSubtitleTrack(id)) {
                _state.update { it.copy(selectedSubtitleId = id) }
            }
        }

        private fun selectSubtitleTrack(id: String): Boolean {
            val player = player ?: return false
            val group =
                player.currentTracks.groups.firstOrNull {
                    it.type == C.TRACK_TYPE_TEXT && it.length > 0 && it.getTrackFormat(0).id == id
                } ?: return false
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .build()
            return true
        }

        private fun startPlayback(
            player: ExoPlayer,
            playable: YouTubePlayable,
            startPositionMs: Long,
            initial: Boolean = false,
            keepSubtitle: String? = null,
        ) {
            // Captions: use the preferred language when starting, but keep the current choice when only the quality changed
            val subtitle =
                if (initial) preferredSubtitle(playable) else playable.subtitles.firstOrNull { it.id == keepSubtitle }
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null)
                    .build()
            pendingSubtitleId = subtitle?.id
            player.setMediaSource(buildSource(playable), startPositionMs)
            player.prepare()
            player.playWhenReady = true
            _state.update {
                YouTubePlayerState(
                    loading = false,
                    player = player,
                    subtitles = playable.subtitles,
                    selectedSubtitleId = subtitle?.id,
                    heights = playable.heights,
                    height = playable.height,
                )
            }
        }

        private fun preferredSubtitle(playable: YouTubePlayable): YouTubeSubtitle? {
            val language = settings.current.captionLanguage
            if (language.isBlank()) return null
            val matching = playable.subtitles.filter { it.language.substringBefore('-') == language }
            // Prefer human made captions over auto generated ones
            return matching.firstOrNull { !it.label.endsWith("(auto)") } ?: matching.firstOrNull()
        }

        private fun buildSource(playable: YouTubePlayable): MediaSource {
            val factory =
                OkHttpDataSource.Factory(okHttpClient).setUserAgent(NewPipeDownloader.USER_AGENT)

            fun progressive(url: String) = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
            val subtitleSources =
                playable.subtitles.map {
                    val config =
                        MediaItem.SubtitleConfiguration
                            .Builder(it.url.toUri())
                            .setId(it.id)
                            .setMimeType(it.mimeType)
                            .setLanguage(it.language)
                            .setLabel(it.label)
                            .setSelectionFlags(0)
                            .build()
                    SingleSampleMediaSource.Factory(factory).createMediaSource(config, C.TIME_UNSET)
                }
            return when {
                playable.hlsUrl != null -> {
                    HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(playable.hlsUrl))
                }

                playable.videoUrl != null && playable.audioUrl != null -> {
                    MergingMediaSource(progressive(playable.videoUrl), progressive(playable.audioUrl), *subtitleSources.toTypedArray())
                }

                else -> {
                    MergingMediaSource(progressive(playable.videoUrl!!), *subtitleSources.toTypedArray())
                }
            }
        }

        override fun onCleared() {
            player?.removeListener(tracksListener)
            player?.release()
            player = null
        }
    }
