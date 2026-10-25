/*
 * UrbanChat extras: press and hold an object in a photo, watch it lift out, release and save it as a sticker.
 */

package io.element.android.libraries.urbanextras.ui

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color as ComposeColor
import io.element.android.libraries.urbanextras.sticker.LiftedSubject
import io.element.android.libraries.urbanextras.sticker.StickerMaker
import io.element.android.libraries.urbanextras.sticker.SubjectSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

private sealed interface LiftPhase {
    data object Idle : LiftPhase
    data class Loading(val at: Offset) : LiftPhase
    data class Lifted(val subject: LiftedSubject, val rect: Rect) : LiftPhase
}

/**
 * Wraps a photo. Press and hold on an object in it: the object lifts out of the picture, and when you let go you can save it as a sticker.
 * Everything else about the photo (zooming, swiping, tapping) keeps working, because only a long press that stays still is taken over.
 */
@Composable
fun StickerLiftContainer(
    uri: Uri?,
    enabled: Boolean,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var phase by remember(uri) { mutableStateOf<LiftPhase>(LiftPhase.Idle) }
    var fingerDown by remember { mutableStateOf(false) }
    var subjectSet by remember(uri) { mutableStateOf<SubjectSet?>(null) }

    fun lift(position: Offset) {
        val source = uri ?: return
        phase = LiftPhase.Loading(position)
        scope.launch {
            val set = subjectSet ?: StickerMaker.findSubjects(context, source).getOrElse {
                onMessage("Could not find an object: ${it.message}")
                phase = LiftPhase.Idle
                return@launch
            }.also { subjectSet = it }
            val container = size
            val fit = fitRect(container, set.width, set.height)
            val hit = pickSubject(set, fit, position)
            if (hit == null) {
                onMessage("No object found there")
                phase = LiftPhase.Idle
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val scale = fit.width / set.width
                phase = LiftPhase.Lifted(
                    subject = hit,
                    rect = Rect(
                        offset = Offset(fit.left + hit.startX * scale, fit.top + hit.startY * scale),
                        size = Size(hit.bitmap.width * scale, hit.bitmap.height * scale),
                    ),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(uri, enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!enabled || uri == null || phase !is LiftPhase.Idle) return@awaitEachGesture
                    // A long press that stays put; anything else is left to the viewer (zoom, swipe, tap).
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var result = "waiting"
                        while (result == "waiting") {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.size > 1) {
                                result = "multi"
                            } else if (!change.pressed) {
                                result = "up"
                            } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                result = "moved"
                            }
                        }
                        result
                    }
                    if (outcome == null) {
                        fingerDown = true
                        lift(down.position)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        fingerDown = false
                    }
                }
            },
    ) {
        content()
        when (val current = phase) {
            LiftPhase.Idle -> Unit
            is LiftPhase.Loading -> LoadingRing(current.at)
            is LiftPhase.Lifted -> {
                LiftedObject(current)
                if (!fingerDown) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ActionPill("Save as sticker", primary = true) {
                            val subject = current.subject
                            phase = LiftPhase.Idle
                            scope.launch {
                                StickerMaker.saveAsSticker(context, subject).fold(
                                    onSuccess = { onMessage("Sticker saved. Find it in the sticker tray, My stickers.") },
                                    onFailure = { onMessage("Could not save the sticker: ${it.message}") },
                                )
                            }
                        }
                        ActionPill("Cancel", primary = false) { phase = LiftPhase.Idle }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) ComposeColor(0xFF2E7DF6) else ComposeColor(0xCC202020))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = ComposeColor.White)
    }
}

@Composable
private fun LoadingRing(at: Offset) {
    val pulse by rememberInfiniteTransition(label = "liftPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700)),
        label = "liftPulseValue",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(Color.White, radius = 28f + 60f * pulse, center = at, alpha = 0.7f * (1f - pulse), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
        drawCircle(Color.White, radius = 14f, center = at, alpha = 0.8f)
    }
}

@Composable
private fun LiftedObject(lifted: LiftPhase.Lifted) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )
    val image = remember(lifted.subject) { lifted.subject.bitmap.asImageBitmap() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Dim everything else.
        drawRect(Color.Black, alpha = 0.55f * progress.coerceIn(0f, 1f))
        val grow = 1f + 0.06f * progress
        val rect = lifted.rect
        val w = rect.width * grow
        val h = rect.height * grow
        val left = rect.center.x - w / 2f
        val top = rect.center.y - h / 2f - 10f * progress
        // Soft shadow under the object.
        listOf(18f to 0.10f, 12f to 0.14f, 6f to 0.2f).forEach { (offset, alpha) ->
            drawImage(
                image = image,
                dstOffset = IntOffset(left.toInt(), (top + offset).toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = alpha * progress,
                colorFilter = ColorFilter.tint(Color.Black),
            )
        }
        // White glow around the edge.
        val glow = 7f * progress
        for (i in 0 until 8) {
            val angle = i * (Math.PI / 4).toFloat()
            drawImage(
                image = image,
                dstOffset = IntOffset((left + kotlin.math.cos(angle) * glow).toInt(), (top + kotlin.math.sin(angle) * glow).toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = 0.9f * progress,
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        drawImage(
            image = image,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
        )
    }
}

private fun fitRect(container: IntSize, width: Int, height: Int): Rect {
    val scale = min(container.width / width.toFloat(), container.height / height.toFloat())
    val w = width * scale
    val h = height * scale
    return Rect(Offset((container.width - w) / 2f, (container.height - h) / 2f), Size(w, h))
}

/** The object under [position] (screen position), or the one whose box contains it, or null. */
private fun pickSubject(set: SubjectSet, fit: Rect, position: Offset): LiftedSubject? {
    val scale = fit.width / set.width
    val x = ((position.x - fit.left) / scale).toInt()
    val y = ((position.y - fit.top) / scale).toInt()
    set.subjects.forEach { subject ->
        val localX = x - subject.startX
        val localY = y - subject.startY
        if (localX in 0 until subject.bitmap.width && localY in 0 until subject.bitmap.height) {
            if (android.graphics.Color.alpha(subject.bitmap.getPixel(localX, localY)) > 24) return subject
        }
    }
    return set.subjects.firstOrNull { subject ->
        x in subject.startX until subject.startX + subject.bitmap.width && y in subject.startY until subject.startY + subject.bitmap.height
    }
}
