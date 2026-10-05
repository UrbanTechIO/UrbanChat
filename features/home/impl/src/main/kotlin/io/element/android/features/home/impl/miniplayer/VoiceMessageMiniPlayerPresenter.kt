/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.miniplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.mediaplayer.api.MediaPlayer

/**
 * Drives the home screen's persistent mini-player for a playing/paused voice message, so playback
 * started from a chat's timeline stays visible (and controllable) after navigating away from it —
 * the underlying [MediaPlayer] is session-scoped and keeps playing regardless.
 */
@Inject
class VoiceMessageMiniPlayerPresenter(
    private val mediaPlayer: MediaPlayer,
) : Presenter<VoiceMessageMiniPlayerState> {
    @Composable
    override fun present(): VoiceMessageMiniPlayerState {
        val playerState by mediaPlayer.state.collectAsState()

        fun handleEvent(event: VoiceMessageMiniPlayerEvents) {
            when (event) {
                VoiceMessageMiniPlayerEvents.PlayPause -> {
                    if (playerState.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                }
                VoiceMessageMiniPlayerEvents.Close -> mediaPlayer.stop()
            }
        }

        return VoiceMessageMiniPlayerState(
            mediaId = playerState.mediaId,
            isPlaying = playerState.isPlaying,
            currentPositionMs = playerState.currentPosition,
            durationMs = playerState.duration,
            eventSink = ::handleEvent,
        )
    }
}
