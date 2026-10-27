package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.github.damontecres.wholphin.services.youtube.YouTubeDownloadService
import com.github.damontecres.wholphin.services.youtube.YouTubeVideo
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.showToast

/**
 * Search YouTube, play videos, and send videos or playlists to the download server
 */
@Composable
fun YouTubePage(
    modifier: Modifier = Modifier,
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    var videoToDownload by remember { mutableStateOf<YouTubeVideo?>(null) }
    var urlToDownload by remember { mutableStateOf<String?>(null) }
    var settingsStep by remember { mutableStateOf<SettingsStep?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { showToast(context, it) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.youtube),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchEditTextBox(
                value = query,
                onValueChange = { query = it },
                onSearchClick = {
                    if (YouTubeDownloadService.isYouTubeUrl(query)) {
                        urlToDownload = query.trim()
                    } else {
                        viewModel.search(query)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            TextButton(
                stringRes = R.string.youtube_download_settings,
                onClick = { settingsStep = SettingsStep.Url },
            )
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
                            YouTubeCard(
                                video = video,
                                onClick = { viewModel.play(video) },
                                onLongClick = { videoToDownload = video },
                            )
                        }
                    }
                }
            }
        }
    }

    // Downloads need the server details first
    fun requestDownload(action: () -> Unit) {
        if (viewModel.downloadsConfigured) action() else settingsStep = SettingsStep.Url
    }

    videoToDownload?.let { video ->
        ConfirmDialog(
            title = stringResource(R.string.youtube_download_video),
            body = video.title,
            onCancel = { videoToDownload = null },
            onConfirm = {
                videoToDownload = null
                requestDownload { viewModel.download(video.url, playlist = false, name = video.title) }
            },
        )
    }

    urlToDownload?.let { url ->
        DownloadUrlDialog(
            url = url,
            onCancel = { urlToDownload = null },
            onDownload = { playlist ->
                urlToDownload = null
                requestDownload { viewModel.download(url, playlist) }
            },
        )
    }

    when (settingsStep) {
        SettingsStep.Url -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = stringResource(R.string.youtube_download_server_url),
                        value = viewModel.downloadServerUrl,
                        keyboardOptions = KeyboardOptions.Default,
                        onSubmit = {},
                    ),
                onSave = { url ->
                    viewModel.saveDownloadServer(url, viewModel.downloadToken)
                    settingsStep = SettingsStep.Token
                },
                onDismissRequest = { settingsStep = null },
            )
        }

        SettingsStep.Token -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = stringResource(R.string.youtube_download_token),
                        value = viewModel.downloadToken,
                        keyboardOptions = KeyboardOptions.Default,
                        onSubmit = {},
                    ),
                onSave = { token ->
                    viewModel.saveDownloadServer(viewModel.downloadServerUrl, token)
                    settingsStep = null
                },
                onDismissRequest = { settingsStep = null },
            )
        }

        null -> {}
    }
}

private enum class SettingsStep { Url, Token }

@Composable
private fun DownloadUrlDialog(
    url: String,
    onCancel: () -> Unit,
    onDownload: (playlist: Boolean) -> Unit,
) = BasicDialog(onDismissRequest = onCancel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.youtube_download),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = url,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(stringRes = R.string.youtube_download_video, onClick = { onDownload(false) })
        TextButton(stringRes = R.string.youtube_download_playlist, onClick = { onDownload(true) })
        TextButton(stringRes = R.string.cancel, onClick = onCancel)
    }
}

@Composable
private fun YouTubeCard(
    video: YouTubeVideo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
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
