/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.async

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * A round badge with a ring that fills as a download progresses, and an optional [label] (for example "12/85 MB")
 * inside it. When [fraction] is null (the size or the first bytes are not known yet) the ring just spins.
 */
@Composable
fun DownloadProgressRing(
    fraction: Float?,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 56.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        val ringModifier = Modifier.size(size - 12.dp)
        if (fraction != null) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = ringModifier,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeWidth = 3.dp,
            )
        } else {
            CircularProgressIndicator(
                modifier = ringModifier,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeWidth = 3.dp,
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = ElementTheme.typography.fontBodyXsMedium,
                color = Color.White,
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        }
    }
}
