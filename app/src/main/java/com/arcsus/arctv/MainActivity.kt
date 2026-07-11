package com.arcsus.arctv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.arcsus.arctv.ui.ArcTvViewModelFactory
import com.arcsus.arctv.ui.AuthScreen
import com.arcsus.arctv.ui.AuthViewModel
import com.arcsus.arctv.ui.HomeScreen
import com.arcsus.arctv.ui.theme.ArcTvTheme

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
                    when (authorized) {
                        null -> Unit // still reading DataStore
                        false -> {
                            val authViewModel: AuthViewModel = viewModel(factory = factory)
                            AuthScreen(authViewModel)
                        }
                        true -> HomeScreen(factory)
                    }
                }
            }
        }
    }
}
