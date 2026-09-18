/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.mapToTheme
import io.element.android.libraries.androidutils.file.copyUriToInternalFile
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val CHAT_WALLPAPER_FILE_NAME = "chat_wallpaper.jpg"

@Inject
class AdvancedSettingsPresenter(
    private val appPreferencesStore: AppPreferencesStore,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val mediaPreviewConfigStateStore: MediaPreviewConfigStateStore,
    private val mediaPickerProvider: PickerProvider,
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val featureFlagService: FeatureFlagService,
) : Presenter<AdvancedSettingsState> {
    @Composable
    override fun present(): AdvancedSettingsState {
        val isDeveloperModeEnabled by remember {
            appPreferencesStore.isDeveloperModeEnabledFlow()
        }.collectAsState(initial = false)
        val isSharePresenceEnabled by remember {
            sessionPreferencesStore.isSharePresenceEnabled()
        }.collectAsState(initial = true)
        val isShowOnlineStatusEnabled by remember {
            sessionPreferencesStore.isShowOnlineStatusEnabled()
        }.collectAsState(initial = true)
        val isBlackThemeAllowed by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.AllowBlackTheme)
        }.collectAsState(initial = false)
        val theme = remember(isBlackThemeAllowed) {
            appPreferencesStore.getThemeFlow().mapToTheme(isBlackThemeAllowed)
        }.collectAsState(initial = Theme.System)

        val liveLocationMinimumDistanceUpdate by produceState<Int?>(null) {
            appPreferencesStore.getLiveLocationMinimumDistanceInMetersUpdateFlow().collect { value = it }
        }

        val accentColorHex by remember {
            appPreferencesStore.getAccentColorFlow()
        }.collectAsState(initial = null)

        val chatBackgroundColorHex by remember {
            appPreferencesStore.getChatBackgroundColorFlow()
        }.collectAsState(initial = null)

        val chatBackgroundImagePath by remember {
            appPreferencesStore.getChatBackgroundImagePathFlow()
        }.collectAsState(initial = null)

        val frostedGlassEnabled by remember {
            appPreferencesStore.isFrostedGlassEnabledFlow()
        }.collectAsState(initial = false)

        val headerBarOpacity by remember {
            appPreferencesStore.getHeaderBarOpacityFlow()
        }.collectAsState(initial = 0.3f)

        val composerBarOpacity by remember {
            appPreferencesStore.getComposerBarOpacityFlow()
        }.collectAsState(initial = 0.15f)

        val bubbleBarOpacity by remember {
            appPreferencesStore.getBubbleBarOpacityFlow()
        }.collectAsState(initial = 0.5f)

        val outgoingBubbleColorHex by remember {
            appPreferencesStore.getOutgoingBubbleColorFlow()
        }.collectAsState(initial = null)

        val incomingBubbleColorHex by remember {
            appPreferencesStore.getIncomingBubbleColorFlow()
        }.collectAsState(initial = null)

        val lockedChatsAccessCode by remember {
            sessionPreferencesStore.lockedChatsAccessCode()
        }.collectAsState(initial = null)

        val downloadToGalleryEnabled by remember {
            appPreferencesStore.isDownloadToGalleryEnabledFlow()
        }.collectAsState(initial = false)

        val urlPreviewEnabled by remember {
            appPreferencesStore.isUrlPreviewEnabledFlow()
        }.collectAsState(initial = true)

        val chatBackgroundImagePicker = mediaPickerProvider.registerGalleryImagePicker(
            onResult = { uri ->
                if (uri != null) {
                    sessionCoroutineScope.launch {
                        val copied = withContext(dispatchers.io) {
                            context.copyUriToInternalFile(uri, CHAT_WALLPAPER_FILE_NAME)
                        }
                        if (copied != null) {
                            appPreferencesStore.setChatBackgroundImage(copied.absolutePath)
                            appPreferencesStore.setChatBackgroundColor(null)
                        } else {
                            Timber.w("Failed to copy picked chat wallpaper image")
                        }
                    }
                }
            }
        )

        val mediaPreviewConfigState = mediaPreviewConfigStateStore.state()

        val themeOption by remember {
            derivedStateOf {
                if (frostedGlassEnabled) {
                    ThemeOption.Frosted
                } else {
                    when (theme.value) {
                        Theme.System -> ThemeOption.System
                        Theme.Dark -> ThemeOption.Dark
                        Theme.Black -> ThemeOption.Black
                        Theme.Light -> ThemeOption.Light
                    }
                }
            }
        }

        val hasSplitMediaQualityOptions by produceState<Boolean?>(null) {
            value = featureFlagService.isFeatureEnabled(FeatureFlags.SelectableMediaQuality)
        }

        val availableThemeOptions = remember(isBlackThemeAllowed) {
            if (isBlackThemeAllowed) {
                ThemeOption.entries
            } else {
                ThemeOption.entries.filterNot { it == ThemeOption.Black }
            }.toImmutableList()
        }

        val mediaOptimizationState by produceState<MediaOptimizationState?>(null) {
            val hasSplitMediaQualityOptionsFlow = featureFlagService.isFeatureEnabledFlow(FeatureFlags.SelectableMediaQuality)
            combine(
                hasSplitMediaQualityOptionsFlow,
                sessionPreferencesStore.doesOptimizeImages(),
                sessionPreferencesStore.getVideoCompressionPreset()
            ) { hasSplitOptions, compressImages, videoPreset ->
                if (hasSplitMediaQualityOptions == true) {
                    value = MediaOptimizationState.Split(
                        compressImages = compressImages,
                        videoPreset = videoPreset,
                    )
                } else if (hasSplitMediaQualityOptions == false) {
                    value = MediaOptimizationState.AllMedia(isEnabled = compressImages)
                }
            }.collect()
        }

        fun handleEvent(event: AdvancedSettingsEvents) {
            when (event) {
                is AdvancedSettingsEvents.SetDeveloperModeEnabled -> sessionCoroutineScope.launch {
                    appPreferencesStore.setDeveloperModeEnabled(event.enabled)
                }
                is AdvancedSettingsEvents.SetSharePresenceEnabled -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setSharePresence(event.enabled)
                }
                is AdvancedSettingsEvents.SetShowOnlineStatusEnabled -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setShowOnlineStatus(event.enabled)
                }
                is AdvancedSettingsEvents.SetCompressMedia -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setOptimizeImages(event.compress)
                }
                is AdvancedSettingsEvents.SetTheme -> sessionCoroutineScope.launch {
                    when (event.theme) {
                        ThemeOption.System -> {
                            appPreferencesStore.setFrostedGlassEnabled(false)
                            appPreferencesStore.setTheme(Theme.System.name)
                        }
                        ThemeOption.Dark -> {
                            appPreferencesStore.setFrostedGlassEnabled(false)
                            appPreferencesStore.setTheme(Theme.Dark.name)
                        }
                        ThemeOption.Black -> {
                            appPreferencesStore.setFrostedGlassEnabled(false)
                            appPreferencesStore.setTheme(Theme.Black.name)
                        }
                        ThemeOption.Light -> {
                            appPreferencesStore.setFrostedGlassEnabled(false)
                            appPreferencesStore.setTheme(Theme.Light.name)
                        }
                        // Frosted isn't a light/dark scheme: keep whichever scheme is already active and just
                        // switch the header/composer bars to the blurred, translucent overlay.
                        ThemeOption.Frosted -> appPreferencesStore.setFrostedGlassEnabled(true)
                    }
                }
                is AdvancedSettingsEvents.SetHideInviteAvatars -> mediaPreviewConfigStateStore.setHideInviteAvatars(event.value)
                is AdvancedSettingsEvents.SetTimelineMediaPreviewValue -> mediaPreviewConfigStateStore.setTimelineMediaPreviewValue(event.value)
                is AdvancedSettingsEvents.SetLiveLocationMinimumDistanceUpdate -> sessionCoroutineScope.launch {
                    appPreferencesStore.setLiveLocationMinimumDistanceInMetersUpdate(event.value)
                }
                is AdvancedSettingsEvents.SetCompressImages -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setOptimizeImages(event.compress)
                }
                is AdvancedSettingsEvents.SetVideoUploadQuality -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setVideoCompressionPreset(event.videoPreset)
                }
                is AdvancedSettingsEvents.SetAccentColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setAccentColor(event.colorHex)
                }
                is AdvancedSettingsEvents.SetChatBackgroundColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatBackgroundColor(event.colorHex)
                    appPreferencesStore.setChatBackgroundImage(null)
                }
                AdvancedSettingsEvents.PickChatBackgroundImage -> chatBackgroundImagePicker.launch()
                AdvancedSettingsEvents.ClearChatBackgroundImage -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatBackgroundImage(null)
                }
                is AdvancedSettingsEvents.SetFrostedGlassEnabled -> sessionCoroutineScope.launch {
                    appPreferencesStore.setFrostedGlassEnabled(event.enabled)
                }
                is AdvancedSettingsEvents.SetHeaderBarOpacity -> sessionCoroutineScope.launch {
                    appPreferencesStore.setHeaderBarOpacity(event.opacity)
                }
                is AdvancedSettingsEvents.SetComposerBarOpacity -> sessionCoroutineScope.launch {
                    appPreferencesStore.setComposerBarOpacity(event.opacity)
                }
                is AdvancedSettingsEvents.SetBubbleBarOpacity -> sessionCoroutineScope.launch {
                    appPreferencesStore.setBubbleBarOpacity(event.opacity)
                }
                is AdvancedSettingsEvents.SetOutgoingBubbleColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setOutgoingBubbleColor(event.colorHex)
                }
                is AdvancedSettingsEvents.SetIncomingBubbleColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setIncomingBubbleColor(event.colorHex)
                }
                is AdvancedSettingsEvents.SetLockedChatsAccessCode -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setLockedChatsAccessCode(event.code)
                }
                is AdvancedSettingsEvents.SetDownloadToGalleryEnabled -> sessionCoroutineScope.launch {
                    appPreferencesStore.setDownloadToGalleryEnabled(event.enabled)
                }
                is AdvancedSettingsEvents.SetUrlPreviewEnabled -> sessionCoroutineScope.launch {
                    appPreferencesStore.setUrlPreviewEnabled(event.enabled)
                }
            }
        }

        return AdvancedSettingsState(
            isDeveloperModeEnabled = isDeveloperModeEnabled,
            isSharePresenceEnabled = isSharePresenceEnabled,
            isShowOnlineStatusEnabled = isShowOnlineStatusEnabled,
            mediaOptimizationState = mediaOptimizationState,
            theme = themeOption,
            availableThemeOptions = availableThemeOptions,
            mediaPreviewConfigState = mediaPreviewConfigState,
            liveLocationMinimumDistanceUpdate = liveLocationMinimumDistanceUpdate,
            accentColorHex = accentColorHex,
            chatBackgroundColorHex = chatBackgroundColorHex,
            chatBackgroundImagePath = chatBackgroundImagePath,
            frostedGlassEnabled = frostedGlassEnabled,
            headerBarOpacity = headerBarOpacity,
            composerBarOpacity = composerBarOpacity,
            bubbleBarOpacity = bubbleBarOpacity,
            outgoingBubbleColorHex = outgoingBubbleColorHex,
            incomingBubbleColorHex = incomingBubbleColorHex,
            lockedChatsAccessCode = lockedChatsAccessCode,
            downloadToGalleryEnabled = downloadToGalleryEnabled,
            urlPreviewEnabled = urlPreviewEnabled,
            eventSink = ::handleEvent,
        )
    }
}
