/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditorState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.RoomId

data class ShareState(
    val shareAction: AsyncAction<List<RoomId>>,
    val imageEditorState: AttachmentImageEditorState?,
    val isApplyingImageEdits: Boolean,
    val displayImageEditError: Boolean,
    /** Non-null while sharing into at least one locked room requires unlocking the device first. */
    val pendingAuthState: DeviceAuthState?,
    /** Non-null while a shared video is waiting for the user to choose its quality before sending. */
    val videoQualityPrompt: ShareVideoQualityPrompt?,
    val eventSink: (ShareEvents) -> Unit
)

data class ShareVideoQualityPrompt(
    /** Size of the shared video, if known. */
    val fileSizeBytes: Long?,
    /** The server's upload limit, if known. */
    val maxUploadSizeBytes: Long?,
) {
    val isOverLimit: Boolean
        get() = fileSizeBytes != null && maxUploadSizeBytes != null && fileSizeBytes > maxUploadSizeBytes
}
