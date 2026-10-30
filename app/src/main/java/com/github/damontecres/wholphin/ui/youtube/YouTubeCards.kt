package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.youtube.YouTubeChannel
import com.github.damontecres.wholphin.services.youtube.YouTubeEntry
import com.github.damontecres.wholphin.services.youtube.YouTubePlaylist
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo

/**
 * A card for a video, channel, or playlist
 */
@Composable
fun YouTubeEntryCard(
    entry: YouTubeEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle =
        when (entry) {
            is YouTubeVideo -> entry.channel
            is YouTubeChannel -> "Channel" + formatCount(entry.subscribers, "subscribers")
            is YouTubePlaylist -> "Playlist" + formatCount(entry.videoCount, "videos") + entry.uploader?.let { " · $it" }.orEmpty()
        }
    Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        Column {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatCount(
    count: Long,
    label: String,
): String = if (count > 0) " \u00B7 ${compact(count)} $label" else ""

private fun compact(count: Long): String =
    when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000 -> "%.1fK".format(count / 1_000.0).replace(".0K", "K")
        else -> count.toString()
    }
