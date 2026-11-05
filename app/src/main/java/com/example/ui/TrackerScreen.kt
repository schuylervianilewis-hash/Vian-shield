package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Intentionally blank Tracker tab.
 *
 * Tracker presentation and runtime functionality are deferred; this screen must not
 * display fabricated tracker data or imply that tracking detection is active.
 */
@Composable
fun TrackerScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tracker_empty_screen")
    )
}
