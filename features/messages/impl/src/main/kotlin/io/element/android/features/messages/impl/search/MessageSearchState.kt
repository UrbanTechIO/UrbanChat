/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.ImmutableList

data class MessageSearchState(
    val query: String,
    val results: ImmutableList<MessageSearchResultUiModel>,
    val isLoading: Boolean,
    val hasSearched: Boolean,
    val eventSink: (MessageSearchEvent) -> Unit,
)

data class MessageSearchResultUiModel(
    val eventId: EventId,
    val avatarData: AvatarData,
    val senderName: String,
    val formattedTimestamp: String,
    val bodyPreview: CharSequence,
)
