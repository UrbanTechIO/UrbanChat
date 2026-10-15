/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** A small floating glass capsule that bends [backdrop], used for the Liquid Glass header. */
internal fun Modifier.liquidGlassCapsule(backdrop: Backdrop): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { CircleShape },
    effects = {
        vibrancy()
        blur(8f.dp.toPx())
        lens(10f.dp.toPx(), 20f.dp.toPx())
    },
    onDrawSurface = {
        drawRect(Color(0xFF0B1220).copy(alpha = 0.10f))
        drawRect(Color.White.copy(alpha = 0.08f))
    },
)

/** Wraps [content] in a glass capsule when [backdrop] is set, and shows it as is otherwise. */
@Composable
internal fun LiquidGlassWrap(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    if (backdrop == null) {
        content()
    } else {
        Box(modifier = modifier.liquidGlassCapsule(backdrop).padding(contentPadding)) {
            content()
        }
    }
}
