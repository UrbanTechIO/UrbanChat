/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.colorpicker.ColorPickerDialog
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.dialogs.TextFieldDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferenceDropdown
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.ElementPreviewBlack
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.stringWithLink
import io.element.android.libraries.designsystem.theme.AccentColorPreset
import io.element.android.libraries.designsystem.theme.BubbleColorPreset
import io.element.android.libraries.designsystem.theme.ChatBackgroundPreset
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ListSupportingTextDefaults
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.hexToColorOrNull
import io.element.android.libraries.designsystem.theme.toColorHex
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.analytics.compose.LocalAnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import kotlin.math.roundToInt

@Composable
fun AdvancedSettingsView(
    state: AdvancedSettingsState,
    onBackClick: () -> Unit,
    onOpenAppSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analyticsService = LocalAnalyticsService.current

    val snackbarDispatcher = LocalSnackbarDispatcher.current
    val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = snackbarMessage)

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_advanced_settings),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) {
        PreferenceDropdown(
            title = stringResource(id = CommonStrings.common_appearance),
            selectedOption = state.theme,
            options = state.availableThemeOptions,
            onSelectOption = { themeOption ->
                state.eventSink(AdvancedSettingsEvents.SetTheme(themeOption))
            }
        )
        if (state.theme == ThemeOption.Frosted) {
            FrostedGlassOpacitySliders(
                headerBarOpacity = state.headerBarOpacity,
                composerBarOpacity = state.composerBarOpacity,
                bubbleBarOpacity = state.bubbleBarOpacity,
                onHeaderBarOpacityChange = { state.eventSink(AdvancedSettingsEvents.SetHeaderBarOpacity(it)) },
                onComposerBarOpacityChange = { state.eventSink(AdvancedSettingsEvents.SetComposerBarOpacity(it)) },
                onBubbleBarOpacityChange = { state.eventSink(AdvancedSettingsEvents.SetBubbleBarOpacity(it)) },
            )
        }
        ListItem(
            content = {
                Text(text = stringResource(id = CommonStrings.action_view_source))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_view_source_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isDeveloperModeEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetDeveloperModeEnabled(!state.isDeveloperModeEnabled)) }
        )
        ListItem(
            content = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isSharePresenceEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetSharePresenceEnabled(!state.isSharePresenceEnabled)) }
        )
        ListItem(
            content = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_organize_chats))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_organize_chats_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isOrganizeChatListsEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetOrganizeChatListsEnabled(!state.isOrganizeChatListsEnabled)) }
        )
        ListItem(
            content = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_show_online_status))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_show_online_status_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.isShowOnlineStatusEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetShowOnlineStatusEnabled(!state.isShowOnlineStatusEnabled)) }
        )
        val compressImages = state.mediaOptimizationState?.shouldCompressImages

        when (state.mediaOptimizationState) {
            null -> Unit
            is MediaOptimizationState.AllMedia -> {
                ListItem(
                    content = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_description))
                    },
                    trailingContent = ListItemContent.Switch(
                        checked = compressImages ?: false,
                    ),
                    onClick = {
                        val newValue = !(compressImages ?: false)
                        analyticsService.captureInteraction(
                            if (newValue) {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                            } else {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                            }
                        )
                        state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                    }
                )
            }
            is MediaOptimizationState.Split -> {
                ListItem(
                    content = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_title))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_description))
                    },
                    trailingContent = ListItemContent.Switch(
                        checked = compressImages ?: false,
                    ),
                    onClick = {
                        val newValue = !(compressImages ?: false)
                        analyticsService.captureInteraction(
                            if (newValue) {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                            } else {
                                Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                            }
                        )
                        state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                    }
                )

                var displaySelectorDialog by remember { mutableStateOf(false) }

                ListItem(
                    content = {
                        Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_title))
                    },
                    supportingContent = {
                        val description = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_description)
                        val quality = when (state.mediaOptimizationState.videoPreset) {
                            VideoCompressionPreset.LOW -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                            VideoCompressionPreset.STANDARD -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                            VideoCompressionPreset.HIGH -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                        }
                        val descriptionWithValue = remember(quality) {
                            String.format(description, quality)
                        }
                        Text(text = descriptionWithValue)
                    },
                    onClick = { displaySelectorDialog = true },
                )

                if (displaySelectorDialog) {
                    VideoQualitySelectorDialog(
                        selectedPreset = state.mediaOptimizationState.videoPreset,
                        onSubmit = { preset ->
                            state.eventSink(AdvancedSettingsEvents.SetVideoUploadQuality(preset))
                            displaySelectorDialog = false
                        },
                        onDismiss = { displaySelectorDialog = false },
                    )
                }
            }
        }
        ListItem(
            content = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_download_to_gallery_title))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_download_to_gallery_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.downloadToGalleryEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetDownloadToGalleryEnabled(!state.downloadToGalleryEnabled)) }
        )
        ListItem(
            content = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_disable_link_previews_title))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.screen_advanced_settings_disable_link_previews_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = !state.urlPreviewEnabled,
            ),
            onClick = { state.eventSink(AdvancedSettingsEvents.SetUrlPreviewEnabled(!state.urlPreviewEnabled)) }
        )

        ModerationAndSafety(state)
        if (state.liveLocationMinimumDistanceUpdate != null) {
            LiveLocationUpdatesSection(
                value = state.liveLocationMinimumDistanceUpdate,
                onSaveValue = { value ->
                    state.eventSink(AdvancedSettingsEvents.SetLiveLocationMinimumDistanceUpdate(value))
                },
                onOpenAppPermissionsClick = onOpenAppSettingsClick,
            )
        }
        CollapsibleColoursSection(state)
        LockedChatsAccessCodePreference(
            accessCode = state.lockedChatsAccessCode,
            onAccessCodeSet = { code -> state.eventSink(AdvancedSettingsEvents.SetLockedChatsAccessCode(code)) },
        )
    }
}

