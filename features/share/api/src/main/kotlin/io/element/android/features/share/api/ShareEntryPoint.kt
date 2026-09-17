/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.matrix.api.core.RoomId

interface ShareEntryPoint : FeatureEntryPoint {
    /**
     * @param preselectedRoomId when set (e.g. the user tapped a Direct Share target for this
     * specific room from the OS Sharesheet), the room picker is skipped entirely and the content
     * is sent straight to this room.
     */
    data class Params(val shareIntentData: ShareIntentData, val preselectedRoomId: RoomId? = null)

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone(roomIds: List<RoomId>)
    }
}
