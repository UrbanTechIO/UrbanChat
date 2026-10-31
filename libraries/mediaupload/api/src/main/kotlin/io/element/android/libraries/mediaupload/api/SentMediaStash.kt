/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.api

import java.io.File

/**
 * Keeps a copy of a media file we send until the timeline can save it into the Sent folder of the Gallery, so our own
 * videos play from the device instead of being downloaded back from the server.
 */
interface SentMediaStash {
    /** Copies [file] aside, keyed by its name and size. Never throws. */
    suspend fun stash(file: File)

    /** The stashed copy for a sent event with this [filename] and [size], or null. The caller owns (and deletes) the returned file. */
    fun take(filename: String, size: Long?): File?
}
