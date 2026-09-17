/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.lockscreen.test.biometric

import io.element.android.features.lockscreen.impl.biometric.ChatLockUnlockTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeChatLockUnlockTracker(
    isUnlocked: Boolean = false,
) : ChatLockUnlockTracker {
    private val _isUnlocked = MutableStateFlow(isUnlocked)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked

    override fun markUnlocked() {
        _isUnlocked.value = true
    }
}
