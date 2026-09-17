/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.lockscreen.impl.biometric

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.services.appnavstate.api.AppForegroundStateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tracks whether the user has already passed a Chat Lock biometric/device-credential check during the
 * current foreground session, so that unlocking the Locked Chats list and then opening a room inside it
 * don't each prompt separately. Resets as soon as the app leaves the foreground.
 */
interface ChatLockUnlockTracker {
    val isUnlocked: StateFlow<Boolean>
    fun markUnlocked()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultChatLockUnlockTracker(
    @AppCoroutineScope
    private val coroutineScope: CoroutineScope,
    private val appForegroundStateService: AppForegroundStateService,
) : ChatLockUnlockTracker {
    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked

    init {
        coroutineScope.launch {
            appForegroundStateService.startObservingForeground()
            appForegroundStateService.isInForeground.collect { isInForeground ->
                if (!isInForeground) {
                    _isUnlocked.value = false
                }
            }
        }
    }

    override fun markUnlocked() {
        _isUnlocked.value = true
    }
}
