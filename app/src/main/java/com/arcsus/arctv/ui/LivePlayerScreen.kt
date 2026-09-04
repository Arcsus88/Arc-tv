package com.arcsus.arctv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.ui.theme.ArcBlue
import com.arcsus.arctv.ui.theme.ArcSurface
import kotlinx.coroutines.delay

/** Some panels only answer a player they recognise. */
private const val LIVE_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

/** How long the channel banner stays up once the picture is playing. */
private const val BANNER_MS = 4_000L

/** Time on a channel before it counts as watched (Continue Watching). */
private const val WATCHED_AFTER_MS = 30_000L

private enum class PlayStatus { TUNING, PLAYING, ENDED, FAILED }

/**
 * Arc's live player: the picture, a Sky-style channel banner that fades once
 * the stream is playing, and set-top-box keys -- up/down zap through the
 * group, OK brings the banner back, BACK leaves. Failures say what went
 * wrong and offer the external player as a way out.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(channels: List<LiveChannel>, startIndex: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    var index by rememberSaveable {
        mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0)))
    }
    val channel = channels.getOrNull(index)
    var status by remember { mutableStateOf(PlayStatus.TUNING) }
    var error by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(LIVE_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build()
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_READY -> PlayStatus.PLAYING
                    Player.STATE_BUFFERING -> PlayStatus.TUNING
                    Player.STATE_ENDED -> PlayStatus.ENDED
                    else -> status
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = describe(e)
                status = PlayStatus.FAILED
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Tune. Re-runs for a zap or a retry.
    LaunchedEffect(index, attempt) {
        val c = channels.getOrNull(index) ?: return@LaunchedEffect
        error = null
        status = PlayStatus.TUNING
        banner = true
        player.setMediaItem(MediaItem.Builder().setUri(c.url).setMediaId(c.url).build())
        player.prepare()
        player.playWhenReady = true
    }

    // A live stream left paused in the background is stale on return: stop
    // it, and tune afresh when the screen comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    player.stop()
                    stopped = true
                }
                Lifecycle.Event.ON_START -> if (stopped) {
                    stopped = false
                    attempt += 1
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Zapping to a channel and staying there is watching it.
    LaunchedEffect(index) {
        val c = channels.getOrNull(index) ?: return@LaunchedEffect
        delay(WATCHED_AFTER_MS)
        (context.applicationContext as? com.arcsus.arctv.ArcTvApp)?.settingsStore?.recordWatch(watchEntryOf(c))
    }

    LaunchedEffect(banner, status, index) {
        if (banner && status == PlayStatus.PLAYING) {
            delay(BANNER_MS)
            banner = false
        }
    }

    val keys = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    BackHandler { onExit() }

    fun zap(step: Int) {
        if (channels.size > 1) index = (index + step + channels.size) % channels.size
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(+1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(-1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.Info -> {
                        banner = !banner
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(keys)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = channel != null && (banner || status != PlayStatus.PLAYING) && status != PlayStatus.FAILED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (channel != null) {
                ChannelBanner(
                    channel = channel,
                    position = "${index + 1} of ${channels.size}",
                    status = when (status) {
                        PlayStatus.TUNING -> "Tuning…"
                        PlayStatus.PLAYING -> "Live"
                        PlayStatus.ENDED -> "Stream ended"
                        PlayStatus.FAILED -> ""
                    },
                )
            }
        }

        if (status == PlayStatus.FAILED && channel != null) {
            FailurePanel(
                channel = channel,
                message = error.orEmpty(),
                retryFocus = retryFocus,
                onRetry = { attempt += 1 },
                onExternal = {
                    if (!playVideo(context, channel.url, channel.name)) {
                        android.widget.Toast.makeText(
                            context, "No video player installed.", android.widget.Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        onExit()
                    }
                },
                onBack = onExit,
            )
        }
    }

    LaunchedEffect(status) {
        if (status == PlayStatus.FAILED) retryFocus.requestFocusWhenReady() else keys.requestFocusWhenReady()
    }
}

@Composable
private fun ChannelBanner(channel: LiveChannel, position: String, status: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 34.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (channel.logo.isNotBlank()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                ) {
                    AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.isNotBlank()) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (status == "Live") ArcBlue else Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        listOf(channel.group.ifBlank { "Live TV" }, position).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            Text(
                "▲▼ Channel     OK Info     BACK Exit",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun FailurePanel(
    channel: LiveChannel,
    message: String,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
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
                "Couldn't play ${channel.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
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
                Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) { Text("Try again") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onExternal) { Text("Open in external player") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Up and down on the remote try another channel.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A sentence for the viewer, plus the code for a bug report. */
private fun describe(e: PlaybackException): String {
    val plain = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The panel refused the stream. Many panels allow one connection at a time -- " +
                "close it on other devices and try again."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Couldn't reach the stream server. Check the connection (and the VPN, if one is on)."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The stream isn't there any more."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "The stream isn't in a format Arc can play. The external player may manage it."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
            "This TV can't decode the stream. The external player may manage it."
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "The stream fell behind; trying again usually fixes it."
        else -> "Playback stopped unexpectedly."
    }
    return "$plain  (${e.errorCodeName})"
}
