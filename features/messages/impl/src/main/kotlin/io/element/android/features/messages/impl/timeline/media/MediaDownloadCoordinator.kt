/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.media.toFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Must match RustMediaLoader's own `cacheDirectory` (features/messages/impl has no access to that
// class, which lives in a lower-level module): the tempDir the Rust SDK is told to stream a
// download's bytes into while it's in flight, which the SDK's own Kotlin API otherwise never
// hands us a live look at.
private const val MEDIA_TEMP_DIR_RELATIVE_PATH = "temp/media"

/**
 * Automatic "download to Gallery" for images and videos, both sent and received — no manual
 * button, no tap required. Used by
 * [io.element.android.features.messages.impl.timeline.components.event.TimelineItemVideoView]
 * (which also shows a progress overlay while downloading, and plays back the saved local copy
 * once done) and [io.element.android.features.messages.impl.timeline.components.event.TimelineItemImageView]
 * (which saves silently in the background, with no visible change to how the image is shown).
 *
 * "Downloaded" status is derived from whether a matching file actually exists at its
 * deterministic Gallery destination (see [GalleryMediaLookup]), not from in-memory state alone —
 * so it survives leaving and re-entering the room (which recreates this [RoomScope]-scoped
 * coordinator). [ensureDownloaded] performs that check once per media item, and downloads+saves
 * it if it isn't there yet.
 *
 * The Gallery folder/filename scheme itself lives in [GalleryMediaLookup] rather than inline here,
 * so the media viewer (a lower-level module that can't depend on this RoomScope-scoped,
 * Compose-state-heavy class) can still look up an already-saved file — see
 * [io.element.android.libraries.mediaviewer.api.local.LocalGalleryMediaResolver], which is what
 * lets viewing a just-sent photo still work while offline.
 */
sealed interface MediaDownloadState {
    data object NotDownloaded : MediaDownloadState
    data class Downloading(val startTimeMs: Long) : MediaDownloadState
    data object Downloaded : MediaDownloadState
    data object Failed : MediaDownloadState
}

interface MediaDownloadCoordinator {
    /** Read inside a `@Composable`: backed by Compose snapshot state, so reads are observed. */
    fun stateFor(mediaSource: MediaSource): MediaDownloadState

    /**
     * Real bytes downloaded so far, polled directly off the SDK's own temp-download directory on
     * disk (there's no API for this — see this file's own top-level comment). Null when
     * [stateFor] isn't [MediaDownloadState.Downloading], or briefly at the very start of one
     * before the first poll finds a plausible file to track.
     */
    fun bytesDownloadedFor(mediaSource: MediaSource): Long?

    /** The local Gallery file's content URI, once [stateFor] reports [MediaDownloadState.Downloaded]. */
    fun localUriFor(mediaSource: MediaSource): Uri?

    /**
     * Confirms the file behind [localUriFor] is still actually there right before playing it back
     * — it may have been deleted from the Gallery after we last checked. Returns true if it's
     * still present. If it's gone, resets [stateFor] back to [MediaDownloadState.NotDownloaded]
     * (and re-arms [ensureDownloaded]) instead of the UI getting stuck pointing at a dead file.
     */
    fun verifyLocalFileStillExists(mediaSource: MediaSource): Boolean

    /**
     * Checks whether [mediaSource] was already saved to the Gallery (in a previous session or
     * earlier in this one) and, if so, moves [stateFor] straight to
     * [MediaDownloadState.Downloaded] with [localUriFor] populated. If it wasn't, downloads it and
     * saves it into the device's public Gallery — in an "UrbanChat" subfolder under the matching
     * category for [mimeType] (DCIM/UrbanImages, DCIM/UrbanVideos, or Download), split further
     * into a `Sent`/`Received` subfolder per [isSent]. No-ops on repeat calls for the same
     * [mediaSource]. Call once when an image or video attachment first appears.
     */
    fun ensureDownloaded(
        mediaSource: MediaSource,
        mimeType: String,
        filename: String,
        fileExtension: String,
        isSent: Boolean,
    )
}