@Composable
private fun FrostedGlassOpacitySliders(
    headerBarOpacity: Float,
    composerBarOpacity: Float,
    bubbleBarOpacity: Float,
    onHeaderBarOpacityChange: (Float) -> Unit,
    onComposerBarOpacityChange: (Float) -> Unit,
    onBubbleBarOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        OpacitySlider(
            title = stringResource(R.string.screen_advanced_settings_header_bar_opacity),
            value = headerBarOpacity,
            onValueChange = onHeaderBarOpacityChange,
        )
        OpacitySlider(
            title = stringResource(R.string.screen_advanced_settings_composer_bar_opacity),
            value = composerBarOpacity,
            onValueChange = onComposerBarOpacityChange,
        )
        OpacitySlider(
            title = stringResource(R.string.screen_advanced_settings_bubble_bar_opacity),
            value = bubbleBarOpacity,
            onValueChange = onBubbleBarOpacityChange,
        )
    }
}

@Composable
private fun OpacitySlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinish = { onValueChange(sliderValue) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = ElementTheme.colors.iconAccentPrimary,
                activeTrackColor = ElementTheme.colors.iconAccentPrimary,
                inactiveTrackColor = ElementTheme.colors.bgBadgeAccent,
                inactiveTickColor = ElementTheme.colors.iconAccentPrimary,
            ),
        )
    }
}

