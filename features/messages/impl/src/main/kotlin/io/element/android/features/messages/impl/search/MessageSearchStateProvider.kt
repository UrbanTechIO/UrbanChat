/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class MessageSearchStateProvider : PreviewParameterProvider<MessageSearchState> {
    override val values: Sequence<MessageSearchState>
        get() = sequenceOf(
            aMessageSearchState(query = ""),
            aMessageSearchState(query = "holiday", results = persistentListOf()),
            aMessageSearchState(
                query = "holiday",
                results = (1..3).map { aMessageSearchResultUiModel(index = it) }.toImmutableList(),
            ),
            aMessageSearchState(query = "holiday", isLoading = true),
        )
}

fun aMessageSearchState(
    query: String = "",
    results: ImmutableList<MessageSearchResultUiModel> = persistentListOf(),
    isLoading: Boolean = false,
    hasSearched: Boolean = query.isNotBlank(),
    eventSink: (MessageSearchEvent) -> Unit = {},
) = MessageSearchState(
    query = query,
    results = results,
    isLoading = isLoading,
    hasSearched = hasSearched,
    eventSink = eventSink,
)

fun aMessageSearchResultUiModel(index: Int = 0) = MessageSearchResultUiModel(
    eventId = EventId("\$event$index"),
    avatarData = AvatarData(id = "@alice:server.org", name = "Alice", url = null, size = AvatarSize.UserListItem),
    senderName = "Alice",
    formattedTimestamp = "10:3$index",
    bodyPreview = "Are we still on for the holiday trip this weekend? Message #$index",
)
