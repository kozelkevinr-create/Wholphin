package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox

/**
 * Search YouTube & browse trending videos
 */
@Composable
fun YouTubePage(
    modifier: Modifier = Modifier,
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.youtube),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        SearchEditTextBox(
            value = query,
            onValueChange = { query = it },
            onSearchClick = { viewModel.search(query) },
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> {
                    CircularProgress(Modifier.align(Alignment.Center))
                }

                state.error != null -> {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                !state.searched -> {
                    Text(
                        text = stringResource(R.string.youtube_search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.videos.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.videos, key = { it.url }) { video ->
                            YouTubeCard(video, onClick = { viewModel.play(video) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeCard(
    video: YouTubeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                video.channel?.let {
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
