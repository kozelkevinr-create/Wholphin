package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.runtime.Composable
import com.github.damontecres.wholphin.services.youtube.YouTubeChannel
import com.github.damontecres.wholphin.services.youtube.YouTubeEntry
import com.github.damontecres.wholphin.services.youtube.YouTubePlaylist
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemEntry
import com.github.damontecres.wholphin.ui.components.DialogPopup

/**
 * What can be done with a video, channel, or playlist, shown on a long press
 *
 * @param savedUrls the urls of the channels & playlists that are saved
 */
@Composable
fun YouTubeEntryActionsDialog(
    entry: YouTubeEntry,
    savedUrls: Set<String>,
    onDownload: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleChannelSaved: (YouTubeVideo) -> Unit,
    onOpenChannel: (YouTubeVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = mutableListOf<DialogItemEntry>()
    when (entry) {
        is YouTubeVideo -> {
            items += DialogItem("Download video", dismissOnClick = true, onClick = onDownload)
            entry.channelUrl?.let { channelUrl ->
                items += DialogItem("Go to channel", dismissOnClick = true) { onOpenChannel(entry) }
                items +=
                    DialogItem(
                        if (channelUrl in savedUrls) "Remove channel from saved" else "Save channel",
                        dismissOnClick = true,
                    ) { onToggleChannelSaved(entry) }
            }
        }

        is YouTubePlaylist -> {
            items += DialogItem("Download playlist", dismissOnClick = true, onClick = onDownload)
            items += DialogItem(saveLabel(entry.url in savedUrls), dismissOnClick = true, onClick = onToggleSaved)
        }

        is YouTubeChannel -> {
            items += DialogItem(saveLabel(entry.url in savedUrls), dismissOnClick = true, onClick = onToggleSaved)
        }
    }
    DialogPopup(
        showDialog = true,
        title = entry.title,
        dialogItems = items,
        onDismissRequest = onDismiss,
    )
}

private fun saveLabel(saved: Boolean) = if (saved) "Remove from saved" else "Save"
