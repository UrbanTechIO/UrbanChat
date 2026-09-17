/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.urlpreview

import androidx.compose.runtime.compositionLocalOf

/**
 * Carries the room's [UrlPreviewCoordinator] and the "Disable link previews" setting down to
 * [io.element.android.features.messages.impl.timeline.components.event.TimelineItemTextView],
 * which lives several composables away from where these are set up
 * ([io.element.android.features.messages.impl.MessagesView]) and doesn't otherwise receive
 * [io.element.android.features.messages.impl.MessagesState] directly.
 */
data class UrlPreviewContext(
    val coordinator: UrlPreviewCoordinator,
    val enabled: Boolean,
)

val LocalUrlPreviewContext = compositionLocalOf {
    UrlPreviewContext(
        coordinator = object : UrlPreviewCoordinator {
            override fun stateFor(url: String): UrlPreviewLoadState? = null
            override fun ensureFetched(url: String) = Unit
        },
        enabled = false,
    )
}
