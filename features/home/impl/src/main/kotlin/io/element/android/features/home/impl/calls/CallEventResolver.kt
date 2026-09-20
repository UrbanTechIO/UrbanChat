/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.calls

import io.element.android.features.call.api.CallLogEntry
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.CallNotifyContent
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private const val MATCH_WINDOW_MILLIS = 10 * 60 * 1_000L
private const val MAX_PAGINATIONS = 8

/**
 * Finds the timeline event of the call in [entry]: the id captured when the call rang if we have it,
 * otherwise the call notification event closest to when the call started, paginating back a little if needed.
 */
suspend fun MatrixClient.findCallEventId(entry: CallLogEntry): EventId? {
    entry.eventId?.let { return EventId(it) }
    val timeline = getJoinedRoom(entry.roomId)?.liveTimeline ?: return null
    repeat(MAX_PAGINATIONS + 1) { attempt ->
        val events = timeline.timelineItems.first()
            .filterIsInstance<MatrixTimelineItem.Event>()
            .map { it.event }
        val best = events
            .filter { it.content is CallNotifyContent && it.eventId != null }
            .minByOrNull { abs(it.timestamp - entry.startedAtMillis) }
        if (best != null && abs(best.timestamp - entry.startedAtMillis) <= MATCH_WINDOW_MILLIS) return best.eventId
        val oldest = events.minOfOrNull { it.timestamp }
        if (oldest != null && oldest < entry.startedAtMillis - MATCH_WINDOW_MILLIS) return null
        if (attempt == MAX_PAGINATIONS) return null
        val hasMore = timeline.paginate(Timeline.PaginationDirection.BACKWARDS).getOrDefault(false)
        if (!hasMore) return null
    }
    return null
}
