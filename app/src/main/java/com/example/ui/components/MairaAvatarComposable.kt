package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.views.MairaAvatarView

/**
 * MairaAvatarComposable: Jetpack Compose wrapper for MairaAvatarView.
 * Provides the hardware-accelerated animated female AI avatar with
 * real-time lip-sync, blinking eyes, and dynamic state auras.
 */
@Composable
fun MairaAvatarComposable(
    status: String,
    micRms: Float,
    speakerRms: Float,
    sessionState: String = "সংযুক্ত",
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            MairaAvatarView(context).apply {
                setSpeakerRms(speakerRms)
                setMicRms(micRms)
                setSessionState(sessionState)
                setStatusMessage(status)
            }
        },
        update = { view ->
            view.setSpeakerRms(speakerRms)
            view.setMicRms(micRms)
            view.setSessionState(sessionState)
            view.setStatusMessage(status)
        },
        modifier = modifier
    )
}
