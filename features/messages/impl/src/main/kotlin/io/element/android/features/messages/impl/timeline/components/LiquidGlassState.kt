/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.runtime.compositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * Carries the Liquid Glass theme down to the chat's bubbles and buttons.
 *
 * @property backgroundBackdrop only the chat background (what bubbles bend: a bubble cannot bend the
 * very message list it is part of).
 * @property timelineBackdrop the background and the message list together (what floating buttons bend).
 * Both are null when the Liquid Glass theme is off.
 */
data class LiquidGlassState(
    val backgroundBackdrop: Backdrop?,
    val timelineBackdrop: Backdrop?,
)

val LocalLiquidGlassState = compositionLocalOf { LiquidGlassState(backgroundBackdrop = null, timelineBackdrop = null) }
