package com.github.damontecres.wholphin.ui.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.youtube.YouTubeService
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class YouTubeState(
    val loading: Boolean = false,
    val videos: List<YouTubeVideo> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
)

@HiltViewModel
class YouTubeViewModel
    @Inject
    constructor(
        private val youTubeService: YouTubeService,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(YouTubeState())
        val state: StateFlow<YouTubeState> = _state

        fun search(query: String) {
            if (query.isNotBlank()) {
                load { youTubeService.search(query) }
            }
        }

        fun play(video: YouTubeVideo) {
            navigationManager.navigateTo(Destination.YouTubePlayer(video.url, video.title))
        }

        private fun load(fetch: suspend () -> List<YouTubeVideo>) {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                try {
                    val videos = fetch()
                    _state.update { YouTubeState(loading = false, videos = videos, searched = true) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading YouTube")
                    _state.update { YouTubeState(loading = false, error = ex.localizedMessage ?: ex.javaClass.simpleName, searched = true) }
                }
            }
        }
    }
