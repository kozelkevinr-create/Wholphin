package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.showToast

/**
 * The videos of a YouTube channel or playlist. Playlists can be downloaded in full.
 */
@Composable
fun YouTubeListingPage(
    url: String,
    title: String,
    playlist: Boolean,
    modifier: Modifier = Modifier,
    viewModel: YouTubeListingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(url) { viewModel.load(url, playlist) }
    LaunchedEffect(Unit) { viewModel.messages.collect { showToast(context, it) } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var videoToDownload by remember { mutableStateOf<YouTubeVideo?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.listing?.title ?: title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (playlist) {
                TextButton(
                    stringRes = R.string.youtube_download_playlist,
                    onClick = { viewModel.download(url, playlist = true, name = state.listing?.title ?: title) },
                )
            }
        }
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

                state.listing?.videos.isNullOrEmpty() -> {
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
                        val videos = state.listing?.videos.orEmpty()
                        itemsIndexed(videos, key = { _, video -> video.url }) { index, video ->
                            if (index >= videos.size - LOAD_MORE_AHEAD) {
                                LaunchedEffect(videos.size) { viewModel.loadMore() }
                            }
                            YouTubeEntryCard(
                                entry = video,
                                onClick = { viewModel.play(video) },
                                onLongClick = { videoToDownload = video },
                            )
                        }
                    }
                }
            }
        }
    }

    videoToDownload?.let { video ->
        ConfirmDialog(
            title = stringResource(R.string.youtube_download_video),
            body = video.title,
            onCancel = { videoToDownload = null },
            onConfirm = {
                videoToDownload = null
                viewModel.download(video.url, playlist = false, name = video.title)
            },
        )
    }
}

/** Start loading the next page when this close to the end of the list */
private const val LOAD_MORE_AHEAD = 6
