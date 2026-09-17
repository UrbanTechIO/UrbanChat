/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import android.graphics.Color as AndroidColor

/**
 * A full-range HSV colour picker: a saturation/value square for the selected hue, a hue slider
 * below it, and a live preview with hex value. Confirms via [onColorSelected], or dismisses
 * without a value via [onDismissRequest].
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var hsv by remember {
        val out = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), out)
        mutableStateOf(out)
    }
    val currentColor = Color(AndroidColor.HSVToColor(hsv))

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElementTheme.colors.bgCanvasDefault,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Spacer(16.dp)
                SaturationValueSquare(
                    hue = hsv[0],
                    saturation = hsv[1],
                    value = hsv[2],
                    onSaturationValueChange = { s, v -> hsv = floatArrayOf(hsv[0], s, v) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Spacer(16.dp)
                HueSlider(
                    hue = hsv[0],
                    onHueChange = { h -> hsv = floatArrayOf(h, hsv[1], hsv[2]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
                Spacer(16.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, CircleShape),
                    )
                    Spacer(12.dp, horizontal = true)
                    Text(
                        text = "#%06X".format(currentColor.toArgb() and 0xFFFFFF),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                Spacer(20.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(CommonStrings.action_cancel),
                        onClick = onDismissRequest,
                    )
                    Spacer(8.dp, horizontal = true)
                    TextButton(
                        text = stringResource(CommonStrings.action_ok),
                        onClick = {
                            onColorSelected(currentColor)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Spacer(size: androidx.compose.ui.unit.Dp, horizontal: Boolean = false) {
    Box(modifier = if (horizontal) Modifier.width(size) else Modifier.height(size))
}

@Composable
private fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInputSaturationValue(onSaturationValueChange),
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val thumb = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = thumb, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = thumb, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun Modifier.pointerInputSaturationValue(
    onChange: (Float, Float) -> Unit,
) = this
    .pointerInputDrag { position, size ->
        val s = (position.x / size.width).coerceIn(0f, 1f)
        val v = 1f - (position.y / size.height).coerceIn(0f, 1f)
        onChange(s, v)
    }

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColors = remember {
        (0..360 step 60).map { Color(AndroidColor.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
    }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .pointerInputDrag { position, size ->
                onHueChange((position.x / size.width).coerceIn(0f, 1f) * 360f)
            },
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val thumbX = (hue / 360f) * size.width
        val radius = size.height / 2f
        drawCircle(
            color = Color.White,
            radius = radius,
            center = Offset(thumbX, radius),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

/**
 * Handles both drag and single-tap as a position update, using the element's own pixel [Offset]
 * and [androidx.compose.ui.unit.IntSize] at callback time.
 */
private fun Modifier.pointerInputDrag(
    onPosition: (Offset, androidx.compose.ui.unit.IntSize) -> Unit,
) = this
    .pointerInput(Unit) {
        detectTapGestures { offset -> onPosition(offset, size) }
    }
    .pointerInput(Unit) {
        detectDragGestures(
            onDrag = { change, _ ->
                change.consume()
                onPosition(change.position, size)
            },
        )
    }
