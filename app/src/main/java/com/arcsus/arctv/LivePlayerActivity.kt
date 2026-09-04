package com.arcsus.arctv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arcsus.arctv.data.LiveChannel
import com.arcsus.arctv.ui.LivePlayerScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme

/**
 * Full-screen live TV in Arc's own player. Carries the channel's group so
 * up/down on the remote zap through it, the way a set-top box does.
 */
class LivePlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val names = intent.getStringArrayExtra(EXTRA_NAMES).orEmpty()
        val urls = intent.getStringArrayExtra(EXTRA_URLS).orEmpty()
        val logos = intent.getStringArrayExtra(EXTRA_LOGOS).orEmpty()
        val group = intent.getStringExtra(EXTRA_GROUP).orEmpty()
        val start = intent.getIntExtra(EXTRA_INDEX, 0)
        val channels = urls.indices.map { i ->
            LiveChannel(
                id = "$i:${urls[i]}",
                name = names.getOrElse(i) { "" },
                logo = logos.getOrElse(i) { "" },
                group = group,
                url = urls[i],
            )
        }
        if (channels.isEmpty()) {
            finish()
            return
        }
        setContent {
            ArcTvTheme {
                LivePlayerScreen(channels = channels, startIndex = start, onExit = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_NAMES = "names"
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_LOGOS = "logos"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_INDEX = "index"

        /** Intents have a size limit; a huge group is trimmed around the chosen channel. */
        private const val MAX_ZAP_LIST = 400

        fun intent(context: Context, channels: List<LiveChannel>, index: Int): Intent {
            val from = (index - MAX_ZAP_LIST / 2).coerceAtLeast(0)
            val to = (from + MAX_ZAP_LIST).coerceAtMost(channels.size)
            val window = channels.subList(from, to)
            return Intent(context, LivePlayerActivity::class.java).apply {
                putExtra(EXTRA_NAMES, window.map { it.name }.toTypedArray())
                putExtra(EXTRA_URLS, window.map { it.url }.toTypedArray())
                putExtra(EXTRA_LOGOS, window.map { it.logo }.toTypedArray())
                putExtra(EXTRA_GROUP, channels.getOrNull(index)?.group.orEmpty())
                putExtra(EXTRA_INDEX, (index - from).coerceIn(0, (window.size - 1).coerceAtLeast(0)))
            }
        }
    }
}
