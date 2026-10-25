/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The built-in chat backgrounds for [io.element.android.libraries.urbanextras.look.ChatLooks.BACKGROUND_PRESETS]. */
fun presetBackgroundBrush(name: String): Brush = when (name) {
    "aurora" -> Brush.linearGradient(listOf(Color(0xFF0B1F3A), Color(0xFF1B6E6B), Color(0xFF5B3FA0)))
    "sunset" -> Brush.linearGradient(listOf(Color(0xFF2B1055), Color(0xFFD1456B), Color(0xFFF7A64B)))
    "ocean" -> Brush.linearGradient(listOf(Color(0xFF021B3A), Color(0xFF0B5E8C), Color(0xFF3CC1D6)))
    "forest" -> Brush.linearGradient(listOf(Color(0xFF07211A), Color(0xFF1F6B45), Color(0xFF9BC53D)))
    "midnight" -> Brush.linearGradient(listOf(Color(0xFF05060F), Color(0xFF16213E), Color(0xFF3A3F8F)))
    "rose" -> Brush.linearGradient(listOf(Color(0xFF3B0D2E), Color(0xFFB4457B), Color(0xFFF4B6C2)))
    else -> Brush.linearGradient(listOf(Color.Black, Color.DarkGray))
}

fun presetBackgroundLabel(name: String): String = name.replaceFirstChar { it.uppercase() }
