/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.DropdownOption
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import kotlinx.collections.immutable.ImmutableList

data class AdvancedSettingsState(
    val isDeveloperModeEnabled: Boolean,
    val isSharePresenceEnabled: Boolean,
    val isShowOnlineStatusEnabled: Boolean,
    val isOrganizeChatListsEnabled: Boolean,
    val isKeepOriginalVideoSizeEnabled: Boolean,
    val mediaOptimizationState: MediaOptimizationState?,
    val theme: ThemeOption,
    val availableThemeOptions: ImmutableList<ThemeOption>,
    val mediaPreviewConfigState: MediaPreviewConfigState,
    val liveLocationMinimumDistanceUpdate: Int?,
    val accentColorHex: String?,
    val chatBackgroundColorHex: String?,
    val chatBackgroundImagePath: String?,
    val frostedGlassEnabled: Boolean,
    val headerBarOpacity: Float,
    val composerBarOpacity: Float,
    val bubbleBarOpacity: Float,
    val outgoingBubbleColorHex: String?,
    val incomingBubbleColorHex: String?,
    val lockedChatsAccessCode: String?,
    val downloadToGalleryEnabled: Boolean,
    val urlPreviewEnabled: Boolean,
    val eventSink: (AdvancedSettingsEvents) -> Unit
)

sealed interface MediaOptimizationState {
    data class AllMedia(val isEnabled: Boolean) : MediaOptimizationState
    data class Split(
        val compressImages: Boolean,
        val videoPreset: VideoCompressionPreset,
    ) : MediaOptimizationState

    val shouldCompressImages: Boolean get() = when (this) {
        is AllMedia -> isEnabled
        is Split -> compressImages
    }
}

enum class ThemeOption : DropdownOption {
    System {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.theme_system)
    },

    Light {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.theme_light)
    },

    Dark {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.theme_dark)
    },

    Black {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.theme_black)
    },

    /** Not an actual light/dark scheme: selecting this only toggles the frosted-glass bar overlay on top of whichever scheme was last active. */
    Frosted {
        @Composable
        @ReadOnlyComposable
        override fun getText(): String = stringResource(R.string.theme_frosted)
    }
}
