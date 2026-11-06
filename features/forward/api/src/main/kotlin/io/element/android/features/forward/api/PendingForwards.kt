/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.forward.api

import io.element.android.libraries.matrix.api.core.EventId

/**
 * Messages forwarded together with the one that opens the forward screen. Forwarding several selected messages sets the
 * others here, then opens the forward screen for the first one; the screen takes them when it is created.
 */
object PendingForwards {
    @Volatile
    private var extraEventIds: List<EventId> = emptyList()

    fun set(eventIds: List<EventId>) {
        extraEventIds = eventIds
    }

    /** Returns the waiting messages and clears them, so they are only forwarded once. */
    fun take(): List<EventId> {
        val taken = extraEventIds
        extraEventIds = emptyList()
        return taken
    }
}
