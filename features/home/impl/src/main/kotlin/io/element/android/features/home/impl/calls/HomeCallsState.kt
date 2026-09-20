/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.calls

import io.element.android.features.call.api.CallLogEntry
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import kotlinx.collections.immutable.ImmutableList

data class HomeCallsState(
    val rows: ImmutableList<CallLogRow>,
)

data class CallLogRow(
    val entry: CallLogEntry,
    val name: String,
    val avatarData: AvatarData,
    val time: String,
)
