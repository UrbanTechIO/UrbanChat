/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.api.CallLogType
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun HomeCallsView(
    state: HomeCallsState,
    onCallClick: (CallLogRow) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    if (state.rows.isEmpty()) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = stringResource(R.string.screen_home_calls_empty),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState, contentPadding = contentPadding) {
        items(state.rows, key = { it.entry.id }) { row ->
            CallLogRowView(row = row, onClick = { onCallClick(row) })
        }
    }
}

@Composable
private fun CallLogRowView(
    row: CallLogRow,
    onClick: () -> Unit,
) {
    val entry = row.entry
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(avatarData = row.avatarData, avatarType = AvatarType.Room())
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = if (entry.type == CallLogType.Missed) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val direction = stringResource(
                when (entry.type) {
                    CallLogType.Outgoing -> if (entry.durationSeconds == null) R.string.screen_home_calls_no_answer else R.string.screen_home_calls_outgoing
                    CallLogType.Incoming -> R.string.screen_home_calls_incoming
                    CallLogType.Missed -> R.string.screen_home_calls_missed
                    CallLogType.Declined -> R.string.screen_home_calls_declined
                }
            )
            val duration = entry.durationSeconds?.let { " · " + formatDuration(it) }.orEmpty()
            Text(
                text = "$direction · ${row.time}$duration",
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (entry.isAudioCall) CompoundIcons.VoiceCall() else CompoundIcons.VideoCall(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
