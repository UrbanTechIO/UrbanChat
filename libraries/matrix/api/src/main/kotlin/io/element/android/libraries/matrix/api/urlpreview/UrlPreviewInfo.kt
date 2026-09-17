/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.urlpreview

/**
 * OpenGraph-derived metadata for a URL, as returned by the homeserver's own preview_url endpoint
 * (the server fetches the page, never the client — see [UrlPreviewProvider]).
 */
data class UrlPreviewInfo(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    /** An `mxc://` URI, if the server captured and re-hosted a thumbnail image for this URL. */
    val imageMxcUri: String?,
    val imageWidth: Long?,
    val imageHeight: Long?,
)
