package com.github.damontecres.wholphin.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.youtube.DownloadJob
import com.github.damontecres.wholphin.ui.components.CircularProgress
import kotlinx.coroutines.delay

private const val REFRESH_MS = 3_000L

/**
 * Shows the status of recent downloads on the download server, refreshing while visible
 */
@Composable
fun YouTubeDownloadsPage(
    modifier: Modifier = Modifier,
    viewModel: YouTubeDownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(REFRESH_MS)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.youtube_downloads),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> {
                    CircularProgress(Modifier.align(Alignment.Center))
                }

                !state.configured -> {
                    Text(
                        text = stringResource(R.string.youtube_downloads_not_configured),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.jobs.isEmpty() -> {
                    Text(
                        text = state.error ?: stringResource(R.string.youtube_no_downloads),
                        color =
                            if (state.error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Rows are cards so they can take focus and the list scrolls with the d-pad
                        items(state.jobs, key = { it.id }) { job ->
                            DownloadRow(job)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(job: DownloadJob) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = job.title ?: job.url,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status =
                buildString {
                    append(job.status.replaceFirstChar { it.uppercase() })
                    if (job.status == "running" && job.progress != null) append(" · ${job.progress}")
                    if (job.playlist) append(" · playlist")
                    if (job.status == "failed" && job.error != null) append(" · ${job.error.take(120)}")
                }
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (job.status == "failed") {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
