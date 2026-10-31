/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.mediaupload.api.SentMediaStash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

@ContributesBinding(SessionScope::class)
class DefaultSentMediaStash(
    @CacheDirectory private val cacheDirectory: File,
) : SentMediaStash {
    private val directory get() = File(cacheDirectory, "sent_media").also { it.mkdirs() }

    override suspend fun stash(file: File) {
        withContext(Dispatchers.IO) {
            runCatchingExceptions {
                val dir = directory
                // Leftovers of videos that were never opened or never matched.
                val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
                dir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
                file.copyTo(File(dir, "${file.length()}_${file.name}"), overwrite = true)
            }.onFailure { Timber.w(it, "Could not stash the sent media") }
        }
    }

    override fun take(filename: String, size: Long?): File? {
        val files = directory.listFiles().orEmpty()
        val exact = size?.let { s -> files.firstOrNull { it.name == "${s}_$filename" } }
        val found = exact ?: files.filter { it.name.endsWith("_$filename") }.maxByOrNull { it.lastModified() }
        Timber.d("Sent media stash lookup: size=$size found=${found != null} (${files.size} stashed)")
        return found
    }
}
