/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.lockedchats

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class LockedChatsStateProvider : PreviewParameterProvider<LockedChatsState> {
    override val values: Sequence<LockedChatsState>
        get() = sequenceOf(
            aLockedChatsState(authState = DeviceAuthState.Authenticating),
            aLockedChatsState(authState = DeviceAuthState.Failed(noDeviceSecurity = false)),
            aLockedChatsState(authState = DeviceAuthState.Failed(noDeviceSecurity = true)),
            aLockedChatsState(
                authState = DeviceAuthState.Unlocked,
                rooms = persistentListOf(),
            ),
            aLockedChatsState(
                authState = DeviceAuthState.Unlocked,
                rooms = listOf(
                    aRoomListRoomSummary(id = "!room1:domain", name = "Alice"),
                    aRoomListRoomSummary(id = "!room2:domain", name = "Bob"),
                ).toImmutableList(),
            ),
        )
}

fun aLockedChatsState(
    authState: DeviceAuthState = DeviceAuthState.Unlocked,
    rooms: ImmutableList<RoomListRoomSummary> = persistentListOf(),
    hideInvitesAvatars: Boolean = false,
    eventSink: (LockedChatsEvent) -> Unit = {},
) = LockedChatsState(
    authState = authState,
    rooms = rooms,
    hideInvitesAvatars = hideInvitesAvatars,
    eventSink = eventSink,
)
