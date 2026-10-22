package com.arcsus.arctv.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.arcsus.arctv.ArcTvApp
import com.arcsus.arctv.LivePlayerActivity
import com.arcsus.arctv.VideoPlayerActivity
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.data.SavedChannel
import com.arcsus.arctv.data.WatchEntry
import kotlinx.coroutines.launch

/**
 * Plays a live channel: in Arc's own player, with the rest of [channels] as
 * the zap list, or handed to an external app when the viewer prefers one.
 */
fun playLive(context: Context, channels: List<LiveChannel>, index: Int, player: String): Boolean {
    val channel = channels.getOrNull(index) ?: return false
    if (player == PLAYER_ARC) {
        context.startActivity(LivePlayerActivity.intent(context, channels, index))
        return true
    }
    return playVideo(context, channel.url, channel.name, player)
}

/** The play action every live screen shares, honouring the player setting. */
@Composable
fun rememberLivePlay(): (List<LiveChannel>, Int) -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext as ArcTvApp
    val player by app.settingsStore.livePlayer.collectAsState(initial = PLAYER_DEFAULT)
    val scope = rememberCoroutineScope()
    return { channels, index ->
        if (!playLive(context, channels, index, player)) {
            Toast.makeText(context, "No video player installed.", Toast.LENGTH_LONG).show()
        } else {
            channels.getOrNull(index)?.let { channel ->
                scope.launch { app.settingsStore.recordWatch(watchEntryOf(channel)) }
            }
        }
    }
}

/**
 * Starts a film or episode. [inApp] when it plays in Arc's own player, which
 * reports how playback ended; another app leaves the caller to guess.
 */
class VideoPlay(val inApp: Boolean, private val start: (String, String?) -> Boolean) {
    operator fun invoke(url: String, title: String?): Boolean = start(url, title)
}

/**
 * The play action for films and series, honouring Settings > Players.
 * [onPlayedToEnd] runs when Arc's own player closes at the end of the video.
 */
@Composable
fun rememberVideoPlay(onPlayedToEnd: () -> Unit = {}): VideoPlay {
    val context = LocalContext.current
    val app = context.applicationContext as ArcTvApp
    val player by app.settingsStore.moviePlayer.collectAsState(initial = PLAYER_ARC)
    val ended by rememberUpdatedState(onPlayedToEnd)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (VideoPlayerActivity.playedToEnd(result.resultCode, result.data)) ended()
    }
    return VideoPlay(inApp = player == PLAYER_ARC) { url, title ->
        if (player == PLAYER_ARC) {
            launcher.launch(VideoPlayerActivity.intent(context, url, title))
            true
        } else {
            playVideo(context, url, title, player)
        }
    }
}

/** The Continue Watching entry for a live channel. */
fun watchEntryOf(channel: LiveChannel) = WatchEntry(
    kind = "live",
    channel = SavedChannel(name = channel.name, url = channel.url, logo = channel.logo),
    group = channel.group,
)
