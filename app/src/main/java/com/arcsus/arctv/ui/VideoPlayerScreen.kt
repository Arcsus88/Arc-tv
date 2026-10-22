package com.arcsus.arctv.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue
import com.arcsus.arctv.ui.theme.ArcSurface
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * libVLC's options. Debrid links are large files over plain HTTP: reconnect
 * when the server drops the connection, and keep a few seconds buffered so a
 * remux's bitrate peaks don't stall the picture.
 */
private val VLC_OPTIONS = listOf("--http-reconnect", "--network-caching=3000")

/** How long the controls stay up after the last key press while playing. */
private const val CONTROLS_MS = 5_000L

/** LEFT/RIGHT skip; holding the key down skips further per repeat. */
private const val SKIP_MS = 10_000L
private const val SKIP_HELD_MS = 30_000L

/** Presses this close together add up to one seek: one new request to the server, not five. */
private const val SEEK_SETTLE_MS = 450L

/** A stream that stops further than this from the end was cut off, not finished. */
private const val END_SLACK_MS = 90_000L

private enum class VideoStatus { OPENING, PLAYING, PAUSED, ENDED, FAILED }

private enum class TrackKind { AUDIO, SUBTITLES }

/**
 * Arc's player for films and series: VLC's own engine (libVLC) inside the
 * app, so anything VLC plays -- MKV, HEVC, DTS and TrueHD sound, embedded
 * subtitles -- plays here without leaving Arc TV. The remote works it the way
 * a TV player should: OK pauses, left/right skip, and the controls offer the
 * audio and subtitle tracks. [onEnded] runs when the video plays to the end.
 */
@Composable
fun VideoPlayerScreen(url: String, title: String, onExit: () -> Unit, onEnded: () -> Unit) {
    val context = LocalContext.current
    // Loads the native engine. libVLC quits the whole app (System.exit) when
    // its libraries won't load, so load them here first: a TV that can't run
    // them gets a way out instead of Arc vanishing.
    val libVlc = remember {
        runCatching {
            listOf("c++_shared", "vlc", "vlcjni").forEach { System.loadLibrary(it) }
            LibVLC(context, VLC_OPTIONS)
        }.getOrNull()
    }
    if (libVlc == null) {
        val focus = remember { FocusRequester() }
        BackHandler { onExit() }
        VideoFailurePanel(
            title = title,
            message = "Arc TV's player couldn't start on this TV. Another player app can still play it.",
            focus = focus,
            onRetry = null,
            onExternal = { if (playVideoWith(context, url, title)) onExit() },
            onBack = onExit,
        )
        LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
        return
    }
    VlcPlayback(libVlc, url, title, onExit, onEnded)
}

