/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.RoomId

open class ShareStateProvider : PreviewParameterProvider<ShareState> {
    override val values: Sequence<ShareState>
        get() = sequenceOf(
            aShareState(),
            aShareState(
                shareAction = AsyncAction.Loading,
            ),
            aShareState(
                shareAction = AsyncAction.Success(
                    listOf(RoomId("!room2:domain")),
                )
            ),
            aShareState(
                shareAction = AsyncAction.Failure(RuntimeException("error")),
            ),
            aShareState(
                pendingAuthState = DeviceAuthState.Authenticating,
            ),
            aShareState(
                pendingAuthState = DeviceAuthState.Failed(noDeviceSecurity = false),
            ),
        )
}

fun aShareState(
    shareAction: AsyncAction<List<RoomId>> = AsyncAction.Uninitialized,
    pendingAuthState: DeviceAuthState? = null,
    eventSink: (ShareEvents) -> Unit = {}
) = ShareState(
    shareAction = shareAction,
    imageEditorState = null,
    isApplyingImageEdits = false,
    displayImageEditError = false,
    pendingAuthState = pendingAuthState,
    eventSink = eventSink
)
