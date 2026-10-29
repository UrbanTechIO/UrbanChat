/*
 * UrbanChat extras.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import io.element.android.features.messages.impl.timeline.media.LocalMediaDownloadContext
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_HEIGHT
import io.element.android.libraries.matrix.ui.media.MAX_THUMBNAIL_WIDTH
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import kotlinx.coroutines.delay
import java.io.File

private val NOTE_SIZE = 200.dp

/**
 * A video note: a small circle that loops silently. Tap it to play it once with sound, tap again to go back to the silent loop.
 * Until the clip is on the phone (or if it cannot be fetched) the circle shows the picture of the first frame.
 */
@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
fun VideoNoteBubble(
    content: TimelineItemVideoContent,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator = LocalMediaDownloadContext.current.coordinator
    var file by remember(content.mediaSource) { mutableStateOf<File?>(null) }
    LaunchedEffect(content.mediaSource) {
        // Our own note may still be uploading, so try again a few times.
        repeat(8) {
            val found = coordinator.cachedVideoFile(content.mediaSource, content.mimeType, content.filename)
            if (found != null) {
                file = found
                return@LaunchedEffect
            }
            delay(3_000)
        }
    }

    var withSound by remember(content.mediaSource) { mutableStateOf(false) }
    val player = remember(file) {
        file?.let { f ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // The one play with sound is over: back to the silent loop.
                if (playbackState == Player.STATE_ENDED && withSound) {
                    withSound = false
                }
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }
    LaunchedEffect(withSound, player) {
        val current = player ?: return@LaunchedEffect
        if (withSound) {
            current.repeatMode = Player.REPEAT_MODE_OFF
            current.volume = 1f
            current.seekTo(0)
            current.playWhenReady = true
        } else {
            current.repeatMode = Player.REPEAT_MODE_ALL
            current.volume = 0f
            current.seekTo(0)
            current.playWhenReady = true
        }
    }

    Box(
        modifier = modifier
            .size(NOTE_SIZE)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.2f))
            .combinedClickable(
                onClick = {
                    if (player != null) withSound = !withSound else onContentClick?.invoke()
                },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            modifier = Modifier.size(NOTE_SIZE),
            model = MediaRequestData(
                source = content.thumbnailSource,
                kind = MediaRequestData.Kind.Thumbnail(
                    width = content.thumbnailWidth?.toLong() ?: MAX_THUMBNAIL_WIDTH,
                    height = content.thumbnailHeight?.toLong() ?: MAX_THUMBNAIL_HEIGHT,
                ),
            ),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
        if (player != null) {
            AndroidView(
                modifier = Modifier.size(NOTE_SIZE),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        this.player = player
                    }
                },
                update = { it.player = player },
            )
        }
        val seconds = content.duration.inWholeSeconds.toInt()
        if (seconds > 0) {
            androidx.compose.material3.Text(
                text = "%d:%02d".format(seconds / 60, seconds % 60),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
