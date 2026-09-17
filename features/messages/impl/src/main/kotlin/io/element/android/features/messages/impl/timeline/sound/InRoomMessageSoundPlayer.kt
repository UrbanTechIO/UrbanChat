/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.sound

import android.content.Context
import android.media.RingtoneManager
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Plays a short, one-shot sound for a message received while its room is open in the foreground —
 * WhatsApp-style. This is independent of the notification channel's own sound
 * ([AppPreferencesStore.getMessageSoundFlow]): Android suppresses the notification (and so its
 * sound) for the room currently on screen, since there's nothing to notify the user of that they
 * can't already see.
 */
fun interface InRoomMessageSoundPlayer {
    suspend fun playIfEnabled()
}

@ContributesBinding(AppScope::class)
class DefaultInRoomMessageSoundPlayer(
    @ApplicationContext private val context: Context,
    private val appPreferencesStore: AppPreferencesStore,
) : InRoomMessageSoundPlayer {
    override suspend fun playIfEnabled() {
        if (!appPreferencesStore.isInRoomMessageSoundEnabledFlow().first()) return
        val sound = appPreferencesStore.getInRoomMessageSoundFlow().first()
        val uri = when (sound) {
            NotificationSound.Silent -> return
            // The bundled Element tones live in a different module (libraries/push) purely as
            // notification-channel raw resources; reusing the system default here avoids pulling
            // in that dependency just for this.
            NotificationSound.SystemDefault,
            NotificationSound.ElementDefault,
            NotificationSound.ElementFade -> RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            is NotificationSound.Custom -> sound.uri.toUri()
        } ?: return
        runCatchingExceptions {
            RingtoneManager.getRingtone(context, uri)?.play()
        }.onFailure {
            Timber.w(it, "Failed to play in-room message sound")
        }
    }
}
