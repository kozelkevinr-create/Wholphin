package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.youtube.YouTubeChoice
import com.github.damontecres.wholphin.services.youtube.YouTubeSettings
import com.github.damontecres.wholphin.services.youtube.YouTubeSettings.Companion.labelFor
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemEntry
import com.github.damontecres.wholphin.ui.components.DialogPopup

/**
 * A list of options to pick one from. Closing the dialog, including by picking something, calls [onDismiss]
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<YouTubeChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) = DialogPopup(
    showDialog = true,
    title = title,
    dialogItems =
        choices.map { choice ->
            DialogItem(
                headlineContent = { Text(choice.label) },
                onClick = { onSelect(choice.value) },
                selected = choice.value == selected,
                dismissOnClick = true,
            )
        },
    onDismissRequest = onDismiss,
    dismissOnClick = false,
    waitToLoad = false,
)

private enum class SettingsMenu { Main, Audio, Region, Quality, FrameRate, Captions }

/**
 * Change how YouTube videos are found and played
 */
@Composable
fun YouTubeSettingsDialog(
    settings: YouTubeSettings,
    onDismiss: () -> Unit,
) {
    val state by settings.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(SettingsMenu.Main) }
    val backToMain = { menu = SettingsMenu.Main }

    fun entry(
        title: String,
        value: String,
        target: SettingsMenu,
    ): DialogItemEntry =
        DialogItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(value) },
            onClick = { menu = target },
        )

    when (menu) {
        SettingsMenu.Main -> {
            DialogPopup(
                showDialog = true,
                title = "YouTube options",
                dialogItems =
                    listOf(
                        entry("Audio language", YouTubeSettings.AUDIO_LANGUAGES.labelFor(state.audioLanguage), SettingsMenu.Audio),
                        entry("Region for search and titles", YouTubeSettings.REGIONS.labelFor(state.region), SettingsMenu.Region),
                        entry("Maximum quality", YouTubeSettings.QUALITIES.labelFor(state.maxHeight), SettingsMenu.Quality),
                        entry("Frame rate", YouTubeSettings.FRAME_RATES.labelFor(state.allow60fps), SettingsMenu.FrameRate),
                        entry("Captions", YouTubeSettings.CAPTION_LANGUAGES.labelFor(state.captionLanguage), SettingsMenu.Captions),
                    ),
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        SettingsMenu.Audio -> {
            ChoiceDialog("Audio language", YouTubeSettings.AUDIO_LANGUAGES, state.audioLanguage, settings::setAudioLanguage, backToMain)
        }

        SettingsMenu.Region -> {
            ChoiceDialog("Region", YouTubeSettings.REGIONS, state.region, settings::setRegion, backToMain)
        }

        SettingsMenu.Quality -> {
            ChoiceDialog("Maximum quality", YouTubeSettings.QUALITIES, state.maxHeight, settings::setMaxHeight, backToMain)
        }

        SettingsMenu.FrameRate -> {
            ChoiceDialog("Frame rate", YouTubeSettings.FRAME_RATES, state.allow60fps, settings::setAllow60fps, backToMain)
        }

        SettingsMenu.Captions -> {
            ChoiceDialog("Captions", YouTubeSettings.CAPTION_LANGUAGES, state.captionLanguage, settings::setCaptionLanguage, backToMain)
        }
    }
}
