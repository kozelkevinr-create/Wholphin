package com.github.damontecres.wholphin.ui.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.youtube.YouTubeChannel
import com.github.damontecres.wholphin.services.youtube.YouTubeCursor
import com.github.damontecres.wholphin.services.youtube.YouTubeDownloadService
import com.github.damontecres.wholphin.services.youtube.YouTubeEntry
import com.github.damontecres.wholphin.services.youtube.YouTubeLibrary
import com.github.damontecres.wholphin.services.youtube.YouTubePageResult
import com.github.damontecres.wholphin.services.youtube.YouTubePlaylist
import com.github.damontecres.wholphin.services.youtube.YouTubeService
import com.github.damontecres.wholphin.services.youtube.YouTubeSettings
import com.github.damontecres.wholphin.services.youtube.YouTubeSuggestions
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

data class YouTubeSuggestionsState(
    val loading: Boolean = false,
    val videos: List<YouTubeVideo> = emptyList(),
)

data class YouTubeState(
    val loading: Boolean = false,
    val entries: List<YouTubeEntry> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
    val cursor: YouTubeCursor? = null,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class YouTubeViewModel
    @Inject
    constructor(
        private val youTubeService: YouTubeService,
        private val downloadService: YouTubeDownloadService,
        val settings: YouTubeSettings,
        val library: YouTubeLibrary,
        private val suggestions: YouTubeSuggestions,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(YouTubeState())
        val state: StateFlow<YouTubeState> = _state

        private val _suggestions = MutableStateFlow(YouTubeSuggestionsState())

        /** The "suggested for you" videos shown when not searching */
        val suggestionsState: StateFlow<YouTubeSuggestionsState> = _suggestions

        private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

        /**
         * One off messages to show the user such as download results
         */
        val messages: SharedFlow<String> = _messages

        val downloadsConfigured: Boolean get() = downloadService.isConfigured
        val downloadServerUrl: String get() = downloadService.serverUrl
        val downloadToken: String get() = downloadService.token

        fun saveDownloadServer(
            url: String,
            token: String,
        ) {
            downloadService.serverUrl = url
            downloadService.token = token
        }

        /**
         * Ask the server to download a video or playlist
         */
        fun download(
            url: String,
            playlist: Boolean,
            name: String? = null,
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

        /**
         * Load the "suggested for you" videos. Does nothing more than refresh if already loaded and unchanged
         */
        fun loadSuggestions(force: Boolean = false) {
            if (_suggestions.value.loading) return
            _suggestions.update { it.copy(loading = true) }
            viewModelScope.launch {
                val videos =
                    try {
                        suggestions.get(force)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error loading YouTube suggestions")
                        emptyList()
                    }
                _suggestions.update { YouTubeSuggestionsState(loading = false, videos = videos) }
            }
        }

        /**
         * Leave the search results and go back to saved items & suggestions
         */
        fun showHome() {
            _state.update { YouTubeState() }
        }

        fun toggleSaved(entry: YouTubeEntry) = library.toggleSaved(entry)

        fun search(query: String) {
            if (query.isNotBlank()) {
                load { youTubeService.search(query) }
            }
        }

        /**
         * Play a video or go to the channel or playlist
         */
        fun open(entry: YouTubeEntry) {
            val destination =
                when (entry) {
                    is YouTubeVideo -> Destination.YouTubePlayer(entry.url, entry.title)
                    is YouTubeChannel -> Destination.YouTubeChannel(entry.url, entry.title)
                    is YouTubePlaylist -> Destination.YouTubePlaylist(entry.url, entry.title)
                }
            navigationManager.navigateTo(destination)
        }

        fun showDownloads() {
            navigationManager.navigateTo(Destination.YouTubeDownloads)
        }

        /**
         * Add the next page of results, called when the user nears the end of the list
         */
        fun loadMore() {
            val cursor = _state.value.cursor ?: return
            if (_state.value.loadingMore) return
            _state.update { it.copy(loadingMore = true) }
            viewModelScope.launch {
                try {
                    val result = youTubeService.more(cursor)
                    _state.update {
                        it.copy(
                            entries = (it.entries + result.items).distinctBy { e -> e.url },
                            cursor = result.cursor,
                            loadingMore = false,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading more YouTube results")
                    // Stop trying so a broken page doesn't loop
                    _state.update { it.copy(cursor = null, loadingMore = false) }
                }
            }
        }

        private fun load(fetch: suspend () -> YouTubePageResult<YouTubeEntry>) {
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                try {
                    val result = fetch()
                    _state.update {
                        YouTubeState(
                            loading = false,
                            entries = result.items.distinctBy { e -> e.url },
                            searched = true,
                            cursor = result.cursor,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading YouTube")
                    _state.update { YouTubeState(loading = false, error = ex.localizedMessage ?: ex.javaClass.simpleName, searched = true) }
                }
            }
        }
    }
