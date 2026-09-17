/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.viewer

import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeAnimatedImage
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeImage

/**
 * Whether the media is a still image which the image editor can load and re-encode.
 * Mirrors the restriction of the attachments image editor (no animated images, no SVG).
 */
internal fun String?.isEditableImageMimeType(): Boolean {
    return this != null &&
        isMimeTypeImage() &&
        !isMimeTypeAnimatedImage() &&
        this != MimeTypes.Svg
}