@Composable
private fun CollapsibleColoursSection(
    state: AdvancedSettingsState,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        ListItem(
            content = { Text(stringResource(R.string.screen_advanced_settings_colours_section_title)) },
            trailingContent = ListItemContent.Icon(
                iconSource = IconSource.Vector(if (expanded) CompoundIcons.ChevronUp() else CompoundIcons.ChevronDown()),
            ),
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                ColorSwatchPreference(
                    title = stringResource(R.string.screen_advanced_settings_accent_color),
                    presets = AccentColorPreset.entries,
                    presetColor = { it.color },
                    selectedColorHex = state.accentColorHex,
                    onColorSelected = { hex -> state.eventSink(AdvancedSettingsEvents.SetAccentColor(hex)) },
                )
                ColorSwatchPreference(
                    title = stringResource(R.string.screen_advanced_settings_chat_background_color),
                    presets = ChatBackgroundPreset.entries,
                    presetColor = { it.color },
                    selectedColorHex = state.chatBackgroundColorHex,
                    onColorSelected = { hex -> state.eventSink(AdvancedSettingsEvents.SetChatBackgroundColor(hex)) },
                )
                ChatBackgroundImagePreference(
                    imagePath = state.chatBackgroundImagePath,
                    onPickImage = { state.eventSink(AdvancedSettingsEvents.PickChatBackgroundImage) },
                    onClearImage = { state.eventSink(AdvancedSettingsEvents.ClearChatBackgroundImage) },
                )
                ColorSwatchPreference(
                    title = stringResource(R.string.screen_advanced_settings_outgoing_bubble_color),
                    presets = BubbleColorPreset.entries,
                    presetColor = { it.color },
                    selectedColorHex = state.outgoingBubbleColorHex,
                    onColorSelected = { hex -> state.eventSink(AdvancedSettingsEvents.SetOutgoingBubbleColor(hex)) },
                )
                ColorSwatchPreference(
                    title = stringResource(R.string.screen_advanced_settings_incoming_bubble_color),
                    presets = BubbleColorPreset.entries,
                    presetColor = { it.color },
                    selectedColorHex = state.incomingBubbleColorHex,
                    onColorSelected = { hex -> state.eventSink(AdvancedSettingsEvents.SetIncomingBubbleColor(hex)) },
                )
            }
        }
    }
}

private sealed interface AccessCodeDialogMode {
    data object None : AccessCodeDialogMode
    data object Verify : AccessCodeDialogMode
    data object Edit : AccessCodeDialogMode
}

@Composable
private fun LockedChatsAccessCodePreference(
    accessCode: String?,
    onAccessCodeSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogMode by remember { mutableStateOf<AccessCodeDialogMode>(AccessCodeDialogMode.None) }
    val snackbarDispatcher = LocalSnackbarDispatcher.current

    when (dialogMode) {
        AccessCodeDialogMode.None -> Unit
        // The current code is never shown in plain text; entering it correctly is required before it can be changed.
        AccessCodeDialogMode.Verify -> {
            TextFieldDialog(
                title = stringResource(R.string.screen_advanced_settings_locked_chats_access_code),
                content = stringResource(R.string.screen_advanced_settings_locked_chats_access_code_verify_description),
                value = "",
                placeholder = stringResource(R.string.screen_advanced_settings_locked_chats_access_code_placeholder),
                onSubmit = { entered ->
                    if (entered == accessCode) {
                        dialogMode = AccessCodeDialogMode.Edit
                    } else {
                        snackbarDispatcher.post(SnackbarMessage(R.string.screen_advanced_settings_locked_chats_access_code_wrong))
                        dialogMode = AccessCodeDialogMode.None
                    }
                },
                onDismissRequest = { dialogMode = AccessCodeDialogMode.None },
            )
        }
        AccessCodeDialogMode.Edit -> {
            TextFieldDialog(
                title = stringResource(R.string.screen_advanced_settings_locked_chats_access_code),
                content = stringResource(R.string.screen_advanced_settings_locked_chats_access_code_description),
                value = "",
                placeholder = stringResource(R.string.screen_advanced_settings_locked_chats_access_code_placeholder),
                onSubmit = { code ->
                    onAccessCodeSet(code)
                    dialogMode = AccessCodeDialogMode.None
                },
                onDismissRequest = { dialogMode = AccessCodeDialogMode.None },
            )
        }
    }
    ListItem(
        modifier = modifier,
        content = {
            Text(text = stringResource(R.string.screen_advanced_settings_locked_chats_access_code))
        },
        supportingContent = {
            Text(text = stringResource(R.string.screen_advanced_settings_locked_chats_access_code_description))
        },
        onClick = {
            dialogMode = if (accessCode.isNullOrBlank()) AccessCodeDialogMode.Edit else AccessCodeDialogMode.Verify
        },
    )
}

