/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeImage
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeVideo
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.mediaviewer.api.local.LocalGalleryMediaResolver
import java.io.File
import java.io.FileOutputStream

internal const val APP_MEDIA_FOLDER_NAME = "UrbanChat"

/**
 * The device-public Gallery folder scheme that [DefaultMediaDownloadCoordinator] automatically
 * saves every sent/received image and video into (see that class's own doc). Extracted into its
 * own class so [LocalGalleryMediaResolver] — used by the media viewer, a lower-level module that
 * can't depend on the RoomScope-scoped, Compose-state-heavy coordinator — can share this exact
 * folder/filename scheme to look up an already-saved file, without duplicating it.
 */
@ContributesBinding(RoomScope::class)
class GalleryMediaLookup(
    @ApplicationContext private val context: Context,
    private val buildMeta: BuildMeta,
) : LocalGalleryMediaResolver {
    override suspend fun resolveLocalUri(
        mediaSource: MediaSource,
        mimeType: String,
        fileExtension: String,
        isSent: Boolean,
    ): Uri? = runCatchingExceptions {
        findExistingGalleryUri(
            destination = mimeType.mediaSaveDestination(isSent),
            filename = mediaSource.safeUrl.deterministicGalleryFilename(fileExtension),
        )
    }.getOrNull()

    fun saveToGallery(sourceFile: File, key: String, fileExtension: String, mimeType: String, isSent: Boolean): Uri {
        val destination = mimeType.mediaSaveDestination(isSent)
        val filename = key.deterministicGalleryFilename(fileExtension)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${destination.topLevelDirectory}/${destination.subPath}")
            }
            val outputUri = context.contentResolver.insert(destination.collectionUri, contentValues)
                ?: error("Unable to create gallery entry")
            sourceFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(outputUri).use { output ->
                    input.copyTo(output!!)
                }
            }
            outputUri
        } else {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(destination.topLevelDirectory), destination.subPath).apply { mkdirs() }
            val targetFile = File(targetDir, filename)
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            fileProviderUriFor(targetFile)
        }
    }

    fun findExistingGalleryUri(destination: MediaSaveDestination, filename: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(destination.topLevelDirectory), destination.subPath)
            val file = File(targetDir, filename)
            return if (file.exists()) fileProviderUriFor(file) else null
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val relativePath = "${destination.topLevelDirectory}/${destination.subPath}/"
        val selectionArgs = arrayOf(filename, relativePath)
        context.contentResolver.query(destination.collectionUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(destination.collectionUri, id)
            }
        }
        return null
    }

    private fun fileProviderUriFor(file: File): Uri {
        val authority = "${buildMeta.applicationId}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /** Stable per-media-item filename, so saving and existence-checking always agree on the same name. */
    fun String.deterministicGalleryFilename(fileExtension: String): String {
        val hash = hashCode().toUInt().toString(16)
        return "urbanchat_$hash.$fileExtension"
    }

    /** [topLevelDirectory] must be one of the [Environment] `DIRECTORY_*` constants; [subPath] nests under it. */
    data class MediaSaveDestination(val topLevelDirectory: String, val subPath: String, val collectionUri: Uri)

    /**
     * DCIM is the only standard top-level directory MediaStore allows nesting *both* images and
     * videos under (Pictures/Movies would each only take one), so that's what everything shares
     * here — except generic files, which MediaStore's Downloads collection only accepts under
     * `Download/`, regardless of subpath (and which aren't split by direction: that distinction
     * only matters for the media the user actually looks at in the Gallery app).
     */
    fun String.mediaSaveDestination(isSent: Boolean): MediaSaveDestination {
        val direction = if (isSent) "Sent" else "Received"
        return when {
            isMimeTypeImage() -> MediaSaveDestination(
                Environment.DIRECTORY_DCIM,
                "$APP_MEDIA_FOLDER_NAME/UrbanImages/UrbanImages $direction",
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            )
            isMimeTypeVideo() -> MediaSaveDestination(
                Environment.DIRECTORY_DCIM,
                "$APP_MEDIA_FOLDER_NAME/UrbanVideos/UrbanVideos $direction",
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            )
            else -> MediaSaveDestination(Environment.DIRECTORY_DOWNLOADS, APP_MEDIA_FOLDER_NAME, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
    }
}
