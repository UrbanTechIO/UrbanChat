/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.impl.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSound.Companion.toStored
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val developerModeKey = booleanPreferencesKey("developerMode")
private val customElementCallBaseUrlKey = stringPreferencesKey("elementCallBaseUrl")
private val themeKey = stringPreferencesKey("theme")
private val accentColorKey = stringPreferencesKey("accentColor")
private val chatBackgroundColorKey = stringPreferencesKey("chatBackgroundColor")
private val chatBackgroundImagePathKey = stringPreferencesKey("chatBackgroundImagePath")
private val frostedGlassEnabledKey = booleanPreferencesKey("frostedGlassEnabled")
private val headerBarOpacityKey = floatPreferencesKey("headerBarOpacity")
private val composerBarOpacityKey = floatPreferencesKey("composerBarOpacity")
private val bubbleBarOpacityKey = floatPreferencesKey("bubbleBarOpacity")
private val outgoingBubbleColorKey = stringPreferencesKey("outgoingBubbleColor")
private val incomingBubbleColorKey = stringPreferencesKey("incomingBubbleColor")
private val hideInviteAvatarsKey = booleanPreferencesKey("hideInviteAvatars")
private val timelineMediaPreviewValueKey = stringPreferencesKey("timelineMediaPreviewValue")
private val liveLocationMinimumDistanceUpdateKey = intPreferencesKey("liveLocationMinimumDistanceUpdate")
private val logLevelKey = stringPreferencesKey("logLevel")
private val traceLogPacksKey = stringPreferencesKey("traceLogPacks")
private val downloadToGalleryEnabledKey = booleanPreferencesKey("downloadToGalleryEnabled")
private val urlPreviewEnabledKey = booleanPreferencesKey("urlPreviewEnabled")
private val inRoomMessageSoundEnabledKey = booleanPreferencesKey("inRoomMessageSoundEnabled")
private val inRoomMessageSoundUriKey = stringPreferencesKey("inRoomMessageSoundUri")
private val messageSoundUriKey = stringPreferencesKey("notificationMessageSoundUri")
private val messageSoundChannelVersionKey = intPreferencesKey("notificationMessageSoundChannelVersion")
private val messageSoundDisplayNameKey = stringPreferencesKey("notificationMessageSoundDisplayName")
private val callRingtoneUriKey = stringPreferencesKey("notificationCallRingtoneUri")
private val callRingtoneChannelVersionKey = intPreferencesKey("notificationCallRingtoneChannelVersion")
private val callRingtoneDisplayNameKey = stringPreferencesKey("notificationCallRingtoneDisplayName")

