/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.components.HomeTopBar
import io.element.android.features.home.impl.components.RoomListContentView
import io.element.android.features.home.impl.components.RoomListMenuAction
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContextMenu
import io.element.android.features.home.impl.roomlist.RoomListDeclineInviteMenu
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.calls.CallLogRow
import io.element.android.features.home.impl.calls.HomeCallsView
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.search.RoomListSearchView
import io.element.android.features.home.impl.spacefilters.SpaceFiltersEvent
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersView
import io.element.android.features.home.impl.spaces.HomeSpacesView
import io.element.android.libraries.androidutils.throttler.FirstThrottler
import io.element.android.libraries.designsystem.colors.gradientSubtleColors
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.darken
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.HorizontalFloatingToolbar
import io.element.android.libraries.designsystem.theme.components.HorizontalFloatingToolbarItem
import io.element.android.libraries.designsystem.theme.components.HorizontalFloatingToolbarSeparator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.utils.lazyColumnContentPadding
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

@Composable
fun HomeView(
    homeState: HomeState,
    onRoomClick: (RoomId) -> Unit,
    onCallLogClick: (CallLogRow) -> Unit,
    onSettingsClick: () -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onStartChatClick: () -> Unit,
    onCreateSpaceClick: () -> Unit,
    onRoomSettingsClick: (roomId: RoomId) -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    onReportRoomClick: (roomId: RoomId) -> Unit,
    onDeclineInviteAndBlockUser: (roomSummary: RoomListRoomSummary) -> Unit,
    acceptDeclineInviteView: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leaveRoomView: @Composable () -> Unit,
) {
    val state: RoomListState = homeState.roomListState
    val coroutineScope = rememberCoroutineScope()
    val firstThrottler = remember { FirstThrottler(300, coroutineScope) }
    LaunchedEffect(state.searchState.revealLockedChatsEvents) {
        state.searchState.revealLockedChatsEvents.collect {
            onMenuActionClick(RoomListMenuAction.LockedChats)
        }
    }
    Box(modifier) {
        if (state.contextMenu is RoomListState.ContextMenu.Shown) {
            RoomListContextMenu(
                contextMenu = state.contextMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onRoomSettingsClick = onRoomSettingsClick,
                onReportRoomClick = onReportRoomClick,
            )
        }
        if (state.declineInviteMenu is RoomListState.DeclineInviteMenu.Shown) {
            RoomListDeclineInviteMenu(
                menu = state.declineInviteMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onDeclineAndBlockClick = onDeclineInviteAndBlockUser,
            )
        }

        leaveRoomView()

        HomeScaffold(
            state = homeState,
            onSetUpRecoveryClick = onSetUpRecoveryClick,
            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
            onRoomClick = { if (firstThrottler.canHandle()) onRoomClick(it) },
            onCallLogClick = { if (firstThrottler.canHandle()) onCallLogClick(it) },
            onOpenSettings = { if (firstThrottler.canHandle()) onSettingsClick() },
            onStartChatClick = { if (firstThrottler.canHandle()) onStartChatClick() },
            onCreateSpaceClick = { if (firstThrottler.canHandle()) onCreateSpaceClick() },
            onMenuActionClick = onMenuActionClick,
        )
        // This overlaid view will only be visible when state.displaySearchResults is true
        RoomListSearchView(
            state = state.searchState,
            eventSink = state.eventSink,
            hideInvitesAvatars = state.hideInvitesAvatars,
            onRoomClick = { if (firstThrottler.canHandle()) onRoomClick(it) },
            modifier = Modifier
                .fillMaxSize()
                .background(ElementTheme.colors.bgCanvasDefault)
        )
        acceptDeclineInviteView()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    state: HomeState,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onCallLogClick: (CallLogRow) -> Unit,
    onOpenSettings: () -> Unit,
    onStartChatClick: () -> Unit,
    onCreateSpaceClick: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun onRoomClick(room: RoomListRoomSummary) {
        onRoomClick(room.roomId)
    }

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val roomListState: RoomListState = state.roomListState

    BackHandler(enabled = state.isBackHandlerEnabled) {
        if (state.currentHomeNavigationBarItem != HomeNavigationBarItem.Chats) {
            state.eventSink(HomeEvent.SelectHomeNavigationBarItem(HomeNavigationBarItem.Chats))
        } else {
            val spaceFiltersState = state.roomListState.spaceFiltersState
            if (spaceFiltersState is SpaceFiltersState.Selected) {
                spaceFiltersState.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            }
        }
    }

    val hazeState = rememberHazeState()
    val roomsLazyListState = rememberLazyListState()
    val spacesLazyListState = rememberLazyListState()
    val callsLazyListState = rememberLazyListState()

    // Extends the top bar's own accent-colored gradient (see HomeTopBar's
    // backgroundVerticalGradient) down across the whole page, fading out by the vertical
    // midpoint of the screen instead of just the top bar's own small height, per the user's
    // "solid at the top, fading towards mid-page" request. Uses the same accent-derived stops
    // (see SemanticColors.withAccent) so both stay in sync with the user's theme color setting.
    val pageGradientColors = gradientSubtleColors()
    val pageBackgroundBrush = Brush.verticalGradient(
        // Compressed compared to a linear 0..0.5 spread: the top app bar paints its own solid/
        // frosted tint over stop1 anyway, so the visible fade needs to happen fast, right where
        // the (transparent) filters row sits, rather than staying vivid blue until mid-screen.
        colorStops = arrayOf(
            0.0f to pageGradientColors[0],
            0.03f to pageGradientColors[1],
            0.06f to pageGradientColors[2],
            0.10f to pageGradientColors[3],
            0.18f to pageGradientColors[4],
            0.30f to pageGradientColors[5],
        ),
    )
    Box(modifier = modifier.fillMaxSize().background(pageBackgroundBrush)) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                HomeTopBar(
                    selectedNavigationItem = state.currentHomeNavigationBarItem,
                    currentUserAndNeighbors = state.currentUserAndNeighbors,
                    showAvatarIndicator = state.showAvatarIndicator,
                    areSearchResultsDisplayed = roomListState.searchState.isSearchActive,
                    onToggleSearch = { roomListState.eventSink(RoomListEvent.ToggleSearchResults) },
                    onMenuActionClick = onMenuActionClick,
                    onOpenSettings = onOpenSettings,
                    onAccountSwitch = {
                        state.eventSink(HomeEvent.SwitchToAccount(it))
                    },
                    scrollBehavior = scrollBehavior,
                    displayFilters = state.displayRoomListFilters,
                    filtersState = roomListState.filtersState,
                    spaceFiltersState = roomListState.spaceFiltersState,
                    canReportBug = state.canReportBug,
                    modifier = if (state.frostedGlassEnabled) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = rememberAccentFrostedHazeStyle(state.headerBarOpacity),
                        )
                    } else {
                        Modifier.background(ElementTheme.colors.bgAccentRest.copy(alpha = 0.55f))
                    }
                )
            },
            floatingActionButton = {
                val coroutineScope = rememberCoroutineScope()
                HomeBottomBar(
                    // The Scaffold uses top-only insets so the scrollable content can go edge-to-edge behind the
                    // navigation bar, so the floating toolbar has to apply the bottom inset itself to avoid overlapping it.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    currentHomeNavigationBarItem = state.currentHomeNavigationBarItem,
                    onItemClick = { item ->
                        // scroll to top if selecting the same item
                        if (item == state.currentHomeNavigationBarItem) {
                            val lazyListStateTarget = when (item) {
                                HomeNavigationBarItem.Chats -> roomsLazyListState
                                HomeNavigationBarItem.Spaces -> spacesLazyListState
                                HomeNavigationBarItem.Calls -> callsLazyListState
                            }
                            coroutineScope.launch {
                                if (lazyListStateTarget.firstVisibleItemIndex > 10) {
                                    lazyListStateTarget.scrollToItem(10)
                                }
                                // Also reset the scrollBehavior height offset as it's not triggered by programmatic scrolls
                                scrollBehavior.state.heightOffset = 0f
                                lazyListStateTarget.animateScrollToItem(0)
                            }
                        } else {
                            state.eventSink(HomeEvent.SelectHomeNavigationBarItem(item))
                        }
                    },
                    floatingActionButton = {
                        when (state.currentHomeNavigationBarItem) {
                            HomeNavigationBarItem.Chats -> {
                                HomeFloatingActionButton(onStartChatClick, CommonStrings.action_create_room)
                            }
                            HomeNavigationBarItem.Spaces -> {
                                HomeFloatingActionButton(onCreateSpaceClick, CommonStrings.action_create_space)
                            }
                            HomeNavigationBarItem.Calls -> Unit
                        }
                    },
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            contentWindowInsets = scaffoldScrollableContentInsets,
            content = { padding ->
                // When frosted glass is on, the list must actually scroll behind the (now
                // translucent) top bar for Haze to have real content to blur, so the top inset
                // is left out here and passed as extra list content padding below instead
                // (mirrors MessagesView's extraTopContentPadding for the chat timeline).
                val outerPadding = PaddingValues(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    // Remove these two lines once https://issuetracker.google.com/issues/436432313 has been fixed
                    bottom = padding.calculateBottomPadding(),
                    top = if (state.frostedGlassEnabled) 0.dp else padding.calculateTopPadding()
                )
                val contentPadding = PaddingValues(
                    top = if (state.frostedGlassEnabled) padding.calculateTopPadding() else 0.dp,
                    bottom = 96.dp,
                )
                when (state.currentHomeNavigationBarItem) {
                    HomeNavigationBarItem.Chats -> {
                        RoomListContentView(
                            contentState = roomListState.contentState,
                            filtersState = roomListState.filtersState,
                            spaceFiltersState = roomListState.spaceFiltersState,
                            lazyListState = roomsLazyListState,
                            hideInvitesAvatars = roomListState.hideInvitesAvatars,
                            eventSink = roomListState.eventSink,
                            onSetUpRecoveryClick = onSetUpRecoveryClick,
                            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                            onRoomClick = ::onRoomClick,
                            onCreateRoomClick = onStartChatClick,
                            contentPadding = lazyColumnContentPadding + contentPadding,
                            modifier = Modifier
                                .padding(outerPadding)
                                .consumeWindowInsets(outerPadding)
                                // Repaints the same gradient the outer Box already shows through here,
                                // but as part of this composable's own subtree: Haze only picks up
                                // colors actually drawn within the hazeSource composable, so without
                                // this the blur behind the top bar only ever sees the list's mostly
                                // dark rows, never the accent-tinted gradient itself.
                                .background(pageBackgroundBrush)
                                .hazeSource(state = hazeState)
                        )
                        SpaceFiltersView(roomListState.spaceFiltersState)
                    }
                    HomeNavigationBarItem.Spaces -> {
                        HomeSpacesView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(outerPadding)
                                .consumeWindowInsets(outerPadding)
                                .background(pageBackgroundBrush)
                                .hazeSource(state = hazeState),
                            contentPadding = lazyColumnContentPadding + contentPadding,
                            state = state.homeSpacesState,
                            lazyListState = spacesLazyListState,
                            onSpaceClick = { spaceId ->
                                onRoomClick(spaceId)
                            },
                            onCreateSpaceClick = onCreateSpaceClick,
                            // TODO use actual callbacks for this
                            onExploreClick = {},
                        )
                    }
                    HomeNavigationBarItem.Calls -> {
                        HomeCallsView(
                            state = state.homeCallsState,
                            onCallClick = onCallLogClick,
                            contentPadding = lazyColumnContentPadding + contentPadding,
                            lazyListState = callsLazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(outerPadding)
                                .consumeWindowInsets(outerPadding)
                                .background(pageBackgroundBrush)
                                .hazeSource(state = hazeState),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        )
    }
}

/**
 * Frosted-glass haze style for the room list's top bar. Same blur/noise mechanism as the chat
 * timeline's equivalent (`rememberFrostedHazeStyle` in MessagesView.kt), but tinted with the
 * user's accent theme color instead of a neutral background color, per their theme color setting.
 */
@Composable
private fun rememberAccentFrostedHazeStyle(opacity: Float): HazeStyle {
    // Darkened rather than the raw accent color: the accent is tuned to read well as a small
    // solid button/highlight, not as a bar filling the full width of the screen, where the same
    // color reads as too bright/saturated.
    val accent = ElementTheme.colors.bgAccentRest.darken(0.6f)
    return remember(accent, opacity) {
        val tintAlpha = 1f - opacity.coerceIn(0f, 1f) * 0.9f
        HazeDefaults.style(
            backgroundColor = accent,
            tint = HazeDefaults.tint(accent.copy(alpha = tintAlpha)),
            blurRadius = HazeDefaults.blurRadius,
            noiseFactor = HazeDefaults.noiseFactor,
        )
    }
}

@Composable
private fun HomeFloatingActionButton(
    onClick: () -> Unit,
    contentDescription: Int,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = CompoundIcons.Plus(),
            contentDescription = stringResource(id = contentDescription),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeBottomBar(
    currentHomeNavigationBarItem: HomeNavigationBarItem,
    onItemClick: (HomeNavigationBarItem) -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: (@Composable () -> Unit)?,
) {
    HorizontalFloatingToolbar(
        floatingActionButton = floatingActionButton,
        modifier = modifier
            .zIndex(1f),
    ) {
        HomeNavigationBarItem.entries.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalFloatingToolbarSeparator()
            }
            val isSelected = currentHomeNavigationBarItem == item
            HorizontalFloatingToolbarItem(
                icon = item.icon(isSelected),
                tooltipLabel = stringResource(item.labelRes),
                isSelected = isSelected,
                onClick = { onItemClick(item) },
            )
        }
    }
}

internal fun RoomListRoomSummary.contentType() = displayType.ordinal

@PreviewsDayNight
@Composable
internal fun HomeViewPreview(@PreviewParameter(HomeStateProvider::class) state: HomeState) = ElementPreview {
    HomeView(
        homeState = state,
        onRoomClick = {},
        onCallLogClick = {},
        onSettingsClick = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onStartChatClick = {},
        onCreateSpaceClick = {},
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        onMenuActionClick = {},
        onDeclineInviteAndBlockUser = {},
        acceptDeclineInviteView = {},
        leaveRoomView = {}
    )
}

@Preview
@Composable
internal fun HomeViewA11yPreview() = ElementPreview {
    HomeView(
        homeState = aHomeState(),
        onRoomClick = {},
        onCallLogClick = {},
        onSettingsClick = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onStartChatClick = {},
        onCreateSpaceClick = {},
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        onMenuActionClick = {},
        onDeclineInviteAndBlockUser = {},
        acceptDeclineInviteView = {},
        leaveRoomView = {}
    )
}
