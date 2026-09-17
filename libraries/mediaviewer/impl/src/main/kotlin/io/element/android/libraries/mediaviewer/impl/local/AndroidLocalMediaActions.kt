/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.net.toFile
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.system.startInstallFromSourceIntent
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeImage
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeVideo
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

private const val EDITABLE_MEDIA_DIR_NAME = "editable-media"
private const val APP_MEDIA_FOLDER_NAME = "UrbanChat"

@ContributesBinding(AppScope::class)
class AndroidLocalMediaActions(
    @ApplicationContext private val context: Context,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val buildMeta: BuildMeta,
) : LocalMediaActions {
    private var activityContext: Context? = null
    private var apkInstallLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null
    private var pendingMedia: LocalMedia? = null

    @Composable
    override fun Configure() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        apkInstallLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { activityResult ->
            if (activityResult.resultCode == Activity.RESULT_OK) {
                pendingMedia?.let {
                    coroutineScope.launch {
                        openFile(it)
                    }
                }
            } else {
                // User cancelled
            }
            pendingMedia = null
        }
        return DisposableEffect(Unit) {
            activityContext = context
            onDispose {
                activityContext = null
            }
        }
    }

    override suspend fun saveOnDisk(localMedia: LocalMedia): Result<Unit> = withContext(coroutineDispatchers.io) {
        require(localMedia.uri.scheme == ContentResolver.SCHEME_FILE)
        runCatchingExceptions {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveOnDiskUsingMediaStore(localMedia)
            } else {
                saveOnDiskUsingExternalStorageApi(localMedia)
            }
        }.onSuccess {
            Timber.v("Save on disk succeed")
        }.onFailure {
            Timber.e(it, "Save on disk failed")
        }
    }

    override suspend fun share(localMedia: LocalMedia): Result<Unit> = withContext(coroutineDispatchers.io) {
        require(localMedia.uri.scheme == ContentResolver.SCHEME_FILE)
        runCatchingExceptions {
            // Make a copy of the shared file in the cache directory, otherwise the original file will be gone once this screen is dismissed
            // and will prevent sharing the media to another room inside the app.
            val copiedFile = localMedia.uri.toFile()
                .copyTo(File(context.cacheDir, "temp/media/" + (localMedia.uri.lastPathSegment ?: "shared_file")), true)
            val shareableUri = copiedFile.toShareableUri()
            val shareMediaIntent = Intent(Intent.ACTION_SEND)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, shareableUri)
                .setTypeAndNormalize(localMedia.info.mimeType)
            withContext(coroutineDispatchers.main) {
                val intent = Intent.createChooser(shareMediaIntent, null)
                activityContext!!.startActivity(intent)
            }
        }.onSuccess {
            Timber.v("Share media succeed")
        }.onFailure {
            Timber.e(it, "Share media failed")
        }
    }

    override suspend fun copyForEditing(localMedia: LocalMedia): Result<LocalMedia> = withContext(coroutineDispatchers.io) {
        runCatchingExceptions {
            val editableMediaDir = File(context.cacheDir, EDITABLE_MEDIA_DIR_NAME).apply { mkdirs() }
            val outputFile = File(editableMediaDir, "${UUID.randomUUID()}-${localMedia.info.filename}")
            context.contentResolver.openInputStream(localMedia.uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open the media to copy it")
            localMedia.copy(uri = outputFile.toUri())
        }.onFailure {
            Timber.e(it, "Failed to copy media for editing")
        }
    }

    override suspend fun open(localMedia: LocalMedia): Result<Unit> = withContext(coroutineDispatchers.io) {
        require(localMedia.uri.scheme == ContentResolver.SCHEME_FILE)
        runCatchingExceptions {
            when (localMedia.info.mimeType) {
                MimeTypes.Apk -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (PermissionChecker.checkPermission(
                                context,
                                Manifest.permission.REQUEST_INSTALL_PACKAGES,
                                -1,
                                -1,
                                context.packageName
                            ) == PermissionChecker.PERMISSION_GRANTED &&
                            activityContext?.packageManager?.canRequestPackageInstalls() == false) {
                            pendingMedia = localMedia
                            activityContext?.startInstallFromSourceIntent(apkInstallLauncher!!).let { }
                        } else {
                            openFile(localMedia)
                        }
                    } else {
                        openFile(localMedia)
                    }
                }
                else -> openFile(localMedia)
            }
        }.onSuccess {
            Timber.v("Open media succeed")
        }.onFailure {
            Timber.e(it, "Open media failed")
        }
    }

    private suspend fun openFile(localMedia: LocalMedia) {
        val openMediaIntent = Intent(Intent.ACTION_VIEW)
            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setDataAndType(localMedia.toShareableUri(), localMedia.info.mimeType)
        withContext(coroutineDispatchers.main) {
            activityContext?.startActivity(openMediaIntent)
        }
    }

    private fun File.toShareableUri(): Uri {
        val authority = "${buildMeta.applicationId}.fileprovider"
        return FileProvider.getUriForFile(context, authority, this).normalizeScheme()
    }

    private fun LocalMedia.toShareableUri(): Uri {
        return this.toFile().toShareableUri()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveOnDiskUsingMediaStore(localMedia: LocalMedia) {
        val destination = localMedia.info.mimeType.mediaSaveDestination()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, localMedia.info.filename)
            put(MediaStore.MediaColumns.MIME_TYPE, localMedia.info.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${destination.topLevelDirectory}/${destination.subPath}")
        }
        val resolver = context.contentResolver
        val outputUri = resolver.insert(destination.collectionUri, contentValues)
        if (outputUri != null) {
            localMedia.openStream()?.use { input ->
                resolver.openOutputStream(outputUri).use { output ->
                    input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }

    private fun saveOnDiskUsingExternalStorageApi(localMedia: LocalMedia) {
        val destination = localMedia.info.mimeType.mediaSaveDestination()
        val targetDir = File(Environment.getExternalStoragePublicDirectory(destination.topLevelDirectory), destination.subPath).apply { mkdirs() }
        val target = File(targetDir, localMedia.info.filename)
        localMedia.openStream()?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    /** [topLevelDirectory] must be one of the [Environment] `DIRECTORY_*` constants; [subPath] nests under it. */
    private data class MediaSaveDestination(val topLevelDirectory: String, val subPath: String, val collectionUri: Uri)

    /**
     * DCIM is the only standard top-level directory MediaStore allows nesting *both* images and
     * videos under (Pictures/Movies would each only take one), so that's what everything shares
     * here — except generic files, which MediaStore's Downloads collection only accepts under
     * `Download/`, regardless of subpath.
     */
    private fun String.mediaSaveDestination(): MediaSaveDestination = when {
        isMimeTypeImage() -> MediaSaveDestination(Environment.DIRECTORY_DCIM, "$APP_MEDIA_FOLDER_NAME/UrbanImages", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        isMimeTypeVideo() -> MediaSaveDestination(Environment.DIRECTORY_DCIM, "$APP_MEDIA_FOLDER_NAME/UrbanVideos", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        else -> MediaSaveDestination(Environment.DIRECTORY_DOWNLOADS, APP_MEDIA_FOLDER_NAME, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
    }

    private fun LocalMedia.openStream(): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    /**
     * Tries to extract a file from the uri.
     */
    private fun LocalMedia.toFile(): File {
        return uri.toFile()
    }
}
