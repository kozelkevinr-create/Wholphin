package com.github.damontecres.wholphin.services.youtube

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A channel or playlist the user saved so it is easy to find again
 */
@Serializable
data class SavedYouTube(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val isPlaylist: Boolean = false,
    /** Who made the playlist, or how many subscribers a channel has */
    val detail: String? = null,
    val count: Long = 0,
) {
    fun toEntry(): YouTubeEntry =
        if (isPlaylist) {
            YouTubePlaylist(url, title, thumbnailUrl, detail, count)
        } else {
            YouTubeChannel(url, title, thumbnailUrl, count)
        }
}

data class YouTubeLibraryState(
    val saved: List<SavedYouTube> = emptyList(),
    /** Videos that were watched, most recent first */
    val history: List<YouTubeVideo> = emptyList(),
) {
    val savedUrls: Set<String> = saved.mapTo(mutableSetOf()) { it.url }
    val channels: List<SavedYouTube> get() = saved.filter { !it.isPlaylist }
}

/**
 * Remembers the channels & playlists the user saved and the videos they watched, on the device.
 *
 * This is what "suggested for you" is built from since there is no YouTube account to personalize with.
 */
@Singleton
class YouTubeLibrary
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        private val json = Json { ignoreUnknownKeys = true }

        private val _state =
            MutableStateFlow(
                YouTubeLibraryState(
                    saved = read(KEY_SAVED),
                    history = read(KEY_HISTORY),
                ),
            )
        val state: StateFlow<YouTubeLibraryState> = _state

        private inline fun <reified T> read(key: String): List<T> =
            try {
                prefs.getString(key, null)?.let { json.decodeFromString<List<T>>(it) }.orEmpty()
            } catch (ex: Exception) {
                Timber.e(ex, "Error reading YouTube $key")
                emptyList()
            }

        /**
         * Save an entry, or remove it if it was already saved. Videos can't be saved, only channels and playlists
         */
        fun toggleSaved(entry: YouTubeEntry) {
            val item =
                when (entry) {
                    is YouTubeChannel -> SavedYouTube(entry.url, entry.title, entry.thumbnailUrl, false, null, entry.subscribers)
                    is YouTubePlaylist -> SavedYouTube(entry.url, entry.title, entry.thumbnailUrl, true, entry.uploader, entry.videoCount)
                    is YouTubeVideo -> return
                }
            toggleSaved(item)
        }

        fun toggleSaved(item: SavedYouTube) {
            _state.update { current ->
                val saved =
                    if (item.url in current.savedUrls) {
                        current.saved.filter { it.url != item.url }
                    } else {
                        listOf(item) + current.saved
                    }
                current.copy(saved = saved).also { prefs.edit { putString(KEY_SAVED, json.encodeToString(saved)) } }
            }
        }

        /**
         * Note that a video was watched
         */
        fun recordWatched(video: YouTubeVideo) {
            _state.update { current ->
                val history = (listOf(video) + current.history.filter { it.url != video.url }).take(MAX_HISTORY)
                current.copy(history = history).also { prefs.edit { putString(KEY_HISTORY, json.encodeToString(history)) } }
            }
        }

        fun clearHistory() {
            _state.update { it.copy(history = emptyList()) }
            prefs.edit { remove(KEY_HISTORY) }
        }

        companion object {
            private const val PREFS_NAME = "youtube_library"
            private const val KEY_SAVED = "saved"
            private const val KEY_HISTORY = "history"
            private const val MAX_HISTORY = 50
        }
    }
