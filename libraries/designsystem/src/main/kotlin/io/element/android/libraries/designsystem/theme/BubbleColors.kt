/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * User-picked message bubble background colors, or null to fall back to the app default for that
 * bubble. Read by [io.element.android.features.messages.impl.timeline.components.MessageEventBubble]
 * callers, which live several modules away from where the setting is read (`features/preferences`)
 * and provided (`features/messages`).
 */
data class BubbleColors(
    val outgoing: Color?,
    val incoming: Color?,
)

val LocalBubbleColors = compositionLocalOf { BubbleColors(outgoing = null, incoming = null) }
