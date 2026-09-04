package com.arcsus.arctv.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus once the requester is attached to something. Items inside a
 * lazy grid are composed during layout, a frame after the effect that wants
 * to focus them starts; asking too early throws. Returns false if nothing
 * attached within [frames] frames (the item never composed -- an empty grid).
 */
suspend fun FocusRequester.requestFocusWhenReady(frames: Int = 10): Boolean {
    repeat(frames) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
