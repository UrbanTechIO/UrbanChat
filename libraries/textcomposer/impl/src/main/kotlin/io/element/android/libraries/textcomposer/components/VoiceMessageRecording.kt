/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.textcomposer.R
import io.element.android.libraries.ui.utils.time.formatShort
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun VoiceMessageRecording(
    levels: ImmutableList<Float>,
    duration: Duration,
    modifier: Modifier = Modifier,
    cancelDragOffsetPx: Float = 0f,
    cancelDragThresholdPx: Float = Float.MAX_VALUE,
) {
    // How far along the slide-to-cancel drag we are, 0f (not dragging) to 1f (about to cancel).
    val cancelProgress = (-cancelDragOffsetPx / cancelDragThresholdPx).coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Slide the whole bar left with the finger, capped at the threshold so it never
                // overshoots off-screen.
                translationX = cancelDragOffsetPx.coerceAtLeast(-cancelDragThresholdPx)
            }
            .background(
                color = ElementTheme.colors.bgSubtleSecondary,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
            .heightIn(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedRecordingDot()

        Spacer(Modifier.size(8.dp))

        // Timer
        Text(
            text = duration.formatShort(),
            color = ElementTheme.colors.textSecondary,
            style = ElementTheme.typography.fontBodySmMedium
        )

        Spacer(Modifier.size(20.dp))

        if (cancelProgress > 0f) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .alpha(0.4f + 0.6f * cancelProgress),
                text = stringResource(R.string.action_slide_to_cancel_voice_message),
                color = if (cancelProgress >= 1f) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                style = ElementTheme.typography.fontBodySmMedium,
            )
        } else {
            LiveWaveformView(
                modifier = Modifier
                    .height(26.dp)
                    .weight(1f),
                levels = levels,
            )
        }
    }
}

@Composable
private fun RedRecordingDot() {
    val infiniteTransition = rememberInfiniteTransition("RedRecordingDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = InfiniteRepeatableSpec(
            animation = TweenSpec(durationMillis = 1_000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "RedRecordingDotAlpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color = ElementTheme.colors.textCriticalPrimary, shape = CircleShape)
    )
}

@PreviewsDayNight
@Composable
internal fun VoiceMessageRecordingPreview() = ElementPreview {
    VoiceMessageRecording(List(100) { it.toFloat() / 100 }.toImmutableList(), 0.seconds)
}
