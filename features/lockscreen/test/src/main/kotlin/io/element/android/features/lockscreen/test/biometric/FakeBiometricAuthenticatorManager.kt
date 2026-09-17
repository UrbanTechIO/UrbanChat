/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.lockscreen.test.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticator
import io.element.android.features.lockscreen.impl.biometric.BiometricAuthenticatorManager

class FakeBiometricAuthenticatorManager(
    override val isDeviceSecured: Boolean = true,
    override val canUseDeviceUnlock: Boolean = true,
    override val hasAvailableAuthenticator: Boolean = false,
    private val createBiometricAuthenticator: () -> BiometricAuthenticator = { FakeBiometricAuthenticator() },
    private val disableLambda: suspend () -> Unit = { },
) : BiometricAuthenticatorManager {
    override fun addCallback(callback: BiometricAuthenticator.Callback) {
        // no-op
    }

    override fun removeCallback(callback: BiometricAuthenticator.Callback) {
        // no-op
    }

    @Composable
    override fun rememberUnlockBiometricAuthenticator(): BiometricAuthenticator {
        return remember {
            createBiometricAuthenticator()
        }
    }

    @Composable
    override fun rememberUnlockDeviceBiometricAuthenticator(): BiometricAuthenticator {
        return remember {
            createBiometricAuthenticator()
        }
    }

    @Composable
    override fun rememberConfirmBiometricAuthenticator(): BiometricAuthenticator {
        return remember {
            createBiometricAuthenticator()
        }
    }

    override suspend fun disable() {
        disableLambda()
    }
}
