package com.github.damontecres.wholphin.ui.youtube

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.CircularProgress
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L
private const val OVERLAY_MS = 4_000L

/**
 * Plays a YouTube video. D-pad: center toggles play/pause, left/right seeks 10 seconds.
 */
@Composable
fun YouTubePlayerPage(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    viewModel: YouTubePlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(url) { viewModel.load(url) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var overlayToken by remember { mutableLongStateOf(0L) }
    var overlayVisible by remember { mutableStateOf(true) }
    LaunchedEffect(overlayToken) {
        overlayVisible = true
        delay(OVERLAY_MS)
        overlayVisible = false
    }

    val player: Player? = state.player
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || player == null) return@onKeyEvent false
                    when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (player.isPlaying) player.pause() else player.play()
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0))
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            player.seekTo(player.currentPosition + SEEK_MS)
                            overlayToken++
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        player?.let {
            PlayerSurface(
                player = it,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
        }
        if (overlayVisible) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(32.dp),
            )
        }
    }
}
