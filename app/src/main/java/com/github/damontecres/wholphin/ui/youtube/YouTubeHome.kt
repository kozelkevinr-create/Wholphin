package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.youtube.SavedYouTube
import com.github.damontecres.wholphin.services.youtube.YouTubeEntry
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.TextButton

/**
 * What's shown before searching: the channels & playlists the user saved and suggested videos
 */
@Composable
fun YouTubeHome(
    saved: List<SavedYouTube>,
    suggestions: YouTubeSuggestionsState,
    onOpen: (YouTubeEntry) -> Unit,
    onLongClick: (YouTubeEntry) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullWidth: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(280.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (saved.isNotEmpty()) {
            item(key = "saved_title", span = fullWidth) { SectionTitle(stringResource(R.string.youtube_saved)) }
            item(key = "saved_row", span = fullWidth) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(saved, key = { it.url }) { item ->
                        val entry = item.toEntry()
                        YouTubeEntryCard(
                            entry = entry,
                            onClick = { onOpen(entry) },
                            onLongClick = { onLongClick(entry) },
                            modifier = Modifier.width(260.dp),
                        )
                    }
                }
            }
        }
        item(key = "suggested_title", span = fullWidth) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionTitle(stringResource(R.string.youtube_suggested), Modifier.weight(1f, fill = false))
                TextButton(stringRes = R.string.youtube_refresh, onClick = onRefresh)
            }
        }
        when {
            suggestions.videos.isNotEmpty() -> {
                items(suggestions.videos, key = { it.url }) { video ->
                    YouTubeEntryCard(
                        entry = video,
                        onClick = { onOpen(video) },
                        onLongClick = { onLongClick(video) },
                    )
                }
            }

            suggestions.loading -> {
                item(key = "suggested_loading", span = fullWidth) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgress() }
                }
            }

            else -> {
                item(key = "suggested_empty", span = fullWidth) {
                    Text(
                        text = stringResource(R.string.youtube_suggested_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = modifier,
)