/**
 * WhatsApp-style row of tappable colour swatches, one per [presets] entry. A `null` colour
 * (the [presets] entry with [presetColor] returning null) renders as an outlined circle with a
 * "no colour" slash and represents resetting to the app default.
 */
@Composable
private fun <T> ColorSwatchPreference(
    title: String,
    presets: List<T>,
    presetColor: (T) -> Color?,
    selectedColorHex: String?,
    onColorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val presetHexes = remember(presets) { presets.mapNotNull { presetColor(it)?.toColorHex() } }
    val customColorHex = selectedColorHex.takeIf { it != null && it !in presetHexes }
    if (showColorPicker) {
        ColorPickerDialog(
            title = title,
            initialColor = customColorHex?.hexToColorOrNull() ?: presetColor(presets.first()) ?: Color.Red,
            onColorSelected = { color -> onColorSelected(color.toColorHex()) },
            onDismissRequest = { showColorPicker = false },
        )
    }
    Column(modifier = modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            presets.forEach { preset ->
                val color = presetColor(preset)
                val isSelected = color?.toColorHex() == selectedColorHex || (color == null && selectedColorHex == null)
                ColorSwatch(
                    color = color,
                    isSelected = isSelected,
                    onClick = { onColorSelected(color?.toColorHex()) },
                ) {
                    if (color == null) {
                        Icon(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            val customLabel = stringResource(R.string.screen_advanced_settings_custom_color)
            ColorSwatch(
                color = customColorHex?.hexToColorOrNull(),
                isSelected = customColorHex != null,
                onClick = { showColorPicker = true },
                background = if (customColorHex == null) {
                    Modifier.background(
                        Brush.sweepGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        )
                    )
                } else {
                    Modifier
                },
                contentDescription = customLabel,
            ) {
                if (customColorHex == null) {
                    Icon(
                        imageVector = CompoundIcons.Edit(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .let { if (color != null) it.background(color) else it }
            .then(background)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.borderInteractiveSecondary,
                shape = CircleShape,
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun ChatBackgroundImagePreference(
    imagePath: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(R.string.screen_advanced_settings_chat_background_image),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (imagePath != null) {
                AsyncImage(
                    model = imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, RoundedCornerShape(8.dp)),
                )
            }
            TextButton(
                text = stringResource(R.string.screen_advanced_settings_choose_photo),
                onClick = onPickImage,
            )
            if (imagePath != null) {
                TextButton(
                    text = stringResource(R.string.screen_advanced_settings_remove_photo),
                    destructive = true,
                    onClick = onClearImage,
                )
            }
        }
    }
}

@Composable
private fun VideoQualitySelectorDialog(
    selectedPreset: VideoCompressionPreset,
    onSubmit: (VideoCompressionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val videoPresets = VideoCompressionPreset.entries
    var localSelectedPreset by remember { mutableStateOf(selectedPreset) }
    ListDialog(
        title = stringResource(CommonStrings.dialog_video_quality_selector_title),
        subtitle = stringResource(CommonStrings.dialog_default_video_quality_selector_subtitle),
        onSubmit = { onSubmit(localSelectedPreset) },
        onDismissRequest = onDismiss,
        applyPaddingToContents = false,
    ) {
        for (preset in videoPresets) {
            val isSelected = preset == localSelectedPreset
            item(
                key = preset,
                contentType = preset,
            ) {
                val title = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                    VideoCompressionPreset.STANDARD -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                    VideoCompressionPreset.HIGH -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                }
                val subtitle = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(CommonStrings.common_video_quality_low_description)
                    VideoCompressionPreset.STANDARD -> stringResource(CommonStrings.common_video_quality_standard_description)
                    VideoCompressionPreset.HIGH -> stringResource(CommonStrings.common_video_quality_high_description)
                }
                ListItem(
                    content = {
                        Text(
                            text = title,
                            style = ElementTheme.typography.fontBodyLgMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    },
                    leadingContent = ListItemContent.RadioButton(
                        selected = isSelected,
                    ),
                    onClick = {
                        localSelectedPreset = preset
                    },
                )
            }
        }
    }
}

@Composable
private fun ModerationAndSafety(
    state: AdvancedSettingsState,
    modifier: Modifier = Modifier,
) {
    PreferenceCategory(
        modifier = modifier,
        title = stringResource(R.string.screen_advanced_settings_moderation_and_safety_section_title),
        showTopDivider = true
    ) {
        PreferenceSwitch(
            title = stringResource(R.string.screen_advanced_settings_hide_invite_avatars_toggle_title),
            isChecked = state.mediaPreviewConfigState.hideInviteAvatars,
            onCheckedChange = {
                state.eventSink(AdvancedSettingsEvents.SetHideInviteAvatars(it))
            },
            enabled = !state.mediaPreviewConfigState.setHideInviteAvatarsAction.isLoading()
        )
        ListSectionHeader(
            title = stringResource(R.string.screen_advanced_settings_show_media_timeline_title),
            hasDivider = false,
            description = {
                ListSupportingText(
                    text = stringResource(R.string.screen_advanced_settings_show_media_timeline_subtitle),
                    contentPadding = ListSupportingTextDefaults.Padding.None,
                )
            }
        )
        ListItem(
            content = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_hide)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Off,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Off))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            content = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_private_rooms)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Private,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Private))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            content = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_show)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.On,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.On))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
    }
}

