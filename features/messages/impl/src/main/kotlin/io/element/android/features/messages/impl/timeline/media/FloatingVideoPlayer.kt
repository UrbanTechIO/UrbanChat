/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.media

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.roundToInt

/** A video ready to be shown in the floating mini-player. */
data class FloatingVideoData(
    val uri: Uri,
    val mimeType: String,
)

fun interface FloatingVideoPlayerController {
    /** Opens the floating mini-player on [uri] (a local file/content uri, already downloaded). */
    fun show(uri: Uri, mimeType: String)
}

val LocalFloatingVideoPlayerController = compositionLocalOf<FloatingVideoPlayerController?> { null }

// Nearly full screen width, with a 16:9 height computed from it — a small side margin so the
// close button and drag handle stay comfortably reachable at the screen edges.
private const val FLOATING_PLAYER_WIDTH_FRACTION = 0.92f
private const val FLOATING_PLAYER_ASPECT_RATIO = 16f / 9f

/**
 * A small draggable video player that floats above the timeline so the user can keep
 * reading/scrolling the chat while a video keeps playing. Tapping it expands to a full,
 * system-level playback experience (the same one used elsewhere in this app for a downloaded
 * video); the close button dismisses it entirely.
 */
@OptIn(UnstableApi::class)
@Composable
fun FloatingVideoPlayerOverlay(
    data: FloatingVideoData?,
    onDismiss: () -> Unit,
) {
    if (data == null) return
    val context = LocalContext.current

    fun expandToFullScreen() {
        runCatchingExceptions {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(data.uri, data.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
        onDismiss()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val playerWidth = maxWidth * FLOATING_PLAYER_WIDTH_FRACTION
        val playerHeight = playerWidth / FLOATING_PLAYER_ASPECT_RATIO
        val maxOffsetXPx = with(density) { (maxWidth - playerWidth).toPx() }
        val maxOffsetYPx = with(density) { (maxHeight - playerHeight).toPx() }
        var offset by remember(data) {
            mutableStateOf(Offset(x = maxOffsetXPx.coerceAtLeast(0f) * 0.5f, y = maxOffsetYPx.coerceAtLeast(0f) * 0.15f))
        }

        val exoPlayer = remember(data) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(data.uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(width = playerWidth, height = playerHeight)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .pointerInput(data) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            x = (offset.x + dragAmount.x).coerceIn(0f, maxOffsetXPx.coerceAtLeast(0f)),
                            y = (offset.y + dragAmount.y).coerceIn(0f, maxOffsetYPx.coerceAtLeast(0f)),
                        )
                    }
                }
                .pointerInput(data) {
                    detectTapGestures { expandToFullScreen() }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(context).apply {
                        useController = false
                        player = exoPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
            )
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(CommonStrings.action_close),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 8.dp))
                    .pointerInput(data) {
                        detectTapGestures { onDismiss() }
                    },
            )
        }
    }
}
