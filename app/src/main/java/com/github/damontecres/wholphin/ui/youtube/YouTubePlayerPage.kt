package com.github.damontecres.wholphin.ui.youtube

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.youtube.YouTubeChoice
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import kotlinx.coroutines.delay

private const val SEEK_MS = 10_000L
private const val MAX_SEEK_MS = 120_000L
private const val OVERLAY_MS = 4_000L
private const val POSITION_UPDATE_MS = 500L

/**
 * Plays a YouTube video.
 *
 * D-pad: center toggles play/pause, left/right seeks (holding speeds up), up/down shows the controls
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

    val player: Player? = state.player
    // Tracks whether the user wants playback (not paused), so seeking/buffering does not flip the icon
    var isPlaying by remember(player) { mutableStateOf(player?.playWhenReady ?: false) }
    var buffering by remember(player) { mutableStateOf(false) }
    var cues by remember(player) { mutableStateOf<List<Cue>>(emptyList()) }
    var menu by remember { mutableStateOf(PlayerMenu.None) }
    // Take focus back from the options dialogs when they close
    LaunchedEffect(menu) { if (menu == PlayerMenu.None) focusRequester.requestFocus() }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    isPlaying = playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == Player.STATE_BUFFERING
                }

                override fun onCues(cueGroup: CueGroup) {
                    cues = cueGroup.cues
                }
            }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    var overlayToken by remember { mutableLongStateOf(0L) }
    var overlayVisible by remember { mutableStateOf(true) }
    LaunchedEffect(player, overlayVisible) {
        // Only poll while the overlay that displays the position is on screen
        while (player != null && overlayVisible) {
            positionMs = player.currentPosition
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(POSITION_UPDATE_MS)
        }
    }
    LaunchedEffect(overlayToken) {
        overlayVisible = true
        delay(OVERLAY_MS)
        overlayVisible = false
    }

    fun seekBy(deltaMs: Long) {
        player?.let {
            val target = (it.currentPosition + deltaMs).coerceAtLeast(0)
            val limited = if (durationMs > 0) target.coerceAtMost(durationMs) else target
            it.seekTo(limited)
            positionMs = limited
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || player == null) return@onKeyEvent false
                    val repeats = event.nativeKeyEvent.repeatCount
                    // Holding the key seeks further each step
                    val step = (SEEK_MS * (1 + repeats / 3)).coerceAtMost(MAX_SEEK_MS)
                    when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        -> {
                            if (repeats == 0) {
                                if (player.playWhenReady) player.pause() else player.play()
                            }
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.play()
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.pause()
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekBy(-step)
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekBy(step)
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_UP -> {
                            overlayToken++
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                            overlayToken++
                            menu = PlayerMenu.Main
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
        AndroidView(
            factory = { context ->
                SubtitleView(context).apply {
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                }
            },
            update = { it.setCues(cues) },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.loading || buffering) {
            CircularProgress(Modifier.align(Alignment.Center))
        }
        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // Keep the controls visible while paused
        if (overlayVisible || (player != null && !isPlaying && !buffering)) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(32.dp),
                )
            }
            PlayerControls(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                quality = state.height.takeIf { it > 0 }?.let { "${it}p" },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }

    when (menu) {
        PlayerMenu.None -> {}

        PlayerMenu.Main -> {
            DialogPopup(
                showDialog = true,
                title = "Playback options",
                dialogItems =
                    buildList {
                        add(
                            DialogItem(
                                headlineContent = { Text("Quality") },
                                supportingContent = { Text(if (state.height > 0) "${state.height}p" else "Auto") },
                                onClick = { menu = PlayerMenu.Quality },
                            ),
                        )
                        if (state.subtitles.isNotEmpty()) {
                            add(
                                DialogItem(
                                    headlineContent = { Text("Captions") },
                                    supportingContent = {
                                        Text(state.subtitles.firstOrNull { it.id == state.selectedSubtitleId }?.label ?: "Off")
                                    },
                                    onClick = { menu = PlayerMenu.Captions },
                                ),
                            )
                        }
                    },
                onDismissRequest = { menu = PlayerMenu.None },
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        PlayerMenu.Quality -> {
            ChoiceDialog(
                title = "Quality",
                choices = state.heights.map { YouTubeChoice(it, "${it}p") },
                selected = state.height,
                onSelect = viewModel::changeQuality,
                onDismiss = { menu = PlayerMenu.None },
            )
        }

        PlayerMenu.Captions -> {
            ChoiceDialog(
                title = "Captions",
                choices =
                    listOf(YouTubeChoice<String?>(null, "Off")) +
                        state.subtitles.map { YouTubeChoice<String?>(it.id, it.label) },
                selected = state.selectedSubtitleId,
                onSelect = viewModel::selectSubtitle,
                onDismiss = { menu = PlayerMenu.None },
            )
        }
    }
}

private enum class PlayerMenu { None, Main, Quality, Captions }

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    quality: String?,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isPlaying) "❚❚" else "▶",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "▼ Options" + quality?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
