/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import kotlinx.coroutines.flow.Flow

interface AppPreferencesStore {
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    fun isDeveloperModeEnabledFlow(): Flow<Boolean>

    suspend fun setCustomElementCallBaseUrl(string: String?)
    fun getCustomElementCallBaseUrlFlow(): Flow<String?>

    suspend fun setTheme(theme: String)
    fun getThemeFlow(): Flow<String?>

    /** ARGB hex string (e.g. "FF25D366"), or null to use the app default accent. */
    suspend fun setAccentColor(colorHex: String?)
    fun getAccentColorFlow(): Flow<String?>

    /** ARGB hex string (e.g. "FFECE5DD"), or null to use the app default chat background. */
    suspend fun setChatBackgroundColor(colorHex: String?)
    fun getChatBackgroundColorFlow(): Flow<String?>

    /**
     * Absolute path to an app-owned copy of a user-picked wallpaper image, or null for none.
     * Takes precedence over [setChatBackgroundColor] when both are set; setting one clears the other.
     */
    suspend fun setChatBackgroundImage(path: String?)
    fun getChatBackgroundImagePathFlow(): Flow<String?>

    /** Whether the chat header and composer should render as blurred, translucent "frosted glass" bars. */
    suspend fun setFrostedGlassEnabled(enabled: Boolean)
    fun isFrostedGlassEnabledFlow(): Flow<Boolean>

    /** Tint opacity of the frosted header bar, 0f (fully transparent) to 1f (opaque). Blur stays constant either way. */
    suspend fun setHeaderBarOpacity(opacity: Float)
    fun getHeaderBarOpacityFlow(): Flow<Float>

    /** Tint opacity of the frosted composer bar, 0f (fully transparent) to 1f (opaque). Blur stays constant either way. */
    suspend fun setComposerBarOpacity(opacity: Float)
    fun getComposerBarOpacityFlow(): Flow<Float>

    /** Tint opacity of frosted message bubbles, 0f (fully transparent) to 1f (opaque). Blur stays constant either way. */
    suspend fun setBubbleBarOpacity(opacity: Float)
    fun getBubbleBarOpacityFlow(): Flow<Float>

    /** ARGB hex string (e.g. "FF25D366"), or null to use the app default outgoing bubble color. */
    suspend fun setOutgoingBubbleColor(colorHex: String?)
    fun getOutgoingBubbleColorFlow(): Flow<String?>

    /** ARGB hex string (e.g. "FFECE5DD"), or null to use the app default incoming bubble color. */
    suspend fun setIncomingBubbleColor(colorHex: String?)
    fun getIncomingBubbleColorFlow(): Flow<String?>

    suspend fun setLiveLocationMinimumDistanceInMetersUpdate(value: Int)
    fun getLiveLocationMinimumDistanceInMetersUpdateFlow(): Flow<Int>

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    suspend fun setHideInviteAvatars(hide: Boolean?)
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    fun getHideInviteAvatarsFlow(): Flow<Boolean?>
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?)
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?>

    suspend fun setTracingLogLevel(logLevel: LogLevel)
    fun getTracingLogLevelFlow(): Flow<LogLevel>

    suspend fun setTracingLogPacks(targets: Set<TraceLogPack>)
    fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>>

    /**
     * Whether a download button appears on received photos and videos. When off, media streams
     * directly from the server as normal. When on, tapping the button saves it to the device's
     * public Gallery and subsequent playback uses that local copy.
     */
    suspend fun setDownloadToGalleryEnabled(enabled: Boolean)
    fun isDownloadToGalleryEnabledFlow(): Flow<Boolean>

    /** Whether messages containing a link show a preview card (image/title/description) fetched via the homeserver. */
    suspend fun setUrlPreviewEnabled(enabled: Boolean)
    fun isUrlPreviewEnabledFlow(): Flow<Boolean>

    /** Whether an in-app sound plays for a message received while its room is open in the foreground (WhatsApp-style). */
    suspend fun setInRoomMessageSoundEnabled(enabled: Boolean)
    fun isInRoomMessageSoundEnabledFlow(): Flow<Boolean>

    /**
     * Sound used by [setInRoomMessageSoundEnabled]. Unlike [setMessageSoundAndIncrementVersion],
     * a [NotificationSound.Custom] URI here is played directly at trigger time (via this app's own
     * process) rather than copied into a notification channel, so no version/title bookkeeping is
     * needed.
     */
    suspend fun setInRoomMessageSound(sound: NotificationSound)
    fun getInRoomMessageSoundFlow(): Flow<NotificationSound>

    fun getMessageSoundFlow(): Flow<NotificationSound>

    /**
     * Atomically persists [sound] (with copy-time [title] for Custom; cleared otherwise) and
     * bumps the channel version. Single transaction so process death can't desync URI and version.
     */
    suspend fun setMessageSoundAndIncrementVersion(sound: NotificationSound, title: String?): Int

    /** Title captured at copy time. Null for SystemDefault / Silent or pre-title persisted data. */
    fun getMessageSoundDisplayNameFlow(): Flow<String?>

    fun getCallRingtoneFlow(): Flow<NotificationSound>

    /** See [setMessageSoundAndIncrementVersion]. */
    suspend fun setCallRingtoneAndIncrementVersion(sound: NotificationSound, title: String?): Int

    /** See [getMessageSoundDisplayNameFlow]. */
    fun getCallRingtoneDisplayNameFlow(): Flow<String?>

    /** Single-snapshot read of all sound prefs; used at boot to seed channels without N reads. */
    suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig

    suspend fun reset()
}
