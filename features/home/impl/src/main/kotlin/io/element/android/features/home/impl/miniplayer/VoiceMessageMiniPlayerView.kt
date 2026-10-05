/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.miniplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay

/** Persistent mini-player shown on the home screen while a voice message is loaded (playing or paused). */
@Composable
fun VoiceMessageMiniPlayerView(
    state: VoiceMessageMiniPlayerState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = state.mediaId != null, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgSubtleSecondary)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = { state.eventSink(VoiceMessageMiniPlayerEvents.PlayPause) }) {
                Icon(
                    imageVector = if (state.isPlaying) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                    contentDescription = stringResource(
                        if (state.isPlaying) CommonStrings.a11y_pause else CommonStrings.a11y_play
                    ),
                    tint = ElementTheme.colors.iconPrimary,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape),
            ) {
                // Ticks locally between state updates so the bar moves smoothly instead of jumping
                // once every ~100ms update from the player.
                val liveProgress by if (state.isPlaying) {
                    produceState(initialValue = state.progress, state.mediaId, state.currentPositionMs, state.durationMs) {
                        val duration = state.durationMs
                        val start = state.currentPositionMs
                        val startedAt = System.currentTimeMillis()
                        if (duration == null || duration <= 0) return@produceState
                        while (true) {
                            val elapsed = System.currentTimeMillis() - startedAt
                            value = ((start + elapsed).toFloat() / duration).coerceIn(0f, 1f)
                            delay(50)
                        }
                    }
                } else {
                    produceState(initialValue = state.progress) { value = state.progress }
                }
                LinearProgressIndicator(
                    progress = { liveProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = ElementTheme.colors.iconAccentPrimary,
                    trackColor = ElementTheme.colors.bgSubtlePrimary,
                )
            }
            Text(
                text = stringResource(R.string.screen_roomlist_voice_message_mini_player_label),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = { state.eventSink(VoiceMessageMiniPlayerEvents.Close) },
            ) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(CommonStrings.action_close),
                    tint = ElementTheme.colors.iconSecondary,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun VoiceMessageMiniPlayerViewPlayingPreview() = ElementPreview {
    VoiceMessageMiniPlayerView(
        state = VoiceMessageMiniPlayerState(
            mediaId = "\$anEventId",
            isPlaying = true,
            currentPositionMs = 4_000L,
            durationMs = 12_000L,
            eventSink = {},
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun VoiceMessageMiniPlayerViewPausedPreview() = ElementPreview {
    VoiceMessageMiniPlayerView(
        state = VoiceMessageMiniPlayerState(
            mediaId = "\$anEventId",
            isPlaying = false,
            currentPositionMs = 4_000L,
            durationMs = 12_000L,
            eventSink = {},
        ),
    )
}