@ContributesBinding(RoomScope::class)
class DefaultMediaDownloadCoordinator(
    @ApplicationContext private val context: Context,
    @CacheDirectory private val cacheDirectory: File,
    private val mediaLoader: MatrixMediaLoader,
    private val galleryMediaLookup: GalleryMediaLookup,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : MediaDownloadCoordinator {
    // Compose SnapshotStateMap: reads inside a @Composable are automatically observed, same as
    // any other Compose state, without needing a wrapping Flow/StateFlow here.
    private val states = mutableStateMapOf<String, MediaDownloadState>()
    private val localUris = mutableStateMapOf<String, Uri>()

    // Real, polled-from-disk progress, keyed the same as [states]; absent (rather than 0) until
    // the first poll finds a plausible in-flight file, so the UI can fall back to its own
    // time-based estimate for that initial gap instead of showing a misleading "0 MB".
    private val bytesDownloaded = mutableStateMapOf<String, Long>()

    // Plain (non-Compose) guard so [ensureDownloaded] only ever kicks off one existence
    // check/download per media item, regardless of how many times the composable recomposes.
    private val checkedKeys = ConcurrentHashMap.newKeySet<String>()

    override fun stateFor(mediaSource: MediaSource): MediaDownloadState {
        return states[mediaSource.safeUrl] ?: MediaDownloadState.NotDownloaded
    }

    override fun bytesDownloadedFor(mediaSource: MediaSource): Long? {
        return bytesDownloaded[mediaSource.safeUrl]
    }

    override fun localUriFor(mediaSource: MediaSource): Uri? {
        return localUris[mediaSource.safeUrl]
    }

    override fun verifyLocalFileStillExists(mediaSource: MediaSource): Boolean {
        val key = mediaSource.safeUrl
        val uri = localUris[key] ?: return false
        val exists = runCatchingExceptions {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true }
        }.getOrNull() ?: false
        if (!exists) {
            localUris.remove(key)
            states.remove(key)
            checkedKeys.remove(key)
        }
        return exists
    }

    override fun ensureDownloaded(
        mediaSource: MediaSource,
        mimeType: String,
        filename: String,
        fileExtension: String,
        isSent: Boolean,
    ) {
        val key = mediaSource.safeUrl
        if (!checkedKeys.add(key)) return
        sessionCoroutineScope.launch {
            val destination = with(galleryMediaLookup) { mimeType.mediaSaveDestination(isSent) }
            val galleryFilename = with(galleryMediaLookup) { key.deterministicGalleryFilename(fileExtension) }
            val existingUri = runCatchingExceptions {
                galleryMediaLookup.findExistingGalleryUri(destination, galleryFilename)
            }.getOrNull()
            if (existingUri != null) {
                localUris[key] = existingUri
                states[key] = MediaDownloadState.Downloaded
            } else {
                startDownload(mediaSource, mimeType, filename, fileExtension, isSent)
            }
        }
    }

    private fun startDownload(
        mediaSource: MediaSource,
        mimeType: String,
        filename: String,
        fileExtension: String,
        isSent: Boolean,
    ) {
        val key = mediaSource.safeUrl
        val current = states[key]
        if (current is MediaDownloadState.Downloading || current is MediaDownloadState.Downloaded) return
        states[key] = MediaDownloadState.Downloading(startTimeMs = System.currentTimeMillis())
        val pollJob = sessionCoroutineScope.launch { pollDownloadedBytes(key) }
        sessionCoroutineScope.launch {
            mediaLoader.downloadMediaFile(
                source = mediaSource,
                mimeType = mimeType,
                filename = filename,
            ).fold(
                onSuccess = { mediaFile ->
                    pollJob.cancel()
                    bytesDownloaded.remove(key)
                    val savedUri = runCatchingExceptions {
                        mediaFile.use { galleryMediaLookup.saveToGallery(mediaFile.toFile(), key, fileExtension, mimeType, isSent) }
                    }.onFailure {
                        Timber.e(it, "Failed to save downloaded media to gallery")
                    }.getOrNull()
                    if (savedUri != null) {
                        localUris[key] = savedUri
                        states[key] = MediaDownloadState.Downloaded
                    } else {
                        states[key] = MediaDownloadState.Failed
                    }
                },
                onFailure = {
                    pollJob.cancel()
                    bytesDownloaded.remove(key)
                    Timber.e(it, "Failed to download media")
                    states[key] = MediaDownloadState.Failed
                },
            )
        }
    }

    /**
     * Polls the SDK's own media-download temp directory for a file that appeared or grew since we
     * started, and reports its size as our best real signal for "bytes downloaded so far". This
     * relies on internal behaviour (see [MEDIA_TEMP_DIR_RELATIVE_PATH]'s doc), not a public
     * contract, so it's written defensively: any failure to read the directory just means no
     * update this tick, never a crash, and the caller (via [bytesDownloadedFor] returning null)
     * falls back to a time-based estimate instead.
     */
    private suspend fun pollDownloadedBytes(key: String) {
        val tempDir = File(cacheDirectory, MEDIA_TEMP_DIR_RELATIVE_PATH)
        val before = runCatchingExceptions {
            tempDir.listFiles()?.associate { it.name to it.length() }
        }.getOrNull() ?: emptyMap()
        while (currentCoroutineContext().isActive) {
            delay(250)
            runCatchingExceptions {
                tempDir.listFiles()
                    ?.filter { it.name !in before || it.length() != before.getValue(it.name) }
                    ?.maxByOrNull { it.length() }
            }.getOrNull()?.let { candidate ->
                bytesDownloaded[key] = candidate.length()
            }
        }
    }
}
