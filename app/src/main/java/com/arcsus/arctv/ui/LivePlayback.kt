package com.arcsus.arctv.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.arcsus.arctv.ArcTvApp
import com.arcsus.arctv.LivePlayerActivity
import com.arcsus.arctv.data.LiveChannel

/**
 * Plays a live channel: in Arc's own player, with the rest of [channels] as
 * the zap list, or handed to an external app when the viewer prefers one.
 */
fun playLive(context: Context, channels: List<LiveChannel>, index: Int, inApp: Boolean): Boolean {
    val channel = channels.getOrNull(index) ?: return false
    if (inApp) {
        context.startActivity(LivePlayerActivity.intent(context, channels, index))
        return true
    }
    return playVideo(context, channel.url, channel.name)
}

/** The play action every live screen shares, honouring the player setting. */
@Composable
fun rememberLivePlay(): (List<LiveChannel>, Int) -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext as ArcTvApp
    val inApp by app.settingsStore.liveInAppPlayer.collectAsState(initial = true)
    return { channels, index ->
        if (!playLive(context, channels, index, inApp)) {
            Toast.makeText(context, "No video player installed.", Toast.LENGTH_LONG).show()
        }
    }
}
