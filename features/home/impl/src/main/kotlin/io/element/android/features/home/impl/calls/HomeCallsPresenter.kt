/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.call.api.CallLog
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.ui.model.getAvatarData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Inject
class HomeCallsPresenter(
    private val client: MatrixClient,
    private val callLog: CallLog,
    private val dateFormatter: DateFormatter,
) : Presenter<HomeCallsState> {
    @Composable
    override fun present(): HomeCallsState {
        val entries by remember { callLog.entries(client.sessionId) }.collectAsState(initial = emptyList())
        var rows: ImmutableList<CallLogRow> by remember { mutableStateOf(persistentListOf()) }
        LaunchedEffect(entries) {
            rows = entries.map { entry ->
                val info = client.getRoom(entry.roomId)?.info()
                CallLogRow(
                    entry = entry,
                    name = info?.name ?: entry.otherName ?: entry.roomId.value,
                    avatarData = info?.getAvatarData(size = AvatarSize.RoomListItem)
                        ?: AvatarData(id = entry.roomId.value, name = entry.otherName, size = AvatarSize.RoomListItem),
                    time = dateFormatter.format(entry.startedAtMillis, DateFormatterMode.TimeOrDate, useRelative = true),
                )
            }.toImmutableList()
        }
        return HomeCallsState(rows = rows)
    }
}
