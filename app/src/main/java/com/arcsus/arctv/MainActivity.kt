package com.arcsus.arctv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.AuthScreen
import com.arcsus.arctv.ui.AuthViewModel
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.LiveSetupScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme
import kotlinx.coroutines.launch

/** TV-safe margins (about 5% of a 1080p picture), see the note in onCreate. */
private val OVERSCAN_HORIZONTAL = 44.dp
private val OVERSCAN_VERTICAL = 24.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ArcTvApp
        setContent {
            ArcTvTheme {
                val factory = remember { ArcTvViewModelFactory(app) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    val authorized by produceState<Boolean?>(initialValue = null) {
                        app.tokenStore.isAuthorized.collect { value = it }
                    }
                    val liveSetupNeeded by produceState<Boolean?>(initialValue = null) {
                        app.settingsStore.liveSetupNeeded.collect { value = it }
                    }
                    val scope = rememberCoroutineScope()
                    // Overscan: most TVs crop the outer few percent of the
                    // picture, so anything flush with the edge is lost. Android
                    // TV's guidance is a 5% safe zone; this keeps every screen
                    // inside it while the background still paints edge to edge.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = OVERSCAN_HORIZONTAL,
                                vertical = OVERSCAN_VERTICAL,
                            ),
                    ) {
                        when {
                            authorized == null || liveSetupNeeded == null -> Unit // still reading DataStore
                            authorized == false -> {
                                val authViewModel: AuthViewModel = viewModel(factory = factory)
                                AuthScreen(authViewModel)
                            }
                            // Second step of first-run setup: Live TV playlists,
                            // sent from the phone rather than typed.
                            liveSetupNeeded == true -> LiveSetupScreen(factory) {
                                scope.launch { app.settingsStore.markLiveSetupDone() }
                            }
                            else -> HomeScreen(factory)
                        }
                    }
                }
            }
        }
    }
}
