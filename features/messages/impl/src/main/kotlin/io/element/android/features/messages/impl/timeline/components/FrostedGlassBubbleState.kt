/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * Carries the Frosted theme setting, and a [HazeState] scoped to *only* the chat background layer
 * (not the message list), down to [MessageEventBubble], which lives several composables away from
 * where these are set up ([io.element.android.features.messages.impl.MessagesView]). Bubbles blur
 * that background layer specifically: a hazeEffect nested inside its own hazeSource renders
 * nothing, so bubbles can't blur the very message list they're themselves a part of, but the chat
 * background is a genuinely separate layer they can safely blur.
 */
data class FrostedGlassBubbleState(
    val hazeState: HazeState?,
    val enabled: Boolean,
    val opacity: Float,
)

val LocalFrostedGlassBubbleState = compositionLocalOf { FrostedGlassBubbleState(hazeState = null, enabled = false, opacity = 1f) }
