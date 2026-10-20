/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.media

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf

/**
 * Live "bytes received so far" for media downloads that report their progress (see
 * [MatrixMediaLoader.downloadMediaFile], `trackProgress`). Backed by Compose snapshot state, so a composable that
 * reads [get] is recomposed whenever the number changes. Keyed by [MediaSource.safeUrl].
 */
object MediaDownloadProgress {
    @Immutable
    data class Progress(
        val downloadedBytes: Long,
        /** Null when the server did not say how big the file is. */
        val totalBytes: Long?,
    ) {
        /** 0..1, or null when the total size is unknown. */
        val fraction: Float? = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    private val progressByKey = mutableStateMapOf<String, Progress>()

    fun get(key: String): Progress? = progressByKey[key]

    fun get(source: MediaSource): Progress? = get(source.safeUrl)

    fun update(key: String, downloadedBytes: Long, totalBytes: Long?) {
        progressByKey[key] = Progress(downloadedBytes, totalBytes)
    }

    fun clear(key: String) {
        progressByKey.remove(key)
    }
}
