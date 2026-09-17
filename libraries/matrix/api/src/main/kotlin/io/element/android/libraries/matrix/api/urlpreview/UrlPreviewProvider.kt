/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.urlpreview

/**
 * Fetches OpenGraph-style metadata for a URL via the current session's own homeserver — the
 * server does the actual fetching of the target page, so this never leaks the URL or the
 * device's IP to the linked site directly.
 */
interface UrlPreviewProvider {
    suspend fun getPreview(url: String): Result<UrlPreviewInfo>
}
