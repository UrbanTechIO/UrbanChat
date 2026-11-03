/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.selection

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The messages ticked in the open chat, for deleting several at once. Selection mode is on while at least one message
 * is ticked; swiping a message to the left ticks the first one, then a tap ticks or unticks the others.
 */
object MessageSelection {
    private val _selected = MutableStateFlow<Map<Any, TimelineItem.Event>>(emptyMap())

    /** Ticked messages, in the order they were ticked. */
    val selected: StateFlow<Map<Any, TimelineItem.Event>> = _selected

    /** Whether the user may also delete other people's messages in this room (moderators). */
    @Volatile
    var canRedactOther: Boolean = false

    /** Only sent messages can be deleted, and only the user's own unless they are a moderator. */
    fun isSelectable(event: TimelineItem.Event): Boolean {
        return event.eventId != null && (event.isMine || canRedactOther)
    }

    fun toggle(event: TimelineItem.Event) {
        if (!isSelectable(event)) return
        _selected.update { current ->
            if (current.containsKey(event.id)) current - event.id else current + (event.id to event)
        }
    }

    fun clear() {
        _selected.value = emptyMap()
    }
}
