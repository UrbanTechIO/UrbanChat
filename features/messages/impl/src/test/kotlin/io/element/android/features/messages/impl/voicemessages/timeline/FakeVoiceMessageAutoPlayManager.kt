/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.voicemessages.timeline

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.coroutines.awaitCancellation

class FakeVoiceMessageAutoPlayManager : VoiceMessageAutoPlayManager {
    override suspend fun observe(
        timelineItems: () -> List<TimelineItem>,
        onAdvancedToEvent: (EventId) -> Unit,
    ) {
        awaitCancellation()
    }
}
