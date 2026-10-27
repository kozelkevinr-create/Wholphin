package com.github.damontecres.wholphin.ui.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.youtube.YouTubeDownloadService
import com.github.damontecres.wholphin.services.youtube.YouTubeListing
import com.github.damontecres.wholphin.services.youtube.YouTubeService
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class YouTubeListingState(
    val loading: Boolean = true,
    val listing: YouTubeListing? = null,
    val error: String? = null,
)

/**
 * Loads the videos for a channel or a playlist
 */
@HiltViewModel
class YouTubeListingViewModel
    @Inject
    constructor(
        private val youTubeService: YouTubeService,
        private val downloadService: YouTubeDownloadService,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(YouTubeListingState())
        val state: StateFlow<YouTubeListingState> = _state

        private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val messages: SharedFlow<String> = _messages

        private var loadedUrl: String? = null

        fun load(
            url: String,
            playlist: Boolean,
        ) {
            if (loadedUrl == url) return
            loadedUrl = url
            viewModelScope.launch {
                try {
                    val listing =
                        if (playlist) youTubeService.playlistVideos(url) else youTubeService.channelVideos(url)
                    _state.update { YouTubeListingState(loading = false, listing = listing) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading YouTube listing")
                    _state.update {
                        YouTubeListingState(loading = false, error = ex.localizedMessage ?: ex.javaClass.simpleName)
                    }
                }
            }
        }

        fun play(video: YouTubeVideo) {
            navigationManager.navigateTo(Destination.YouTubePlayer(video.url, video.title))
        }

        fun download(
            url: String,
            playlist: Boolean,
            name: String?,
        ) {
            viewModelScope.launch {
                try {
                    downloadService.download(url, playlist)
                    _messages.emit("Download queued${name?.let { ": $it" }.orEmpty()}")
                } catch (ex: Exception) {
                    Timber.e(ex, "Error queueing download")
                    _messages.emit("Download failed: ${ex.localizedMessage ?: ex.javaClass.simpleName}")
                }
            }
        }
    }
