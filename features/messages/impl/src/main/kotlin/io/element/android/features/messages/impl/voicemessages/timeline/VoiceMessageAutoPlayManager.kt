/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.voicemessages.timeline

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVoiceContent
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.mediaplayer.api.MediaPlayer
import io.element.android.libraries.voiceplayer.api.VoiceMessagePresenterFactory
import kotlinx.coroutines.flow.distinctUntilChanged

interface VoiceMessageAutoPlayManager {
    /**
     * Watches the shared voice-message player and, whenever a voice message finishes playing,
     * automatically starts the next voice message immediately following it in [timelineItems]
     * (the chain stops the moment it reaches anything that isn't a voice message, e.g. a text
     * message). Calls [onAdvancedToEvent] so the caller can keep that message visible (e.g. by
     * scrolling to it). Suspends forever watching for completions; call from a `LaunchedEffect`.
     */
    suspend fun observe(
        timelineItems: () -> List<TimelineItem>,
        onAdvancedToEvent: (EventId) -> Unit,
    )
}

@ContributesBinding(RoomScope::class)
class DefaultVoiceMessageAutoPlayManager(
    private val mediaPlayer: MediaPlayer,
    private val voiceMessagePresenterFactory: VoiceMessagePresenterFactory,
) : VoiceMessageAutoPlayManager {
    override suspend fun observe(
        timelineItems: () -> List<TimelineItem>,
        onAdvancedToEvent: (EventId) -> Unit,
    ) {
        mediaPlayer.state
            .distinctUntilChanged { old, new -> old.isEnded == new.isEnded && old.mediaId == new.mediaId }
            .collect { state ->
                val mediaId = state.mediaId
                if (!state.isEnded || mediaId == null) return@collect
                val finishedEventId = EventId(mediaId)
                val next = findNextVoiceContent(timelineItems(), finishedEventId) ?: return@collect
                val nextEventId = next.eventId ?: return@collect
                val started = voiceMessagePresenterFactory.playVoiceMessage(
                    eventId = nextEventId,
                    mediaSource = next.mediaSource,
                    mimeType = next.mimeType,
                    filename = next.filename,
                )
                if (started) {
                    onAdvancedToEvent(nextEventId)
                }
            }
    }

    /**
     * Timeline items are ordered newest-first (index 0 is the most recent, rendered at the
     * bottom), so the message sent chronologically *after* [afterEventId] sits at a *lower* index.
     * Virtual items (date separators, typing notifications, etc.) in between are skipped over —
     * only the next real *event* decides whether the chain continues.
     */
    private fun findNextVoiceContent(items: List<TimelineItem>, afterEventId: EventId): TimelineItemVoiceContent? {
        val index = items.indexOfFirst { it is TimelineItem.Event && it.eventId == afterEventId }
        if (index <= 0) return null
        for (i in index - 1 downTo 0) {
            val next = items[i] as? TimelineItem.Event ?: continue
            return next.content as? TimelineItemVoiceContent
        }
        return null
    }
}
