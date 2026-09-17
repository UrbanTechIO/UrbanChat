/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.api.local

import android.net.Uri
import io.element.android.libraries.matrix.api.media.MediaSource

/**
 * Looks up whether a piece of media the app already auto-saved to the device's public Gallery is
 * available locally, so the media viewer can load it straight from disk instead of the network.
 *
 * This is what lets a just-sent (or previously-received) photo/video still open while offline: the
 * Matrix SDK's own media cache is only ever populated by downloading, never by uploading, so
 * without this check, viewing a just-sent original after the app cleans up its own temporary
 * upload file has nothing left to load from and fails with a network error, even though the exact
 * same bytes already sit in the device's Gallery.
 */
fun interface LocalGalleryMediaResolver {
    /**
     * @return the local content [Uri] already saved for this media (checking disk fresh if not
     * already known in memory), or null if it isn't there.
     */
    suspend fun resolveLocalUri(
        mediaSource: MediaSource,
        mimeType: String,
        fileExtension: String,
        isSent: Boolean,
    ): Uri?
}
