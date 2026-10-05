/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.miniplayer

sealed interface VoiceMessageMiniPlayerEvents {
    data object PlayPause : VoiceMessageMiniPlayerEvents
    data object Close : VoiceMessageMiniPlayerEvents
}

data class VoiceMessageMiniPlayerState(
    /** Null when nothing is loaded, in which case the mini player is hidden. */
    val mediaId: String?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long?,
    val eventSink: (VoiceMessageMiniPlayerEvents) -> Unit,
) {
    val progress: Float
        get() = durationMs?.takeIf { it > 0 }?.let { (currentPositionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}
