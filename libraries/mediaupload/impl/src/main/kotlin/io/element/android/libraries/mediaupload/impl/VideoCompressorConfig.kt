/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.element.android.libraries.androidutils.media.VideoCompressorHelper
import io.element.android.libraries.mediaupload.api.compressorHelper
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import kotlin.math.min

@OptIn(UnstableApi::class)
internal object VideoCompressorConfigFactory {
    private const val DEFAULT_FRAME_RATE = 30

    /** Audio is re-encoded as AAC at roughly this bitrate; it counts against the size budget. */
    private const val AUDIO_BITRATE = 128_000L

    /** Leaves room for container overhead and for VBR encoders overshooting the requested bitrate. */
    private const val SIZE_BUDGET_FACTOR = 0.85

    /** Below this the picture is unusable, so we stop shrinking (the upload may then still be too large). */
    private const val MIN_VIDEO_BITRATE = 60_000L

    fun create(
        metadata: VideoFileMetadata?,
        preset: VideoCompressionPreset,
        /** If set, the output is sized to fit under this many bytes, whatever the length of the video. */
        maxOutputBytes: Long? = null,
        /** Scales [maxOutputBytes] down further, used to retry when a first attempt still came out too large. */
        budgetScale: Double = 1.0,
    ): VideoCompressorConfig {
        val width = metadata?.width?.takeIf { it >= 0 } ?: Int.MAX_VALUE
        val height = metadata?.height?.takeIf { it >= 0 } ?: Int.MAX_VALUE
        val originalFrameRate = metadata?.frameRate?.takeIf { it >= 0 } ?: DEFAULT_FRAME_RATE

        // If we are resizing, we also want to reduce the frame rate to the default value (30fps)
        val newFrameRate = min(originalFrameRate, DEFAULT_FRAME_RATE)

        val videoBitrateBudget = videoBitrateBudget(maxOutputBytes, metadata?.durationMs, budgetScale)

        var resizer = preset.compressorHelper()
        // If we need to resize the video, we also want to recalculate the bitrate
        var newBitrate = resizer.calculateOptimalBitrate(Size(width, height), newFrameRate).toLong()

        if (videoBitrateBudget != null && newBitrate > videoBitrateBudget) {
            // Too big for the limit at this quality: step down to the next smaller preset(s) until the
            // natural bitrate fits...
            val smallerPresets = VideoCompressionPreset.entries.filter { it.ordinal > preset.ordinal }
            val fitting = smallerPresets.firstNotNullOfOrNull { candidate ->
                val candidateResizer = candidate.compressorHelper()
                val candidateBitrate = candidateResizer.calculateOptimalBitrate(Size(width, height), newFrameRate).toLong()
                (candidateResizer to candidateBitrate).takeIf { candidateBitrate <= videoBitrateBudget }
            }
            if (fitting != null) {
                resizer = fitting.first
                newBitrate = fitting.second
            } else {
                // ...and if even the smallest preset is too big (a very long video), keep its resolution and
                // squeeze the bitrate down to the budget instead, so the upload still fits.
                resizer = VideoCompressionPreset.LOW.compressorHelper()
                newBitrate = videoBitrateBudget.coerceAtLeast(MIN_VIDEO_BITRATE)
            }
        }

        return VideoCompressorConfig(
            videoCompressorHelper = resizer,
            newBitRate = newBitrate.toInt(),
            newFrameRate = newFrameRate,
        )
    }

    /** The video bitrate (bits/s) that keeps a video of [durationMs] under [maxOutputBytes], or null if unknown. */
    private fun videoBitrateBudget(maxOutputBytes: Long?, durationMs: Long?, budgetScale: Double): Long? {
        if (maxOutputBytes == null || maxOutputBytes <= 0 || durationMs == null || durationMs <= 0) return null
        val durationSeconds = durationMs / 1000.0
        val totalBitrate = (maxOutputBytes * SIZE_BUDGET_FACTOR * budgetScale * 8) / durationSeconds
        return (totalBitrate - AUDIO_BITRATE).toLong()
    }
}

@OptIn(UnstableApi::class)
internal data class VideoCompressorConfig(
    val videoCompressorHelper: VideoCompressorHelper,
    val newBitRate: Int,
    val newFrameRate: Int,
)
