package com.github.damontecres.wholphin.ui.youtube

import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.services.youtube.DownloadJob
import com.github.damontecres.wholphin.services.youtube.YouTubeDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

data class YouTubeDownloadsState(
    val loading: Boolean = true,
    val configured: Boolean = true,
    val jobs: List<DownloadJob> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class YouTubeDownloadsViewModel
    @Inject
    constructor(
        private val downloadService: YouTubeDownloadService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(YouTubeDownloadsState())
        val state: StateFlow<YouTubeDownloadsState> = _state

        suspend fun refresh() {
            if (!downloadService.isConfigured) {
                _state.update { YouTubeDownloadsState(loading = false, configured = false) }
                return
            }
            try {
                val jobs = downloadService.jobs()
                _state.update { YouTubeDownloadsState(loading = false, jobs = jobs) }
            } catch (ex: Exception) {
                Timber.w(ex, "Error getting downloads")
                // Keep showing the last known jobs
                _state.update { it.copy(loading = false, error = ex.localizedMessage ?: ex.javaClass.simpleName) }
            }
        }
    }
