/*
 * UrbanChat: messages waiting for their send time, shown at the bottom of the chat like sent messages, with a countdown.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.scheduled.ScheduledMessage
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * The scheduled messages of this chat. Each looks like a sent bubble with a small circle at its top-left corner that counts down to the
 * send time. Press and hold a bubble to cancel it. They sit above the composer and never hold up other messages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledMessageBubbles(
    messages: List<ScheduledMessage>,
    bubbleColor: Color,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val haptics = LocalHapticFeedback.current
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        messages.forEach { message ->
            val remaining = (message.sendAtMillis - now).coerceAtLeast(0)
            val total = (message.sendAtMillis - message.createdAtMillis).coerceAtLeast(1)
            Box(modifier = Modifier.padding(top = 14.dp, start = 12.dp)) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bubbleColor.copy(alpha = 0.55f))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancel(message.id)
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(text = message.body, color = ElementTheme.colors.textPrimary, maxLines = 6)
                    Text(
                        text = "Sends ${format.format(Date(message.sendAtMillis))} · hold to cancel",
                        style = ElementTheme.typography.fontBodyXsRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.End,
                    )
                }
                CountdownCircle(
                    remainingMillis = remaining,
                    fraction = (remaining.toFloat() / total).coerceIn(0f, 1f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-12).dp, y = (-12).dp),
                )
            }
        }
    }
}

@Composable
private fun CountdownCircle(remainingMillis: Long, fraction: Float, modifier: Modifier = Modifier) {
    val accent = ElementTheme.colors.iconAccentPrimary
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgCanvasDefault),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = accent.copy(alpha = 0.25f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = compactTime(remainingMillis),
            style = ElementTheme.typography.fontBodyXsMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

/** 45s, 12m, 3h, 2d: whatever fits in a small circle. */
private fun compactTime(millis: Long): String {
    val seconds = millis / 1_000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        else -> "${seconds / 86_400}d"
    }
}
