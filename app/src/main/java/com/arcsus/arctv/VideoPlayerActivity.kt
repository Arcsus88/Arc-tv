package com.arcsus.arctv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arcsus.arctv.ui.VideoPlayerScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme

/**
 * Full-screen films and series in Arc's own player (libVLC). Reports back
 * whether the video played to the end, so a series can offer the next
 * episode.
 */
class VideoPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        setContent {
            ArcTvTheme {
                VideoPlayerScreen(
                    url = url,
                    title = title,
                    onExit = { finish() },
                    onEnded = {
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_COMPLETED, true))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_COMPLETED = "completed"

        fun intent(context: Context, url: String, title: String?): Intent =
            Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title.orEmpty())
            }

        /** True when the player closed because the video reached its end. */
        fun playedToEnd(resultCode: Int, data: Intent?): Boolean =
            resultCode == Activity.RESULT_OK && data?.getBooleanExtra(EXTRA_COMPLETED, false) == true
    }
}
