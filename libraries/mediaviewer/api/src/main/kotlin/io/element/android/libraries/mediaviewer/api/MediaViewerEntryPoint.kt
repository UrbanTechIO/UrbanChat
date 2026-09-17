/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import kotlinx.parcelize.Parcelize

interface MediaViewerEntryPoint : FeatureEntryPoint {
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    fun createParamsForAvatar(filename: String, avatarUrl: String): Params

    interface Callback : Plugin {
        fun onDone()
        fun viewInTimeline(eventId: EventId)
        fun forwardEvent(eventId: EventId, fromPinnedEvents: Boolean)

        /**
         * Called when the user wants to edit a copy of the given image and send it as a new message.
         * [localMedia] points to a copy of the downloaded file owned by the receiver of this callback.
         * Only hosts which can route to the message composer flow should override this; the action is
         * hidden when the viewer is not opened from a timeline or event gallery.
         */
        fun editImage(localMedia: LocalMedia) = Unit
    }

    sealed interface Params : NodeInputs {
        data class RoomMedia(
            val mode: MediaViewerMode,
            val eventId: EventId?,
            val mediaInfo: MediaInfo,
            val mediaSource: MediaSource,
            val thumbnailSource: MediaSource?,
            val blurHash: String?,
        ) : Params

        data class EventGallery(
            val eventId: EventId?,
            val galleryInfo: GalleryInfo,
            val galleryItems: List<GalleryItemData>,
            val fromPinnedMessages: Boolean,
        ) : Params

        data class Avatar(
            val avatarInfo: AvatarInfo,
            val mediaSource: MediaSource,
            val thumbnailSource: MediaSource?,
            val blurHash: String?,
        ) : Params
    }

    sealed interface MediaViewerMode : Parcelable {
        @Parcelize
        data class EventGallery(val fromPinnedMessages: Boolean) : MediaViewerMode

        @Parcelize
        data class TimelineImagesAndVideos(val timelineMode: Timeline.Mode) : MediaViewerMode

        @Parcelize
        data class TimelineFilesAndAudios(val timelineMode: Timeline.Mode) : MediaViewerMode
    }
}
