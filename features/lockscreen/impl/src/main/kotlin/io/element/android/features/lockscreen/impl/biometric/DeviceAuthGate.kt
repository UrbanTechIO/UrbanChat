/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.lockscreen.impl.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Result of a device-unlock (biometric/PIN/pattern) challenge gating access to something sensitive
 * (a locked room, the Locked Chats list, sharing into a locked room).
 */
sealed interface DeviceAuthState {
    data object Authenticating : DeviceAuthState
    data class Failed(val noDeviceSecurity: Boolean) : DeviceAuthState
    data object Unlocked : DeviceAuthState
}

/**
 * Drives a [DeviceAuthState] off the device's own lock screen (biometric, PIN, pattern) via
 * [BiometricAuthenticatorManager.rememberUnlockDeviceBiometricAuthenticator].
 *
 * A successful check is remembered for the rest of the app's current foreground session via
 * [chatLockUnlockTracker], so unlocking the Locked Chats list and then opening a room inside it
 * (or sharing into a locked room) don't each prompt separately.
 *
 * @param authTrigger while `null`, no authentication attempt is made and the state stays
 * [DeviceAuthState.Authenticating]. Passing any non-null value starts an attempt; changing the
 * value (e.g. an incrementing counter) starts a fresh attempt, which is how callers implement retry.
 */
@Composable
fun rememberDeviceAuthState(
    biometricAuthenticatorManager: BiometricAuthenticatorManager,
    chatLockUnlockTracker: ChatLockUnlockTracker,
    authTrigger: Any?,
): DeviceAuthState {
    val isAlreadyUnlocked by chatLockUnlockTracker.isUnlocked.collectAsState()
    var authState by remember { mutableStateOf<DeviceAuthState>(DeviceAuthState.Authenticating) }
    val authenticator = biometricAuthenticatorManager.rememberUnlockDeviceBiometricAuthenticator()

    LaunchedEffect(authenticator, authTrigger, isAlreadyUnlocked) {
        if (isAlreadyUnlocked) {
            authState = DeviceAuthState.Unlocked
            return@LaunchedEffect
        }
        if (authTrigger == null) return@LaunchedEffect
        if (!biometricAuthenticatorManager.canUseDeviceUnlock) {
            authState = DeviceAuthState.Failed(noDeviceSecurity = true)
            return@LaunchedEffect
        }
        authState = DeviceAuthState.Authenticating
        authenticator.setup()
        authState = when (authenticator.authenticate()) {
            BiometricAuthenticator.AuthenticationResult.Success -> {
                chatLockUnlockTracker.markUnlocked()
                DeviceAuthState.Unlocked
            }
            is BiometricAuthenticator.AuthenticationResult.Failure -> DeviceAuthState.Failed(noDeviceSecurity = false)
        }
    }
    return authState
}