@Composable
private fun VlcPlayback(libVlc: LibVLC, url: String, title: String, onExit: () -> Unit, onEnded: () -> Unit) {
    val context = LocalContext.current
    val player = remember { MediaPlayer(libVlc) }
    var layout by remember { mutableStateOf<VLCVideoLayout?>(null) }

    var status by remember { mutableStateOf(VideoStatus.OPENING) }
    var buffering by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var cutOff by remember { mutableStateOf<String?>(null) }
    // Bumped whenever libVLC finds, drops or switches a track, so the
    // audio/subtitle lists are read afresh.
    var tracksVersion by remember { mutableIntStateOf(0) }
    // Each bump (re)opens the stream, from resumeAt: a retry, or coming back
    // from the TV's home screen.
    var attempt by remember { mutableIntStateOf(0) }
    var resumeAt by remember { mutableLongStateOf(0L) }

    var controls by remember { mutableStateOf(true) }
    // Bumped on every key press; restarts the controls' hide timer.
    var nudge by remember { mutableIntStateOf(0) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var picker by remember { mutableStateOf<TrackKind?>(null) }

    val finished by rememberUpdatedState(onEnded)
    DisposableEffect(player) {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> status = VideoStatus.OPENING
                MediaPlayer.Event.Buffering -> buffering = event.buffering
                MediaPlayer.Event.Playing -> status = VideoStatus.PLAYING
                MediaPlayer.Event.Paused -> status = VideoStatus.PAUSED
                MediaPlayer.Event.TimeChanged -> {
                    time = event.timeChanged
                    // The clock only moves while frames are flowing.
                    buffering = 100f
                }
                MediaPlayer.Event.LengthChanged -> length = event.lengthChanged
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected ->
                    tracksVersion += 1
                MediaPlayer.Event.EndReached ->
                    if (length > 0 && time < length - END_SLACK_MS) {
                        // The server hung up mid-film: offer to carry on from here.
                        resumeAt = time
                        cutOff = "The stream stopped at ${clock(time)} of ${clock(length)}. " +
                            "Try again to carry on from there."
                        status = VideoStatus.FAILED
                    } else {
                        status = VideoStatus.ENDED
                        finished()
                    }
                MediaPlayer.Event.EncounteredError -> {
                    resumeAt = time
                    status = VideoStatus.FAILED
                }
            }
        }
        onDispose {
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }

    // Open the stream once the picture has somewhere to go; again for a retry.
    LaunchedEffect(layout, attempt) {
        if (layout == null) return@LaunchedEffect
        status = VideoStatus.OPENING
        buffering = 0f
        cutOff = null
        val media = Media(libVlc, Uri.parse(url))
        if (resumeAt > 0) media.addOption(":start-time=${resumeAt / 1000}")
        player.setMedia(media)
        media.release()
        player.play()
    }

    // The TV's home screen (or another app) takes the screen and its surface
    // with it: let go of the stream, and pick up where it was on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, layout) {
        val view = layout
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (view != null && status != VideoStatus.FAILED && status != VideoStatus.ENDED) {
                    // Still opening (a retry) reads 0: keep the old spot then.
                    resumeAt = player.time.takeIf { it > 0 } ?: resumeAt
                    player.stop()
                    player.detachViews()
                    stopped = true
                }
                Lifecycle.Event.ON_START -> if (stopped && view != null) {
                    stopped = false
                    player.attachViews(view, null, true, false)
                    controls = true
                    attempt += 1
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Key presses close together become one seek.
    LaunchedEffect(pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        delay(SEEK_SETTLE_MS)
        player.setTime(target)
        time = target
        pendingSeek = null
    }

    LaunchedEffect(controls, status, nudge, picker) {
        if (controls && status == VideoStatus.PLAYING && picker == null) {
            delay(CONTROLS_MS)
            controls = false
        }
    }

    fun skip(delta: Long) {
        controls = true
        if (!player.isSeekable) return
        val last = if (length > 0) length - 1_000 else Long.MAX_VALUE
        pendingSeek = ((pendingSeek ?: time) + delta).coerceIn(0L, last.coerceAtLeast(0L))
    }

    fun togglePause() {
        controls = true
        when (status) {
            VideoStatus.PLAYING -> player.pause()
            VideoStatus.PAUSED -> player.play()
            else -> Unit
        }
    }

    fun openElsewhere() {
        if (playVideoWith(context, url, title)) onExit()
    }

    val failed = status == VideoStatus.FAILED
    val keys = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    BackHandler {
        if (controls && (status == VideoStatus.PLAYING || status == VideoStatus.PAUSED)) controls = false else onExit()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) nudge += 1
                false
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || failed) return@onKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> { if (!event.isRepeat()) togglePause(); true }
                    Key.MediaPlay -> { if (status == VideoStatus.PAUSED) togglePause(); true }
                    Key.MediaPause -> { if (status == VideoStatus.PLAYING) togglePause(); true }
                    Key.MediaFastForward -> { skip(SKIP_HELD_MS); true }
                    Key.MediaRewind -> { skip(-SKIP_MS); true }
                    // With the controls up, the arrows move between them.
                    else -> !controls && when (event.key) {
                        Key.DirectionLeft -> { skip(-skipFor(event)); true }
                        Key.DirectionRight -> { skip(skipFor(event)); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!event.isRepeat()) togglePause(); true }
                        Key.DirectionUp, Key.DirectionDown, Key.Menu, Key.Info -> { controls = true; true }
                        else -> false
                    }
                }
            }
            .focusRequester(keys)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also {
                    it.keepScreenOn = true
                    player.attachViews(it, null, true, false)
                    layout = it
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val loading = status == VideoStatus.OPENING || (status == VideoStatus.PLAYING && buffering < 100f)
        if (loading && !failed) {
            Text(
                if (buffering in 1f..99f) "Loading… ${buffering.toInt()}%" else "Loading…",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = controls && !failed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val audio = remember(tracksVersion) { player.audioTracks.orEmpty().filter { it.id != -1 } }
            val subtitles = remember(tracksVersion) { player.spuTracks.orEmpty().filter { it.id != -1 } }
            PlayerControls(
                title = title,
                position = pendingSeek ?: time,
                length = length,
                playing = status != VideoStatus.PAUSED,
                seekFocus = seekFocus,
                showAudio = audio.size > 1,
                showSubtitles = subtitles.isNotEmpty(),
                onSkip = { skip(it) },
                onTogglePause = { togglePause() },
                onAudio = { picker = TrackKind.AUDIO },
                onSubtitles = { picker = TrackKind.SUBTITLES },
                onExternal = { openElsewhere() },
            )
        }

        if (failed) {
            VideoFailurePanel(
                title = title,
                message = cutOff ?: rememberLinkCheck(url, attempt),
                focus = retryFocus,
                onRetry = { attempt += 1 },
                onExternal = { openElsewhere() },
                onBack = onExit,
            )
        }
    }

    picker?.let { kind ->
        val tracks = remember(kind, tracksVersion) {
            when (kind) {
                TrackKind.AUDIO -> player.audioTracks.orEmpty().toList()
                TrackKind.SUBTITLES -> player.spuTracks.orEmpty().toList()
            }
        }
        val current = when (kind) {
            TrackKind.AUDIO -> player.audioTrack
            TrackKind.SUBTITLES -> player.spuTrack
        }
        TrackPicker(
            heading = if (kind == TrackKind.AUDIO) "Audio" else "Subtitles",
            tracks = tracks,
            current = current,
            onPick = { id ->
                when (kind) {
                    TrackKind.AUDIO -> player.setAudioTrack(id)
                    TrackKind.SUBTITLES -> player.setSpuTrack(id)
                }
                tracksVersion += 1
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    LaunchedEffect(controls, failed) {
        when {
            failed -> retryFocus.requestFocusWhenReady()
            controls -> seekFocus.requestFocusWhenReady()
            else -> keys.requestFocusWhenReady()
        }
    }
}

/** The key is being held down, not freshly pressed. */
private fun KeyEvent.isRepeat(): Boolean = nativeKeyEvent.repeatCount > 0

/** A held key skips further with each repeat. */
private fun skipFor(event: KeyEvent): Long = if (event.isRepeat()) SKIP_HELD_MS else SKIP_MS

/**
 * libVLC says only that playback failed; asking the server for the first
 * bytes tells a dead link from a file the TV can't decode.
 */
@Composable
private fun rememberLinkCheck(url: String, attempt: Int): String {
    var result by remember(attempt) { mutableStateOf("Checking the link…") }
    LaunchedEffect(attempt) {
        result = withContext(Dispatchers.IO) { probeStream(url) }
    }
    return result
}

@Composable
private fun PlayerControls(
    title: String,
    position: Long,
    length: Long,
    playing: Boolean,
    seekFocus: FocusRequester,
    showAudio: Boolean,
    showSubtitles: Boolean,
    onSkip: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onExternal: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
            .padding(start = 48.dp, end = 48.dp, top = 72.dp, bottom = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(24.dp))
            Text(
                "◀▶ Skip     OK Pause     BACK Exit",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(clock(position), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.width(16.dp))
            SeekBar(
                position = position,
                length = length,
                focus = seekFocus,
                onSkip = onSkip,
                onTogglePause = onTogglePause,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                if (length > 0) clock(length) else "--:--",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArcChip(label = if (playing) "❚❚  Pause" else "▶  Play", selected = false, onClick = onTogglePause)
            if (showAudio) ArcChip(label = "Audio", selected = false, onClick = onAudio)
            if (showSubtitles) ArcChip(label = "Subtitles", selected = false, onClick = onSubtitles)
            ArcChip(label = "Open with…", selected = false, onClick = onExternal)
        }
    }
}

/** The progress bar. Focused, LEFT/RIGHT skip through the film and OK pauses. */
@Composable
private fun SeekBar(
    position: Long,
    length: Long,
    focus: FocusRequester,
    onSkip: (Long) -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f
    val bar = if (focused) 6.dp else 4.dp
    BoxWithConstraints(
        modifier
            .height(22.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSkip(-skipFor(event)); true }
                    Key.DirectionRight -> { onSkip(skipFor(event)); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!event.isRepeat()) onTogglePause(); true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(bar)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(bar)
                .background(if (focused) Color.White else ArcBlue, RoundedCornerShape(3.dp)),
        )
        if (focused) {
            Box(
                Modifier
                    .offset(x = maxWidth * fraction - 8.dp)
                    .size(16.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun TrackPicker(
    heading: String,
    tracks: List<MediaPlayer.TrackDescription>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .background(ArcSurface, RoundedCornerShape(16.dp))
                .padding(26.dp),
        ) {
            Text(
                heading,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(tracks) { track ->
                    val selected = track.id == current
                    ArcChip(
                        label = (if (selected) "✓  " else "") + track.name.ifBlank { "Track ${track.id}" },
                        selected = selected,
                        onClick = { onPick(track.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { selectedFocus.requestFocusWhenReady() }
}

@Composable
private fun VideoFailurePanel(
    title: String,
    message: String,
    focus: FocusRequester,
    onRetry: (() -> Unit)?,
    onExternal: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.56f)
                .background(ArcSurface, RoundedCornerShape(14.dp))
                .padding(26.dp),
        ) {
            Text(
                "Couldn't play ${title.ifBlank { "this" }}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRetry != null) {
                    Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text("Try again") }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = onExternal) { Text("Open with…") }
                } else {
                    Button(onClick = onExternal, modifier = Modifier.focusRequester(focus)) { Text("Open with…") }
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}

/** 1:02:03, or 4:05 under an hour. */
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
