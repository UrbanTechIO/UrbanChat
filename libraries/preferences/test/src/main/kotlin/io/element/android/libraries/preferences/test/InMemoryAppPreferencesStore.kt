/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.test

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

class InMemoryAppPreferencesStore(
    isDeveloperModeEnabled: Boolean = false,
    customElementCallBaseUrl: String? = null,
    hideInviteAvatars: Boolean? = null,
    timelineMediaPreviewValue: MediaPreviewValue? = null,
    theme: String? = null,
    accentColor: String? = null,
    chatBackgroundColor: String? = null,
    chatBackgroundImagePath: String? = null,
    frostedGlassEnabled: Boolean = false,
    headerBarOpacity: Float = 0.3f,
    composerBarOpacity: Float = 0.15f,
    bubbleBarOpacity: Float = 0.5f,
    outgoingBubbleColor: String? = null,
    incomingBubbleColor: String? = null,
    liveLocationMinimumDistanceUpdate: Int = 10,
    logLevel: LogLevel = LogLevel.INFO,
    traceLogPacks: Set<TraceLogPack> = emptySet(),
    downloadToGalleryEnabled: Boolean = false,
    urlPreviewEnabled: Boolean = true,
    inRoomMessageSoundEnabled: Boolean = false,
    inRoomMessageSound: NotificationSound = NotificationSound.SystemDefault,
    messageSound: NotificationSound = NotificationSound.SystemDefault,
    messageSoundChannelVersion: Int = 0,
    messageSoundDisplayName: String? = null,
    callRingtone: NotificationSound = NotificationSound.SystemDefault,
    callRingtoneChannelVersion: Int = 0,
    callRingtoneDisplayName: String? = null,
) : AppPreferencesStore {
    private val isDeveloperModeEnabled = MutableStateFlow(isDeveloperModeEnabled)
    private val customElementCallBaseUrl = MutableStateFlow(customElementCallBaseUrl)
    private val theme = MutableStateFlow(theme)
    private val accentColor = MutableStateFlow(accentColor)
    private val chatBackgroundColor = MutableStateFlow(chatBackgroundColor)
    private val chatBackgroundImagePath = MutableStateFlow(chatBackgroundImagePath)
    private val frostedGlassEnabled = MutableStateFlow(frostedGlassEnabled)
    private val headerBarOpacity = MutableStateFlow(headerBarOpacity)
    private val composerBarOpacity = MutableStateFlow(composerBarOpacity)
    private val bubbleBarOpacity = MutableStateFlow(bubbleBarOpacity)
    private val outgoingBubbleColor = MutableStateFlow(outgoingBubbleColor)
    private val incomingBubbleColor = MutableStateFlow(incomingBubbleColor)
    private val liveLocationMinimumDistanceUpdate = MutableStateFlow(liveLocationMinimumDistanceUpdate)
    private val logLevel = MutableStateFlow(logLevel)
    private val tracingLogPacks = MutableStateFlow(traceLogPacks)
    private val hideInviteAvatars = MutableStateFlow(hideInviteAvatars)
    private val timelineMediaPreviewValue = MutableStateFlow(timelineMediaPreviewValue)
    private val downloadToGalleryEnabled = MutableStateFlow(downloadToGalleryEnabled)
    private val urlPreviewEnabled = MutableStateFlow(urlPreviewEnabled)
    private val inRoomMessageSoundEnabled = MutableStateFlow(inRoomMessageSoundEnabled)
    private val inRoomMessageSound = MutableStateFlow(inRoomMessageSound)
    private val messageSound = MutableStateFlow(messageSound)
    private val messageSoundChannelVersion = MutableStateFlow(messageSoundChannelVersion)
    private val messageSoundDisplayName = MutableStateFlow(messageSoundDisplayName)
    private val callRingtone = MutableStateFlow(callRingtone)
    private val callRingtoneChannelVersion = MutableStateFlow(callRingtoneChannelVersion)
    private val callRingtoneDisplayName = MutableStateFlow(callRingtoneDisplayName)

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        isDeveloperModeEnabled.value = enabled
    }

    override fun isDeveloperModeEnabledFlow(): Flow<Boolean> {
        return isDeveloperModeEnabled
    }

    override suspend fun setCustomElementCallBaseUrl(string: String?) {
        customElementCallBaseUrl.tryEmit(string)
    }

    override fun getCustomElementCallBaseUrlFlow(): Flow<String?> {
        return customElementCallBaseUrl
    }

    override suspend fun setTheme(theme: String) {
        this.theme.value = theme
    }

    override fun getThemeFlow(): Flow<String?> {
        return theme
    }

    override suspend fun setAccentColor(colorHex: String?) {
        accentColor.value = colorHex
    }

    override fun getAccentColorFlow(): Flow<String?> {
        return accentColor
    }

    override suspend fun setChatBackgroundColor(colorHex: String?) {
        chatBackgroundColor.value = colorHex
    }

    override fun getChatBackgroundColorFlow(): Flow<String?> {
        return chatBackgroundColor
    }

    override suspend fun setChatBackgroundImage(path: String?) {
        chatBackgroundImagePath.value = path
    }

    override fun getChatBackgroundImagePathFlow(): Flow<String?> {
        return chatBackgroundImagePath
    }

    override suspend fun setFrostedGlassEnabled(enabled: Boolean) {
        frostedGlassEnabled.value = enabled
    }

    override fun isFrostedGlassEnabledFlow(): Flow<Boolean> {
        return frostedGlassEnabled
    }

    override suspend fun setHeaderBarOpacity(opacity: Float) {
        headerBarOpacity.value = opacity
    }

    override fun getHeaderBarOpacityFlow(): Flow<Float> {
        return headerBarOpacity
    }

    override suspend fun setComposerBarOpacity(opacity: Float) {
        composerBarOpacity.value = opacity
    }

    override fun getComposerBarOpacityFlow(): Flow<Float> {
        return composerBarOpacity
    }

    override suspend fun setBubbleBarOpacity(opacity: Float) {
        bubbleBarOpacity.value = opacity
    }

    override fun getBubbleBarOpacityFlow(): Flow<Float> {
        return bubbleBarOpacity
    }

    override suspend fun setOutgoingBubbleColor(colorHex: String?) {
        outgoingBubbleColor.value = colorHex
    }

    override fun getOutgoingBubbleColorFlow(): Flow<String?> {
        return outgoingBubbleColor
    }

    override suspend fun setIncomingBubbleColor(colorHex: String?) {
        incomingBubbleColor.value = colorHex
    }

    override fun getIncomingBubbleColorFlow(): Flow<String?> {
        return incomingBubbleColor
    }

    override suspend fun setLiveLocationMinimumDistanceInMetersUpdate(value: Int) {
        liveLocationMinimumDistanceUpdate.value = value
    }

    override fun getLiveLocationMinimumDistanceInMetersUpdateFlow(): Flow<Int> {
        return liveLocationMinimumDistanceUpdate
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getHideInviteAvatarsFlow(): Flow<Boolean?> {
        return hideInviteAvatars
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?> {
        return timelineMediaPreviewValue
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setHideInviteAvatars(hide: Boolean?) {
        hideInviteAvatars.value = hide
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?) {
        timelineMediaPreviewValue.value = mediaPreviewValue
    }

    override suspend fun setTracingLogLevel(logLevel: LogLevel) {
        this.logLevel.value = logLevel
    }

    override fun getTracingLogLevelFlow(): Flow<LogLevel> {
        return logLevel
    }

    override suspend fun setTracingLogPacks(targets: Set<TraceLogPack>) {
        tracingLogPacks.value = targets
    }

    override fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>> {
        return tracingLogPacks
    }

    override suspend fun setDownloadToGalleryEnabled(enabled: Boolean) {
        downloadToGalleryEnabled.value = enabled
    }

    override fun isDownloadToGalleryEnabledFlow(): Flow<Boolean> {
        return downloadToGalleryEnabled
    }

    override suspend fun setUrlPreviewEnabled(enabled: Boolean) {
        urlPreviewEnabled.value = enabled
    }

    override fun isUrlPreviewEnabledFlow(): Flow<Boolean> {
        return urlPreviewEnabled
    }

    override suspend fun setInRoomMessageSoundEnabled(enabled: Boolean) {
        inRoomMessageSoundEnabled.value = enabled
    }

    override fun isInRoomMessageSoundEnabledFlow(): Flow<Boolean> {
        return inRoomMessageSoundEnabled
    }

    override suspend fun setInRoomMessageSound(sound: NotificationSound) {
        inRoomMessageSound.value = sound
    }

    override fun getInRoomMessageSoundFlow(): Flow<NotificationSound> {
        return inRoomMessageSound
    }

    override fun getMessageSoundFlow(): Flow<NotificationSound> {
        return messageSound
    }

    override suspend fun setMessageSoundAndIncrementVersion(sound: NotificationSound, title: String?): Int {
        messageSound.value = sound
        messageSoundDisplayName.value = if (sound is NotificationSound.Custom && !title.isNullOrBlank()) title else null
        return messageSoundChannelVersion.updateAndGet { it + 1 }
    }

    override fun getMessageSoundDisplayNameFlow(): Flow<String?> {
        return messageSoundDisplayName
    }

    override fun getCallRingtoneFlow(): Flow<NotificationSound> {
        return callRingtone
    }

    override suspend fun setCallRingtoneAndIncrementVersion(sound: NotificationSound, title: String?): Int {
        callRingtone.value = sound
        callRingtoneDisplayName.value = if (sound is NotificationSound.Custom && !title.isNullOrBlank()) title else null
        return callRingtoneChannelVersion.updateAndGet { it + 1 }
    }

    override fun getCallRingtoneDisplayNameFlow(): Flow<String?> {
        return callRingtoneDisplayName
    }

    override suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig {
        return NotificationSoundChannelConfig(
            messageSound = messageSound.value,
            messageSoundVersion = messageSoundChannelVersion.value,
            messageSoundDisplayName = messageSoundDisplayName.value,
            callRingtone = callRingtone.value,
            callRingtoneVersion = callRingtoneChannelVersion.value,
            callRingtoneDisplayName = callRingtoneDisplayName.value,
        )
    }

    override suspend fun reset() {
        // No op
    }
}
