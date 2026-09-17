/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.eventformatter.api.TimelineEventFormatter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.search.MessageSearchPaginationState
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

private const val QUERY_DEBOUNCE_MILLIS = 300L

@Inject
class MessageSearchPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val timelineEventFormatter: TimelineEventFormatter,
    private val dateFormatter: DateFormatter,
) : Presenter<MessageSearchState> {
    @Composable
    override fun present(): MessageSearchState {
        val coroutineScope = rememberCoroutineScope()
        val messageSearch = remember { matrixClient.messageSearchService.createMessageSearch(coroutineScope, room.roomId) }

        var query by rememberSaveable { mutableStateOf("") }
        var hasSearched by remember { mutableStateOf(false) }
        var searchFailed by remember { mutableStateOf(false) }
        val results by messageSearch.results.collectAsState()
        val paginationState by messageSearch.paginationState.collectAsState()

        LaunchedEffect(query) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                hasSearched = false
                searchFailed = false
                return@LaunchedEffect
            }
            delay(QUERY_DEBOUNCE_MILLIS)
            hasSearched = true
            messageSearch.setQuery(trimmed)
                .onFailure { Timber.e(it, "Failed to search messages") }
                .also { searchFailed = it.isFailure }
        }

        fun handleEvent(event: MessageSearchEvent) {
            when (event) {
                is MessageSearchEvent.UpdateQuery -> query = event.query
                MessageSearchEvent.LoadMore -> coroutineScope.launch { messageSearch.paginate() }
            }
        }

        val resultModels = remember(results, query) {
            if (query.isBlank()) {
                return@remember persistentListOf()
            }
            results.map { result ->
                val senderName = result.senderProfile.getDisambiguatedDisplayName(result.senderId)
                val avatarUrl = (result.senderProfile as? ProfileDetails.Ready)?.avatarUrl
                MessageSearchResultUiModel(
                    eventId = result.eventId,
                    avatarData = AvatarData(
                        id = result.senderId.value,
                        name = senderName,
                        url = avatarUrl,
                        size = AvatarSize.UserListItem,
                    ),
                    senderName = senderName,
                    formattedTimestamp = dateFormatter.format(
                        timestamp = result.timestamp,
                        mode = DateFormatterMode.TimeOrDate,
                        useRelative = true,
                    ),
                    bodyPreview = timelineEventFormatter.format(
                        content = result.content,
                        isOutgoing = result.senderId == matrixClient.sessionId,
                        sender = result.senderId,
                        senderDisambiguatedDisplayName = senderName,
                    ) ?: "",
                )
            }.toImmutableList()
        }

        // Room-filtering happens client-side: the SDK ranks results across every room the user
        // is in, so a room with few matches may need several pages before any of its results
        // surface. Keep paginating automatically while this room has nothing yet, instead of
        // relying on the user to scroll an empty list.
        val idlePaginationState = paginationState as? MessageSearchPaginationState.Idle
        LaunchedEffect(resultModels.size, idlePaginationState, query) {
            if (query.isBlank() || searchFailed) return@LaunchedEffect
            if (resultModels.isEmpty() && idlePaginationState != null && !idlePaginationState.endReached) {
                messageSearch.paginate()
                    .onFailure { Timber.e(it, "Failed to paginate message search") }
            }
        }

        val isCatchingUpToFirstMatch = hasSearched &&
            resultModels.isEmpty() &&
            !searchFailed &&
            (paginationState is MessageSearchPaginationState.Loading || idlePaginationState?.endReached == false)

        return MessageSearchState(
            query = query,
            results = resultModels,
            isLoading = paginationState is MessageSearchPaginationState.Loading || isCatchingUpToFirstMatch,
            hasSearched = hasSearched,
            eventSink = ::handleEvent,
        )
    }
}
