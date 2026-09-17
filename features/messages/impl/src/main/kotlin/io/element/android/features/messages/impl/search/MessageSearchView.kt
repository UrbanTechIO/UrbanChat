/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.lazyColumnContentPadding
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

@Composable
fun MessageSearchView(
    state: MessageSearchState,
    onBackClick: () -> Unit,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MessageSearchTopBar(
                query = state.query,
                onQueryChange = { state.eventSink(MessageSearchEvent.UpdateQuery(it)) },
                onBackClick = onBackClick,
            )
        },
        content = { padding ->
            MessageSearchContent(
                state = state,
                onResultClick = onResultClick,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        },
        contentWindowInsets = scaffoldScrollableContentInsets,
    )
}

@Composable
private fun MessageSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    TopAppBar(
        modifier = modifier,
        navigationIcon = { BackButton(onClick = onBackClick) },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.screen_room_search_placeholder),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
    )
    LaunchedEffect(Unit) {
        // Requesting focus immediately races the Appyx push-transition: the previous screen is
        // still in composition and can steal focus back once the transition settles, which shows
        // the keyboard for a moment and then dismisses it. Give the transition time to finish first.
        delay(300)
        focusRequester.requestFocus()
    }
}

@Composable
private fun MessageSearchContent(
    state: MessageSearchState,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.query.isBlank() -> MessageSearchEmptyState(titleRes = R.string.screen_room_search_initial_state_title)
            state.results.isEmpty() && state.hasSearched && !state.isLoading ->
                MessageSearchEmptyState(titleRes = R.string.screen_room_search_no_results_title)
            else -> MessageSearchResultsList(
                results = state.results,
                isLoading = state.isLoading,
                onResultClick = onResultClick,
                onLoadMore = { state.eventSink(MessageSearchEvent.LoadMore) },
            )
        }
    }
}

@Composable
private fun MessageSearchResultsList(
    results: ImmutableList<MessageSearchResultUiModel>,
    isLoading: Boolean,
    onResultClick: (EventId) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Load the next page once the user scrolls within 5 rows of the end.
    LaunchedEffect(listState, results.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= results.size - 5) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = lazyColumnContentPadding,
    ) {
        itemsIndexed(
            items = results,
            key = { _, result -> result.eventId.value },
        ) { index, result ->
            MessageSearchResultRow(
                result = result,
                onClick = { onResultClick(result.eventId) },
            )
            if (index < results.lastIndex) {
                HorizontalDivider()
            }
        }
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun MessageSearchEmptyState(
    titleRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconTitleSubtitleMolecule(
            title = stringResource(titleRes),
            subTitle = null,
            iconStyle = BigIcon.Style.Default(CompoundIcons.Search()),
        )
    }
}

@Composable
private fun MessageSearchResultRow(
    result: MessageSearchResultUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Avatar(
                avatarData = result.avatarData,
                avatarType = AvatarType.User,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = result.senderName,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = result.formattedTimestamp,
                style = ElementTheme.typography.fontBodyXsRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Text(
            text = result.bodyPreview.toString(),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 44.dp, top = 2.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun MessageSearchViewPreview(@PreviewParameter(MessageSearchStateProvider::class) state: MessageSearchState) = ElementPreview {
    MessageSearchView(
        state = state,
        onBackClick = {},
        onResultClick = {},
    )
}
