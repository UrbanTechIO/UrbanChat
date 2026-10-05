/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.test

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemorySessionPreferencesStore(
    isSharePresenceEnabled: Boolean = true,
    isShowOnlineStatusEnabled: Boolean = true,
    isOrganizeChatListsEnabled: Boolean = true,
    isKeepOriginalVideoSizeEnabled: Boolean = false,
    isSendPublicReadReceiptsEnabled: Boolean = true,
    isRenderReadReceiptsEnabled: Boolean = true,
    isSendTypingNotificationsEnabled: Boolean = true,
    isRenderTypingNotificationsEnabled: Boolean = true,
    isSessionVerificationSkipped: Boolean = false,
    doesCompressMedia: Boolean = true,
    videoCompressionPreset: VideoCompressionPreset = VideoCompressionPreset.STANDARD,
    lockedRoomIds: Set<RoomId> = emptySet(),
    lockedChatsAccessCode: String = "",
) : SessionPreferencesStore {
    private val isSharePresenceEnabled = MutableStateFlow(isSharePresenceEnabled)
    private val isShowOnlineStatusEnabled = MutableStateFlow(isShowOnlineStatusEnabled)
    private val isOrganizeChatListsEnabled = MutableStateFlow(isOrganizeChatListsEnabled)
    private val isKeepOriginalVideoSizeEnabled = MutableStateFlow(isKeepOriginalVideoSizeEnabled)
    private val isSendPublicReadReceiptsEnabled = MutableStateFlow(isSendPublicReadReceiptsEnabled)
    private val isRenderReadReceiptsEnabled = MutableStateFlow(isRenderReadReceiptsEnabled)
    private val isSendTypingNotificationsEnabled = MutableStateFlow(isSendTypingNotificationsEnabled)
    private val isRenderTypingNotificationsEnabled = MutableStateFlow(isRenderTypingNotificationsEnabled)
    private val isSessionVerificationSkipped = MutableStateFlow(isSessionVerificationSkipped)
    private val doesCompressMedia = MutableStateFlow(doesCompressMedia)
    private val videoCompressionPreset = MutableStateFlow(videoCompressionPreset)
    private val lockedRoomIds = MutableStateFlow(lockedRoomIds)
    private val lockedChatsAccessCode = MutableStateFlow(lockedChatsAccessCode)
    var clearCallCount = 0
        private set

    override suspend fun setSharePresence(enabled: Boolean) {
        isSharePresenceEnabled.tryEmit(enabled)
    }

    override fun isSharePresenceEnabled(): Flow<Boolean> = isSharePresenceEnabled

    override suspend fun setShowOnlineStatus(enabled: Boolean) {
        isShowOnlineStatusEnabled.tryEmit(enabled)
    }

    override fun isShowOnlineStatusEnabled(): Flow<Boolean> = isShowOnlineStatusEnabled

    override suspend fun setOrganizeChatLists(enabled: Boolean) {
        isOrganizeChatListsEnabled.tryEmit(enabled)
    }

    override fun isOrganizeChatListsEnabled(): Flow<Boolean> = isOrganizeChatListsEnabled

    override suspend fun setKeepOriginalVideoSize(enabled: Boolean) {
        isKeepOriginalVideoSizeEnabled.tryEmit(enabled)
    }

    override fun isKeepOriginalVideoSizeEnabled(): Flow<Boolean> = isKeepOriginalVideoSizeEnabled

    override suspend fun setSendPublicReadReceipts(enabled: Boolean) {
        isSendPublicReadReceiptsEnabled.tryEmit(enabled)
    }

    override fun isSendPublicReadReceiptsEnabled(): Flow<Boolean> = isSendPublicReadReceiptsEnabled

    override suspend fun setRenderReadReceipts(enabled: Boolean) {
        isRenderReadReceiptsEnabled.tryEmit(enabled)
    }

    override fun isRenderReadReceiptsEnabled(): Flow<Boolean> = isRenderReadReceiptsEnabled

    override suspend fun setSendTypingNotifications(enabled: Boolean) {
        isSendTypingNotificationsEnabled.tryEmit(enabled)
    }

    override fun isSendTypingNotificationsEnabled(): Flow<Boolean> = isSendTypingNotificationsEnabled

    override suspend fun setRenderTypingNotifications(enabled: Boolean) {
        isRenderTypingNotificationsEnabled.tryEmit(enabled)
    }

    override fun isRenderTypingNotificationsEnabled(): Flow<Boolean> = isRenderTypingNotificationsEnabled

    override suspend fun setSkipSessionVerification(skip: Boolean) {
        isSessionVerificationSkipped.tryEmit(skip)
    }

    override fun isSessionVerificationSkipped(): Flow<Boolean> {
        return isSessionVerificationSkipped
    }

    override suspend fun setOptimizeImages(compress: Boolean) = doesCompressMedia.emit(compress)

    override fun doesOptimizeImages(): Flow<Boolean> = doesCompressMedia

    override suspend fun setVideoCompressionPreset(preset: VideoCompressionPreset) {
        videoCompressionPreset.value = preset
    }

    override fun getVideoCompressionPreset(): Flow<VideoCompressionPreset> {
        return videoCompressionPreset
    }

    override suspend fun setRoomLocked(roomId: RoomId, isLocked: Boolean) {
        lockedRoomIds.value = if (isLocked) lockedRoomIds.value + roomId else lockedRoomIds.value - roomId
    }

    override fun lockedRoomIds(): Flow<Set<RoomId>> = lockedRoomIds

    override suspend fun setLockedChatsAccessCode(code: String) {
        lockedChatsAccessCode.value = code
    }

    override fun lockedChatsAccessCode(): Flow<String> = lockedChatsAccessCode

    override suspend fun clear() {
        clearCallCount++
        isSendPublicReadReceiptsEnabled.tryEmit(true)
    }
}
