package com.arcsus.arctv.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            AuthViewModel.State.Choose -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(48.dp),
                ) {
                    Text(
                        "Sign in",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Connect a debrid account to start streaming.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(32.dp))
                    Row {
                        Button(onClick = { viewModel.start(DebridProvider.REAL_DEBRID) }) {
                            Text("Real-Debrid")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { viewModel.start(DebridProvider.ALL_DEBRID) }) {
                            Text("AllDebrid")
                        }
                    }
                }
            }

            is AuthViewModel.State.Loading -> {
                Text("Contacting ${s.provider.label}…", style = MaterialTheme.typography.titleLarge)
            }

            is AuthViewModel.State.CodeReady -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(48.dp),
                ) {
                    Text(
                        "Sign in to ${s.provider.label}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "On your phone or computer, go to",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.verificationUrl,
                        style = MaterialTheme.typography.headlineSmall,
                        color = ArcBlue,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "and enter this code",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.userCode,
                        fontSize = 64.sp,
                        letterSpacing = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val alpha by pulse.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                        label = "alpha",
                    )
                    Text(
                        "Waiting for authorisation…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { this.alpha = alpha },
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.choose() }) {
                        Text("Back")
                    }
                }
            }

            is AuthViewModel.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(48.dp),
                ) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row {
                        Button(onClick = { viewModel.start(s.provider) }) {
                            Text("Try again")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { viewModel.choose() }) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}
