/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.voiceplayer.api

import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlin.time.Duration

interface VoiceMessagePresenterFactory {
    fun createVoiceMessagePresenter(
        eventId: EventId?,
        mediaSource: MediaSource,
        mimeType: String?,
        filename: String?,
        duration: Duration,
    ): Presenter<VoiceMessageState>

    /**
     * Starts playing the given voice message directly, without a dedicated [Presenter]. Since all
     * voice messages in a room share the same underlying player, any currently visible voice
     * message item for [eventId] will reflect this as it starts playing.
     *
     * @return true if playback was started successfully.
     */
    suspend fun playVoiceMessage(
        eventId: EventId,
        mediaSource: MediaSource,
        mimeType: String?,
        filename: String?,
    ): Boolean
}
