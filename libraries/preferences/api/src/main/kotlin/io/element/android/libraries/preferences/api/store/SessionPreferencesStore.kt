/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow

interface SessionPreferencesStore {
    /** Room IDs the user has locally locked via Chat Lock. Local-only, never synced. */
    suspend fun setRoomLocked(roomId: RoomId, isLocked: Boolean)
    fun lockedRoomIds(): Flow<Set<RoomId>>

    /** Secret code typed into the room list search bar to reveal the hidden Locked Chats entry. Empty means unset/disabled. */
    suspend fun setLockedChatsAccessCode(code: String)
    fun lockedChatsAccessCode(): Flow<String>
    suspend fun setSharePresence(enabled: Boolean)
    fun isSharePresenceEnabled(): Flow<Boolean>

    /** Whether other users can see this account as online. Unrelated to [isSharePresenceEnabled] (read receipts/typing). */
    suspend fun setShowOnlineStatus(enabled: Boolean)
    fun isShowOnlineStatusEnabled(): Flow<Boolean>

    suspend fun setSendPublicReadReceipts(enabled: Boolean)
    fun isSendPublicReadReceiptsEnabled(): Flow<Boolean>

    suspend fun setRenderReadReceipts(enabled: Boolean)
    fun isRenderReadReceiptsEnabled(): Flow<Boolean>

    suspend fun setSendTypingNotifications(enabled: Boolean)
    fun isSendTypingNotificationsEnabled(): Flow<Boolean>

    suspend fun setRenderTypingNotifications(enabled: Boolean)
    fun isRenderTypingNotificationsEnabled(): Flow<Boolean>

    suspend fun setSkipSessionVerification(skip: Boolean)
    fun isSessionVerificationSkipped(): Flow<Boolean>

    suspend fun setOptimizeImages(compress: Boolean)
    fun doesOptimizeImages(): Flow<Boolean>

    suspend fun setVideoCompressionPreset(preset: VideoCompressionPreset)
    fun getVideoCompressionPreset(): Flow<VideoCompressionPreset>

    suspend fun clear()
}
