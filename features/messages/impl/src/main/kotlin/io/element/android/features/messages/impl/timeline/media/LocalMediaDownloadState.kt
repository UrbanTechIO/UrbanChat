/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.media

import androidx.compose.runtime.compositionLocalOf
import android.net.Uri
import io.element.android.libraries.matrix.api.media.MediaSource

/**
 * Carries the room's [MediaDownloadCoordinator] and the "Enable Download to Gallery" setting down
 * to [io.element.android.features.messages.impl.timeline.components.event.TimelineItemVideoView],
 * which lives several composables away from where these are set up
 * ([io.element.android.features.messages.impl.MessagesView]) and doesn't otherwise receive
 * [io.element.android.features.messages.impl.MessagesState] or
 * [io.element.android.features.messages.impl.timeline.TimelineState] directly.
 */
data class MediaDownloadContext(
    val coordinator: MediaDownloadCoordinator,
    val downloadToGalleryEnabled: Boolean,
)

val LocalMediaDownloadContext = compositionLocalOf {
    MediaDownloadContext(
        coordinator = object : MediaDownloadCoordinator {
            override fun stateFor(mediaSource: MediaSource) = MediaDownloadState.NotDownloaded
            override fun bytesDownloadedFor(mediaSource: MediaSource): Long? = null
            override fun localUriFor(mediaSource: MediaSource): Uri? = null
            override fun verifyLocalFileStillExists(mediaSource: MediaSource): Boolean = false
            override fun ensureDownloaded(
                mediaSource: MediaSource,
                mimeType: String,
                filename: String,
                fileExtension: String,
                isSent: Boolean,
            ) = Unit
        },
        downloadToGalleryEnabled = false,
    )
}
