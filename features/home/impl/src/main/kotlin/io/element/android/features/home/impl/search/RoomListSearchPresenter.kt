/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@Inject
class RoomListSearchPresenter(
    private val dataSourceFactory: RoomListSearchDataSource.Factory,
    private val sessionPreferencesStore: SessionPreferencesStore,
) : Presenter<RoomListSearchState> {
    // A class-level (not `remember`ed) one-shot event: the Home screen is only mounted while its
    // NavTarget is on top of the backstack, so any state derived via `remember` inside `present()`
    // gets recreated from scratch every time the user navigates back to it. A plain counter that
    // stays > 0 forever would immediately re-fire navigation on that fresh composition. A SharedFlow
    // with no replay only reaches whoever is actively collecting at emission time, so it can't leak
    // into a later, unrelated composition.
    private val revealLockedChatsEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Composable
    override fun present(): RoomListSearchState {
        // Do not use rememberSaveable so that search is not active when the user navigates back to the screen
        var isSearchActive by remember {
            mutableStateOf(false)
        }
        val searchQuery = rememberTextFieldState()

        val coroutineScope = rememberCoroutineScope()
        val dataSource = remember { dataSourceFactory.create(coroutineScope) }

        LaunchedEffect(searchQuery.text) {
            dataSource.setSearchQuery(searchQuery.text.toString())
        }

        val lockedChatsAccessCode by remember { sessionPreferencesStore.lockedChatsAccessCode() }.collectAsState(initial = "")
        LaunchedEffect(searchQuery.text, lockedChatsAccessCode) {
            val code = lockedChatsAccessCode
            if (code.isNotBlank() && searchQuery.text.toString() == code) {
                revealLockedChatsEvents.tryEmit(Unit)
                searchQuery.clearText()
            }
        }

        fun handleEvent(event: RoomListSearchEvent) {
            when (event) {
                RoomListSearchEvent.ClearQuery -> {
                    searchQuery.clearText()
                }
                RoomListSearchEvent.ToggleSearchVisibility -> {
                    isSearchActive = !isSearchActive
                    searchQuery.clearText()
                }
                is RoomListSearchEvent.UpdateVisibleRange -> coroutineScope.launch {
                    dataSource.updateVisibleRange(visibleRange = event.range)
                }
            }
        }

        val lockedRoomIds by remember { sessionPreferencesStore.lockedRoomIds() }.collectAsState(emptySet())
        val searchResults by dataSource.roomSummaries.collectAsState(initial = persistentListOf())

        return RoomListSearchState(
            isSearchActive = isSearchActive,
            query = searchQuery,
            // Locked chats must never surface through search results, same as the main room
            // list: the only way to reach them is via the access-code reveal flow above.
            results = searchResults
                .filterNot { lockedRoomIds.contains(it.roomId) }
                .toImmutableList(),
            revealLockedChatsEvents = revealLockedChatsEvents.asSharedFlow(),
            eventSink = ::handleEvent,
        )
    }
}
