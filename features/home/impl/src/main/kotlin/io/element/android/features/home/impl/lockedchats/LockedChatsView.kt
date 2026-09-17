/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.lockedchats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.components.RoomSummaryRow
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.lazyColumnContentPadding
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun LockedChatsView(
    state: LockedChatsState,
    onBackClick: () -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onUnlockRoom: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_locked_chats_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
        content = { padding ->
            LockedChatsContent(
                state = state,
                onRoomClick = onRoomClick,
                onUnlockRoom = onUnlockRoom,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        },
        contentWindowInsets = scaffoldScrollableContentInsets,
    )
}

@Composable
private fun LockedChatsContent(
    state: LockedChatsState,
    onRoomClick: (RoomId) -> Unit,
    onUnlockRoom: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val authState = state.authState) {
            DeviceAuthState.Authenticating -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DeviceAuthState.Failed -> {
                LockedChatsAuthFailed(
                    noDeviceSecurity = authState.noDeviceSecurity,
                    onRetryClick = { state.eventSink(LockedChatsEvent.Retry) },
                )
            }
            DeviceAuthState.Unlocked -> {
                if (state.rooms.isEmpty()) {
                    LockedChatsEmpty()
                } else {
                    LazyColumn(contentPadding = lazyColumnContentPadding) {
                        item {
                            Text(
                                text = stringResource(R.string.screen_locked_chats_unlock_hint),
                                style = ElementTheme.typography.fontBodySmRegular,
                                color = ElementTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(
                            items = state.rooms,
                            key = { it.id },
                            contentType = { it.displayType },
                        ) { room ->
                            RoomSummaryRow(
                                room = room,
                                hideInviteAvatars = state.hideInvitesAvatars,
                                isInviteSeen = true,
                                onClick = { onRoomClick(it.roomId) },
                                eventSink = { event ->
                                    if (event is RoomListEvent.ShowContextMenu) {
                                        onUnlockRoom(event.roomSummary.roomId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedChatsAuthFailed(
    noDeviceSecurity: Boolean,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTitleSubtitleMolecule(
            title = if (noDeviceSecurity) {
                stringResource(R.string.screen_locked_chats_no_device_security_title)
            } else {
                stringResource(R.string.screen_locked_chats_auth_failed_title)
            },
            subTitle = if (noDeviceSecurity) {
                stringResource(R.string.screen_locked_chats_no_device_security_subtitle)
            } else {
                null
            },
            iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            text = stringResource(CommonStrings.action_retry),
            onClick = onRetryClick,
        )
    }
}

@Composable
private fun LockedChatsEmpty(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconTitleSubtitleMolecule(
            title = stringResource(R.string.screen_locked_chats_empty_title),
            subTitle = stringResource(R.string.screen_locked_chats_empty_subtitle),
            iconStyle = BigIcon.Style.Default(CompoundIcons.Lock()),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun LockedChatsViewPreview(@PreviewParameter(LockedChatsStateProvider::class) state: LockedChatsState) = ElementPreview {
    LockedChatsView(
        state = state,
        onBackClick = {},
        onRoomClick = {},
        onUnlockRoom = {},
    )
}
