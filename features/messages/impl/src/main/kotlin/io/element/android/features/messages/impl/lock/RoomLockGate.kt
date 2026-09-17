/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager
import io.element.android.features.lockscreen.impl.biometric.ChatLockUnlockTracker
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import io.element.android.features.lockscreen.impl.biometric.rememberDeviceAuthState
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Gates [content] behind the device's own lock screen (biometric/PIN/pattern) whenever the room is
 * locked. Renders nothing of [content] until authentication succeeds, so no timeline data or read
 * receipts are ever processed before the user has proven they can see this room. This is the single
 * choke point that closes every path into a locked room's content — notification taps, permalinks,
 * deep links, and the auto-navigate-after-share flow all end up here, not just the room list.
 */
@Composable
fun RoomLockGate(
    biometricAuthenticatorManager: BiometricAuthenticatorManager,
    chatLockUnlockTracker: ChatLockUnlockTracker,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var retryTrigger by remember { mutableIntStateOf(0) }
    val authState = rememberDeviceAuthState(biometricAuthenticatorManager, chatLockUnlockTracker, authTrigger = retryTrigger)

    BackHandler(enabled = authState !is DeviceAuthState.Unlocked, onBack = onBackClick)

    when (authState) {
        DeviceAuthState.Authenticating -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DeviceAuthState.Failed -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconTitleSubtitleMolecule(
                    title = if (authState.noDeviceSecurity) {
                        stringResource(R.string.screen_room_lock_no_device_security_title)
                    } else {
                        stringResource(R.string.screen_room_lock_auth_failed_title)
                    },
                    subTitle = if (authState.noDeviceSecurity) {
                        stringResource(R.string.screen_room_lock_no_device_security_subtitle)
                    } else {
                        null
                    },
                    iconStyle = BigIcon.Style.Default(CompoundIcons.LockSolid()),
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    text = stringResource(CommonStrings.action_retry),
                    onClick = { retryTrigger++ },
                )
            }
        }
        DeviceAuthState.Unlocked -> content()
    }
}
