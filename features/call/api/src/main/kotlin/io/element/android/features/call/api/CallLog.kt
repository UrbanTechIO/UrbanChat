/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.api

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

enum class CallLogType { Outgoing, Incoming, Missed, Declined }

data class CallLogEntry(
    val id: String,
    val sessionId: SessionId,
    val roomId: RoomId,
    val type: CallLogType,
    val isAudioCall: Boolean,
    val startedAtMillis: Long,
    /** Null when the call was never answered. */
    val durationSeconds: Long?,
    /** Caller display name, when known (incoming calls only). */
    val otherName: String?,
    /** Event id of the call notification, when known. */
    val eventId: String?,
)

/** Device-local history of the calls made, received and missed on this device. */
interface CallLog {
    fun entries(sessionId: SessionId): Flow<List<CallLogEntry>>
    suspend fun add(entry: CallLogEntry)
    suspend fun clear(sessionId: SessionId)
}