@Composable
private fun LiveLocationUpdatesSection(
    value: Int,
    onSaveValue: (Int) -> Unit,
    onOpenAppPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceCategory(
        modifier = modifier,
        showTopDivider = true,
    ) {
        ListSectionHeader(
            title = stringResource(R.string.screen_advanced_settings_live_location_section_title),
            description = {
                ListSupportingText(
                    text = stringResource(R.string.screen_advanced_settings_live_location_section_description),
                    contentPadding = ListSupportingTextDefaults.Padding.None,
                )
            }
        )
        var sliderValue by remember(value) { mutableIntStateOf(value) }
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.screen_advanced_settings_live_location_update_distance,
                    sliderValue,
                    sliderValue,
                ),
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
            val valueRange = 1f..100f
            val start = valueRange.start.toInt()
            val end = valueRange.endInclusive.toInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${start}m", color = ElementTheme.colors.textSecondary, style = ElementTheme.typography.fontBodyMdRegular)
                Slider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    value = sliderValue.toFloat(),
                    onValueChange = { sliderValue = it.roundToInt() },
                    onValueChangeFinish = {
                        onSaveValue(sliderValue)
                    },
                    valueRange = valueRange,
                    colors = SliderDefaults.colors(
                        thumbColor = ElementTheme.colors.iconAccentPrimary,
                        activeTrackColor = ElementTheme.colors.iconAccentPrimary,
                        inactiveTrackColor = ElementTheme.colors.bgBadgeAccent,
                        inactiveTickColor = ElementTheme.colors.iconAccentPrimary,
                    )
                )
                Text("${end}m", color = ElementTheme.colors.textSecondary, style = ElementTheme.typography.fontBodyMdRegular)
            }
        }
        val footerText = stringWithLink(
            textRes = R.string.screen_advanced_settings_live_location_section_footer,
            url = "",
            linkTextRes = R.string.screen_advanced_settings_live_location_section_footer_link,
            onLinkClick = { onOpenAppPermissionsClick() },
        )
        ListSupportingText(
            annotatedString = footerText,
            contentPadding = ListSupportingTextDefaults.Padding.Default,
        )
    }
}

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewLightPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewLight { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewDarkPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewDark { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun AdvancedSettingsViewBlackPreview(@PreviewParameter(AdvancedSettingsStateProvider::class) state: AdvancedSettingsState) =
    ElementPreviewBlack { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: AdvancedSettingsState) {
    AdvancedSettingsView(
        state = state,
        onBackClick = { },
        onOpenAppSettingsClick = {}
    )
}

@Composable
@PreviewsDayNight
internal fun VideoQualitySelectorDialogPreview() {
    ElementPreview {
        VideoQualitySelectorDialog(
            selectedPreset = VideoCompressionPreset.STANDARD,
            onSubmit = { /* no-op */ },
            onDismiss = { /* no-op */ }
        )
    }
}
