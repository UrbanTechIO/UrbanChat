/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import io.element.android.compound.tokens.generated.SemanticColors

/**
 * A small WhatsApp-style preset palette for the user-selectable accent (theme) colour.
 * `null`/[Default] keeps the app's original Compound accent tokens untouched.
 */
enum class AccentColorPreset(val color: Color?) {
    Default(null),
    Teal(Color(0xFF00A884)),
    Green(Color(0xFF34C759)),
    Blue(Color(0xFF0A84FF)),
    Indigo(Color(0xFF5856D6)),
    Purple(Color(0xFFAF52DE)),
    Pink(Color(0xFFFF2D55)),
    Red(Color(0xFFFF3B30)),
    Orange(Color(0xFFFF9500)),
}

fun Color.toColorHex(): String = "%08X".format(toArgb())

fun String.hexToColorOrNull(): Color? = runCatching { Color(android.graphics.Color.parseColor("#$this")) }.getOrNull()

/**
 * Overrides the handful of Compound tokens that carry the app's accent colour (primary actions,
 * links, focus ring, selection) with [accent], deriving hover/pressed/subtle variants from it.
 * Structural tokens (backgrounds, body text, borders) are left untouched.
 */
fun SemanticColors.withAccent(accent: Color): SemanticColors {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return copy(
        bgAccentRest = accent,
        bgAccentHovered = accent.darken(0.9f),
        bgAccentPressed = accent.darken(0.8f),
        bgAccentSelected = accent,
        bgAccentSubtle = accent.copy(alpha = 0.12f),
        bgActionPrimaryRest = accent,
        bgActionPrimaryHovered = accent.darken(0.9f),
        bgActionPrimaryPressed = accent.darken(0.8f),
        borderAccentPrimary = accent,
        borderAccentSubtle = accent.copy(alpha = 0.4f),
        borderFocused = accent,
        iconAccentPrimary = accent,
        iconAccentTertiary = accent,
        iconOnSolidPrimary = onAccent,
        textActionAccent = accent,
        // Recolors the "subtle" gradient (used behind the room list's top bar, and now its page
        // background too) from Compound's fixed brand green to the user's chosen accent. Plain
        // alpha fade-out works the same over both light and dark canvases, unlike the upstream
        // tokens which use differently-shaded opaque colors per theme for the same visual effect.
        gradientSubtleStop1 = accent.copy(alpha = 0.65f),
        gradientSubtleStop2 = accent.copy(alpha = 0.50f),
        gradientSubtleStop3 = accent.copy(alpha = 0.35f),
        gradientSubtleStop4 = accent.copy(alpha = 0.22f),
        gradientSubtleStop5 = accent.copy(alpha = 0.10f),
        gradientSubtleStop6 = accent.copy(alpha = 0f),
    )
}

private fun Color.darken(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

/** A small WhatsApp-style preset palette for the chat/timeline background. */
enum class ChatBackgroundPreset(val color: Color?) {
    Default(null),
    Sand(Color(0xFFECE5DD)),
    Sage(Color(0xFFD9EAD3)),
    SkyBlue(Color(0xFFD6E9F5)),
    Lavender(Color(0xFFE6E0F8)),
    Blush(Color(0xFFF8E1E7)),
    Charcoal(Color(0xFF0B141A)),
}

/** A small WhatsApp-style preset palette for a message bubble's background, used for both the outgoing and incoming pickers. */
enum class BubbleColorPreset(val color: Color?) {
    Default(null),
    Teal(Color(0xFF00A884)),
    Green(Color(0xFF34C759)),
    Blue(Color(0xFF0A84FF)),
    Indigo(Color(0xFF5856D6)),
    Purple(Color(0xFFAF52DE)),
    Pink(Color(0xFFFF2D55)),
    Sand(Color(0xFFECE5DD)),
    Charcoal(Color(0xFF2A2F32)),
}
