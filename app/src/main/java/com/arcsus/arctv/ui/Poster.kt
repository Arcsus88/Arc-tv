package com.arcsus.arctv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import com.arcsus.arctv.ArcTvApp

/** Resolves (and caches) a poster URL for a release filename; null while loading or when unknown. */
@Composable
fun rememberPosterUrl(filename: String): String? {
    val app = LocalContext.current.applicationContext as ArcTvApp
    val url by produceState<String?>(initialValue = null, filename) {
        value = app.artworkRepository.posterFor(filename)
    }
    return url
}

/** Poster image with a placeholder while loading / for unmatched titles. */
@Composable
fun PosterImage(posterUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp).align(Alignment.Center),
            )
        }
    }
}