@ContributesBinding(AppScope::class)
class DefaultAppPreferencesStore(
    private val buildMeta: BuildMeta,
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : AppPreferencesStore {
    private val store = preferenceDataStoreFactory.create("elementx_preferences")

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[developerModeKey] = enabled
        }
    }

    override fun isDeveloperModeEnabledFlow(): Flow<Boolean> {
        return store.data.map { prefs ->
            // disabled by default on release and nightly, enabled by default on debug
            prefs[developerModeKey] ?: (buildMeta.buildType == BuildType.DEBUG)
        }
    }

    override suspend fun setCustomElementCallBaseUrl(string: String?) {
        store.edit { prefs ->
            if (string != null) {
                prefs[customElementCallBaseUrlKey] = string
            } else {
                prefs.remove(customElementCallBaseUrlKey)
            }
        }
    }

    override fun getCustomElementCallBaseUrlFlow(): Flow<String?> {
        return store.data.map { prefs ->
            prefs[customElementCallBaseUrlKey]
        }
    }

    override suspend fun setTheme(theme: String) {
        store.edit { prefs ->
            prefs[themeKey] = theme
        }
    }

    override fun getThemeFlow(): Flow<String?> {
        return store.data.map { prefs ->
            prefs[themeKey]
        }
    }

    override suspend fun setAccentColor(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex != null) {
                prefs[accentColorKey] = colorHex
            } else {
                prefs.remove(accentColorKey)
            }
        }
    }

    override fun getAccentColorFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[accentColorKey] }
    }

    override suspend fun setChatBackgroundColor(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex != null) {
                prefs[chatBackgroundColorKey] = colorHex
            } else {
                prefs.remove(chatBackgroundColorKey)
            }
        }
    }

    override fun getChatBackgroundColorFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[chatBackgroundColorKey] }
    }

    override suspend fun setChatBackgroundImage(path: String?) {
        store.edit { prefs ->
            if (path != null) {
                prefs[chatBackgroundImagePathKey] = path
            } else {
                prefs.remove(chatBackgroundImagePathKey)
            }
        }
    }

    override fun getChatBackgroundImagePathFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[chatBackgroundImagePathKey] }
    }

    override suspend fun setFrostedGlassEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[frostedGlassEnabledKey] = enabled }
    }

    override fun isFrostedGlassEnabledFlow(): Flow<Boolean> {
        return store.data.map { prefs -> prefs[frostedGlassEnabledKey] ?: false }
    }

    override suspend fun setHeaderBarOpacity(opacity: Float) {
        store.edit { prefs -> prefs[headerBarOpacityKey] = opacity }
    }

    override fun getHeaderBarOpacityFlow(): Flow<Float> {
        return store.data.map { prefs -> prefs[headerBarOpacityKey] ?: 0.3f }
    }

    override suspend fun setComposerBarOpacity(opacity: Float) {
        store.edit { prefs -> prefs[composerBarOpacityKey] = opacity }
    }

    override fun getComposerBarOpacityFlow(): Flow<Float> {
        return store.data.map { prefs -> prefs[composerBarOpacityKey] ?: 0.15f }
    }

    override suspend fun setBubbleBarOpacity(opacity: Float) {
        store.edit { prefs -> prefs[bubbleBarOpacityKey] = opacity }
    }

    override fun getBubbleBarOpacityFlow(): Flow<Float> {
        return store.data.map { prefs -> prefs[bubbleBarOpacityKey] ?: 0.5f }
    }

    override suspend fun setOutgoingBubbleColor(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex != null) {
                prefs[outgoingBubbleColorKey] = colorHex
            } else {
                prefs.remove(outgoingBubbleColorKey)
            }
        }
    }

    override fun getOutgoingBubbleColorFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[outgoingBubbleColorKey] }
    }

    override suspend fun setIncomingBubbleColor(colorHex: String?) {
        store.edit { prefs ->
            if (colorHex != null) {
                prefs[incomingBubbleColorKey] = colorHex
            } else {
                prefs.remove(incomingBubbleColorKey)
            }
        }
    }

    override fun getIncomingBubbleColorFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[incomingBubbleColorKey] }
    }

    override suspend fun setLiveLocationMinimumDistanceInMetersUpdate(value: Int) {
        store.edit { prefs ->
            prefs[liveLocationMinimumDistanceUpdateKey] = value
        }
    }

    override fun getLiveLocationMinimumDistanceInMetersUpdateFlow(): Flow<Int> {
        return store.data.map { prefs ->
            prefs[liveLocationMinimumDistanceUpdateKey] ?: 10
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getHideInviteAvatarsFlow(): Flow<Boolean?> {
        return store.data.map { prefs ->
            prefs[hideInviteAvatarsKey]
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setHideInviteAvatars(hide: Boolean?) {
        store.edit { prefs ->
            if (hide != null) {
                prefs[hideInviteAvatarsKey] = hide
            } else {
                prefs.remove(hideInviteAvatarsKey)
            }
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?) {
        store.edit { prefs ->
            if (mediaPreviewValue != null) {
                prefs[timelineMediaPreviewValueKey] = mediaPreviewValue.name
            } else {
                prefs.remove(timelineMediaPreviewValueKey)
            }
        }
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?> {
        return store.data.map { prefs ->
            prefs[timelineMediaPreviewValueKey]?.let { MediaPreviewValue.valueOf(it) }
        }
    }

    override suspend fun setTracingLogLevel(logLevel: LogLevel) {
        store.edit { prefs ->
            prefs[logLevelKey] = logLevel.name
        }
    }

    override fun getTracingLogLevelFlow(): Flow<LogLevel> {
        return store.data.map { prefs ->
            prefs[logLevelKey]?.let { LogLevel.valueOf(it) } ?: buildMeta.defaultLogLevel()
        }
    }

    override suspend fun setTracingLogPacks(targets: Set<TraceLogPack>) {
        val value = targets.joinToString(",") { it.key }
        store.edit { prefs ->
            prefs[traceLogPacksKey] = value
        }
    }

    override fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>> {
        return store.data.map { prefs ->
            prefs[traceLogPacksKey]
                ?.split(",")
                ?.mapNotNull { value -> TraceLogPack.entries.find { it.key == value } }
                ?.toSet()
                ?: emptySet()
        }
    }

    override suspend fun setDownloadToGalleryEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[downloadToGalleryEnabledKey] = enabled }
    }

    override fun isDownloadToGalleryEnabledFlow(): Flow<Boolean> {
        return store.data.map { prefs -> prefs[downloadToGalleryEnabledKey] ?: false }
    }

    override suspend fun setUrlPreviewEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[urlPreviewEnabledKey] = enabled }
    }

    override fun isUrlPreviewEnabledFlow(): Flow<Boolean> {
        return store.data.map { prefs -> prefs[urlPreviewEnabledKey] ?: true }
    }

    override suspend fun setInRoomMessageSoundEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[inRoomMessageSoundEnabledKey] = enabled }
    }

    override fun isInRoomMessageSoundEnabledFlow(): Flow<Boolean> {
        return store.data.map { prefs -> prefs[inRoomMessageSoundEnabledKey] ?: false }
    }

    override suspend fun setInRoomMessageSound(sound: NotificationSound) {
        store.edit { prefs ->
            val stored = sound.toStored()
            if (stored != null) {
                prefs[inRoomMessageSoundUriKey] = stored
            } else {
                prefs.remove(inRoomMessageSoundUriKey)
            }
        }
    }

    override fun getInRoomMessageSoundFlow(): Flow<NotificationSound> {
        return store.data.map { prefs -> NotificationSound.fromStored(prefs[inRoomMessageSoundUriKey]) }
    }

    override fun getMessageSoundFlow(): Flow<NotificationSound> {
        return store.data.map { prefs -> NotificationSound.fromStored(prefs[messageSoundUriKey]) }
    }

    override suspend fun setMessageSoundAndIncrementVersion(sound: NotificationSound, title: String?): Int {
        var newVersion = 0
        store.edit { prefs ->
            val stored = sound.toStored()
            if (stored != null) {
                prefs[messageSoundUriKey] = stored
            } else {
                prefs.remove(messageSoundUriKey)
            }
            // Clear title on non-Custom so the picker doesn't show a stale label after a revert.
            if (sound is NotificationSound.Custom && !title.isNullOrBlank()) {
                prefs[messageSoundDisplayNameKey] = title
            } else {
                prefs.remove(messageSoundDisplayNameKey)
            }
            newVersion = (prefs[messageSoundChannelVersionKey] ?: 0) + 1
            prefs[messageSoundChannelVersionKey] = newVersion
        }
        return newVersion
    }

    override fun getMessageSoundDisplayNameFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[messageSoundDisplayNameKey] }
    }

    override fun getCallRingtoneFlow(): Flow<NotificationSound> {
        return store.data.map { prefs -> NotificationSound.fromStored(prefs[callRingtoneUriKey]) }
    }

    override suspend fun setCallRingtoneAndIncrementVersion(sound: NotificationSound, title: String?): Int {
        var newVersion = 0
        store.edit { prefs ->
            val stored = sound.toStored()
            if (stored != null) {
                prefs[callRingtoneUriKey] = stored
            } else {
                prefs.remove(callRingtoneUriKey)
            }
            if (sound is NotificationSound.Custom && !title.isNullOrBlank()) {
                prefs[callRingtoneDisplayNameKey] = title
            } else {
                prefs.remove(callRingtoneDisplayNameKey)
            }
            newVersion = (prefs[callRingtoneChannelVersionKey] ?: 0) + 1
            prefs[callRingtoneChannelVersionKey] = newVersion
        }
        return newVersion
    }

    override fun getCallRingtoneDisplayNameFlow(): Flow<String?> {
        return store.data.map { prefs -> prefs[callRingtoneDisplayNameKey] }
    }

    override suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig {
        val prefs = store.data.first()
        return NotificationSoundChannelConfig(
            messageSound = NotificationSound.fromStored(prefs[messageSoundUriKey]),
            messageSoundVersion = prefs[messageSoundChannelVersionKey] ?: 0,
            messageSoundDisplayName = prefs[messageSoundDisplayNameKey],
            callRingtone = NotificationSound.fromStored(prefs[callRingtoneUriKey]),
            callRingtoneVersion = prefs[callRingtoneChannelVersionKey] ?: 0,
            callRingtoneDisplayName = prefs[callRingtoneDisplayNameKey],
        )
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }
}

private fun BuildMeta.defaultLogLevel(): LogLevel {
    return when (buildType) {
        BuildType.DEBUG -> LogLevel.TRACE
        BuildType.NIGHTLY -> LogLevel.DEBUG
        BuildType.RELEASE -> LogLevel.INFO
    }
}
