/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.lockedchats

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import kotlinx.collections.immutable.ImmutableList

data class LockedChatsState(
    val authState: DeviceAuthState,
    val rooms: ImmutableList<RoomListRoomSummary>,
    val hideInvitesAvatars: Boolean,
    val eventSink: (LockedChatsEvent) -> Unit,
)
