/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.lockedchats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.datasource.RoomListDataSource
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager
import io.element.android.features.lockscreen.impl.biometric.ChatLockUnlockTracker
import io.element.android.features.lockscreen.impl.biometric.rememberDeviceAuthState
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.ui.safety.rememberHideInvitesAvatar
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Inject
class LockedChatsPresenter(
    private val client: MatrixClient,
    private val roomListDataSource: RoomListDataSource,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val biometricAuthenticatorManager: BiometricAuthenticatorManager,
    private val chatLockUnlockTracker: ChatLockUnlockTracker,
) : Presenter<LockedChatsState> {
    @Composable
    override fun present(): LockedChatsState {
        var retryTrigger by remember { mutableIntStateOf(0) }
        val authState = rememberDeviceAuthState(biometricAuthenticatorManager, chatLockUnlockTracker, authTrigger = retryTrigger)

        val hideInvitesAvatar by client.rememberHideInvitesAvatar()
        val lockedRoomIds by remember { sessionPreferencesStore.lockedRoomIds() }.collectAsState(emptySet())
        val allSummaries by remember { roomListDataSource.roomSummariesFlow }.collectAsState(persistentListOf())
        val rooms = remember(allSummaries, lockedRoomIds) {
            allSummaries.filter { lockedRoomIds.contains(it.roomId) }.toImmutableList()
        }

        fun handleEvent(event: LockedChatsEvent) {
            when (event) {
                LockedChatsEvent.Retry -> retryTrigger++
            }
        }

        return LockedChatsState(
            authState = authState,
            rooms = rooms,
            hideInvitesAvatars = hideInvitesAvatar,
            eventSink = ::handleEvent,
        )
    }
}
