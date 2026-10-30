package com.github.damontecres.wholphin.services.youtube

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A choice in a settings list. [value] is what gets stored, [label] is shown to the user
 */
data class YouTubeChoice<T>(
    val value: T,
    val label: String,
)

/**
 * The user's YouTube preferences
 *
 * @param audioLanguage language code of the audio track to prefer, blank for the video's original audio
 * @param region region (country code) used for search results and titles
 * @param maxHeight highest video resolution to play
 * @param allow60fps whether to prefer 60fps streams when available
 * @param captionLanguage language code of captions to turn on automatically, blank for off
 */
data class YouTubeSettingsState(
    val audioLanguage: String = "",
    val region: String = "US",
    val maxHeight: Int = 1080,
    val allow60fps: Boolean = false,
    val captionLanguage: String = "",
)

/**
 * Stores [YouTubeSettingsState] on the device
 */
@Singleton
class YouTubeSettings
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private val _state =
            MutableStateFlow(
                YouTubeSettingsState(
                    audioLanguage = prefs.getString(KEY_AUDIO, "").orEmpty(),
                    region = prefs.getString(KEY_REGION, "US").orEmpty().ifBlank { "US" },
                    maxHeight = prefs.getInt(KEY_MAX_HEIGHT, 1080),
                    allow60fps = prefs.getBoolean(KEY_60FPS, false),
                    captionLanguage = prefs.getString(KEY_CAPTIONS, "").orEmpty(),
                ),
            )
        val state: StateFlow<YouTubeSettingsState> = _state

        val current: YouTubeSettingsState get() = _state.value

        fun setAudioLanguage(value: String) {
            prefs.edit { putString(KEY_AUDIO, value) }
            _state.update { it.copy(audioLanguage = value) }
        }

        fun setRegion(value: String) {
            prefs.edit { putString(KEY_REGION, value) }
            _state.update { it.copy(region = value) }
        }

        fun setMaxHeight(value: Int) {
            prefs.edit { putInt(KEY_MAX_HEIGHT, value) }
            _state.update { it.copy(maxHeight = value) }
        }

        fun setAllow60fps(value: Boolean) {
            prefs.edit { putBoolean(KEY_60FPS, value) }
            _state.update { it.copy(allow60fps = value) }
        }

        fun setCaptionLanguage(value: String) {
            prefs.edit { putString(KEY_CAPTIONS, value) }
            _state.update { it.copy(captionLanguage = value) }
        }

        companion object {
            private const val PREFS_NAME = "youtube_settings"
            private const val KEY_AUDIO = "audio_language"
            private const val KEY_REGION = "region"
            private const val KEY_MAX_HEIGHT = "max_height"
            private const val KEY_60FPS = "allow_60fps"
            private const val KEY_CAPTIONS = "caption_language"

            /** Languages offered for audio and captions */
            val LANGUAGES =
                listOf(
                    YouTubeChoice("en", "English"),
                    YouTubeChoice("es", "Español"),
                    YouTubeChoice("fr", "Français"),
                    YouTubeChoice("de", "Deutsch"),
                    YouTubeChoice("it", "Italiano"),
                    YouTubeChoice("pt", "Português"),
                    YouTubeChoice("ja", "日本語"),
                    YouTubeChoice("ko", "한국어"),
                    YouTubeChoice("hi", "हिन्दी"),
                )

            val AUDIO_LANGUAGES = listOf(YouTubeChoice("", "Original")) + LANGUAGES

            val CAPTION_LANGUAGES = listOf(YouTubeChoice("", "Off")) + LANGUAGES

            /** Region code to the language used for titles & search in that region */
            private val REGION_LANGUAGES =
                linkedMapOf(
                    "US" to ("United States" to "en"),
                    "GB" to ("United Kingdom" to "en"),
                    "CA" to ("Canada" to "en"),
                    "AU" to ("Australia" to "en"),
                    "IN" to ("India" to "en"),
                    "DE" to ("Germany" to "de"),
                    "FR" to ("France" to "fr"),
                    "ES" to ("Spain" to "es"),
                    "MX" to ("Mexico" to "es"),
                    "IT" to ("Italy" to "it"),
                    "BR" to ("Brazil" to "pt"),
                    "JP" to ("Japan" to "ja"),
                    "KR" to ("South Korea" to "ko"),
                )

            val REGIONS = REGION_LANGUAGES.map { (code, v) -> YouTubeChoice(code, v.first) }

            fun languageForRegion(region: String): String = REGION_LANGUAGES[region]?.second ?: "en"

            /** Heights offered. Only H.264 streams are used, which YouTube serves up to 1080p */
            val QUALITIES =
                listOf(
                    YouTubeChoice(1080, "1080p"),
                    YouTubeChoice(720, "720p"),
                    YouTubeChoice(480, "480p"),
                    YouTubeChoice(360, "360p"),
                )

            val FRAME_RATES =
                listOf(
                    YouTubeChoice(false, "30 fps (smoother on weaker devices)"),
                    YouTubeChoice(true, "Up to 60 fps"),
                )

            fun <T> List<YouTubeChoice<T>>.labelFor(value: T): String = firstOrNull { it.value == value }?.label ?: value.toString()
        }
    }
