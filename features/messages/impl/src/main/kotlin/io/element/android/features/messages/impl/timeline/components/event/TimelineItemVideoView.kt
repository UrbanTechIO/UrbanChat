/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.media.LocalFloatingVideoPlayerController
import io.element.android.features.messages.impl.timeline.media.LocalMediaDownloadContext
import io.element.android.features.messages.impl.timeline.media.MediaDownloadState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContentProvider
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.features.messages.impl.timeline.protection.coerceRatioWhenHidingContent
import io.element.android.features.messages.impl.timeline.util.handleAsyncImageStateChange
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.modifiers.roundedBackground
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_HEIGHT
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_WIDTH
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationState
import io.element.android.libraries.matrix.ui.media.contentvalidation.NoopContentValidationState
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectOverallState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TimelineItemVideoView(
    content: TimelineItemVideoContent,
    isMine: Boolean,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowContentClick: () -> Unit,
    contentValidationState: ContentValidationState,
    modifier: Modifier = Modifier,
) {
    val isTalkbackActive = isTalkbackActive()
    val a11yLabel = stringResource(CommonStrings.common_video)
    val description = content.caption?.let { "$a11yLabel: $it" } ?: a11yLabel

    val mediaDownloadContext = LocalMediaDownloadContext.current
    val downloadToGalleryEnabled = mediaDownloadContext.downloadToGalleryEnabled
    val downloadState = mediaDownloadContext.coordinator.stateFor(content.mediaSource)
    val localContext = LocalContext.current
    val floatingVideoPlayerController = LocalFloatingVideoPlayerController.current

    // When the setting is off, media behaves exactly as it did before this feature existed: no
    // progress overlay, no gating, just the original click-to-open-viewer / stream-from-server
    // path. When it's on, this happens automatically — no button, no tap required.
    //
    // Only for media received from others: anything *we* sent necessarily came from somewhere on
    // this device already (gallery, camera, files), so re-downloading our own sent video from the
    // server and saving a second copy would just create a duplicate in the Gallery.
    if (downloadToGalleryEnabled && !isMine) {
        LaunchedEffect(content.mediaSource) {
            mediaDownloadContext.coordinator.ensureDownloaded(
                mediaSource = content.mediaSource,
                mimeType = content.mimeType,
                filename = content.filename,
                fileExtension = content.fileExtension,
                isSent = isMine,
            )
        }
    }

    val effectiveOnContentClick: (() -> Unit)? = when {
        !downloadToGalleryEnabled -> onContentClick
        downloadState is MediaDownloadState.NotDownloaded ||
            downloadState is MediaDownloadState.Failed ||
            downloadState is MediaDownloadState.Downloading -> onContentClick
        else -> {
            // Downloaded: play straight from the local Gallery copy, never re-fetching from the server.
            val localUri = mediaDownloadContext.coordinator.localUriFor(content.mediaSource)
            if (localUri != null) {
                {
                    if (mediaDownloadContext.coordinator.verifyLocalFileStillExists(content.mediaSource)) {
                        // Play in the small floating mini-player rather than handing off to an
                        // external app, so the user can keep reading/scrolling the chat while it
                        // plays; tapping the mini-player itself still opens the full system player.
                        val controller = floatingVideoPlayerController
                        if (controller != null) {
                            controller.show(localUri, content.mimeType)
                        } else {
                            runCatchingExceptions {
                                localContext.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(localUri, content.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                )
                            }
                        }
                    } else {
                        // The Gallery copy was deleted since we last checked: state has just been
                        // reset to NotDownloaded, so stream this once from the server instead of
                        // leaving the tap with no effect.
                        onContentClick?.invoke()
                    }
                }
            } else {
                onContentClick
            }
        }
    }

    Column(modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally)) {
        val containerModifier = if (content.showCaption) {
            Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(6.dp))
        } else {
            Modifier
        }

        val eventContentValidation by contentValidationState.collectOverallState()
        val isContentBeingValidated = !eventContentValidation.isValidated()
        TimelineItemAspectRatioBox(
            modifier = containerModifier.blurHashBackground(content.blurHash, alpha = 0.9f),
            aspectRatio = coerceRatioWhenHidingContent(content.aspectRatio, hideMediaContent),
            contentAlignment = Alignment.Center,
        ) {
            ProtectedView(
                hideContent = hideMediaContent,
                onShowClick = onShowContentClick,
            ) {
                var isLoaded by remember { mutableStateOf(false) }
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isLoaded) Modifier.background(Color.White) else Modifier)
                        .then(
                            if (!isTalkbackActive && effectiveOnContentClick != null) {
                                Modifier
                                    .combinedClickable(
                                        onClick = effectiveOnContentClick,
                                        onLongClick = onLongClick,
                                    )
                                    .onKeyboardContextMenuAction(onLongClick)
                            } else {
                                Modifier
                            }
                        ),
                    model = MediaRequestData(
                        source = content.thumbnailSource,
                        kind = MediaRequestData.Kind.Thumbnail(
                            width = content.thumbnailWidth?.toLong() ?: MAX_THUMBNAIL_WIDTH,
                            height = content.thumbnailHeight?.toLong() ?: MAX_THUMBNAIL_HEIGHT,
                        )
                    ),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    contentDescription = description,
                    onState = { state ->
                        val url = content.thumbnailSource?.safeUrl
                        if (url != null) {
                            handleAsyncImageStateChange(
                                state = state,
                                onLoaded = { isLoaded = true },
                                updateContentValidationState = { contentValidationState.update(url, it) },
                            )
                        }
                    },
                )

                if (isContentBeingValidated) {
                    CircularProgressIndicator()
                } else if (!downloadToGalleryEnabled) {
                    Box(
                        modifier = Modifier.roundedBackground(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            imageVector = CompoundIcons.PlaySolid(),
                            contentDescription = stringResource(id = CommonStrings.a11y_play),
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                    }
                } else if (downloadState is MediaDownloadState.Downloading) {
                    // Not yet downloaded/failed: the video is still fully watchable via normal
                    // streaming (effectiveOnContentClick already falls back to that), so just show
                    // the ordinary play icon there too — no "download" affordance to tap anymore.
                    DownloadingIndicator(
                        startTimeMs = downloadState.startTimeMs,
                        totalBytes = content.fileSize,
                        realBytesDownloaded = mediaDownloadContext.coordinator.bytesDownloadedFor(content.mediaSource),
                    )
                } else {
                    Box(
                        modifier = Modifier.roundedBackground(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            imageVector = CompoundIcons.PlaySolid(),
                            contentDescription = stringResource(id = CommonStrings.a11y_play),
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.semantics { hideFromAccessibility() }
                        )
                    }
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemVideoViewPreview(@PreviewParameter(TimelineItemVideoContentProvider::class) content: TimelineItemVideoContent) = ElementPreview {
    TimelineItemVideoView(
        content = content,
        isMine = false,
        hideMediaContent = false,
        onShowContentClick = {},
        onContentClick = {},
        onLongClick = {},
        contentValidationState = NoopContentValidationState(),
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineItemVideoViewHideMediaContentPreview() = ElementPreview {
    TimelineItemVideoView(
        content = aTimelineItemVideoContent(),
        isMine = false,
        hideMediaContent = true,
        onShowContentClick = {},
        onContentClick = {},
        onLongClick = {},
        contentValidationState = NoopContentValidationState(),
    )
}

/**
 * Circular spinner plus a "~X MB / Y MB" readout. [realBytesDownloaded], when present, is polled
 * directly off the SDK's own temp-download file on disk — see
 * [io.element.android.features.messages.impl.timeline.media.MediaDownloadCoordinator]'s doc for
 * why that's a best-effort read of internal behaviour, not a public API. Only while that hasn't
 * reported anything yet (e.g. the first fraction of a second) does this fall back to a
 * time-elapsed guess, so the number on screen is real whenever it can be.
 */
@Composable
private fun DownloadingIndicator(
    startTimeMs: Long,
    totalBytes: Long?,
    realBytesDownloaded: Long?,
    modifier: Modifier = Modifier,
) {
    val assumedBytesPerSecond = 2 * 1024 * 1024L // Conservative simulated rate; fallback only, see doc above.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startTimeMs, realBytesDownloaded) {
        // No need to keep ticking once real progress is available: that already recomposes this
        // on its own whenever the coordinator's poll updates it.
        if (realBytesDownloaded != null) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(300)
        }
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .roundedBackground(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = Color.White,
            strokeWidth = 3.dp,
        )
        if (totalBytes != null && totalBytes > 0) {
            val totalMb = totalBytes / (1024f * 1024f)
            val downloadedMb = if (realBytesDownloaded != null) {
                (realBytesDownloaded / (1024f * 1024f)).coerceAtMost(totalMb)
            } else {
                val elapsedSeconds = (nowMs - startTimeMs) / 1000f
                (elapsedSeconds * assumedBytesPerSecond / (1024f * 1024f)).coerceIn(0f, totalMb * 0.95f)
            }
            Text(
                text = "${if (realBytesDownloaded == null) "~" else ""}${downloadedMb.roundToInt()}/${totalMb.roundToInt()} MB",
                style = ElementTheme.typography.fontBodyXsMedium,
                color = Color.White,
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        }
    }
}
