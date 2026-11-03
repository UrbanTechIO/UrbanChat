/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import androidx.compose.ui.res.stringResource

/**
 * The counterpart of [ReplySwipeIndicator] on the right-hand side: shown when swiping a message to the left, to say
 * that releasing there selects the message.
 *
 * @param swipeProgress the progress of the swipe, between 0 and X. When swipeProgress >= 1 the swipe will be detected.
 */
@Composable
fun RowScope.SelectSwipeIndicator(
    swipeProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .weight(1f)
            .align(Alignment.CenterVertically)
            .padding(end = 12.dp)
            .graphicsLayer {
                translationX = 36.dp.toPx() * (1f - swipeProgress().coerceAtMost(1f))
                alpha = swipeProgress()
            },
        horizontalAlignment = Alignment.End,
    ) {
        Icon(
            contentDescription = null,
            imageVector = CompoundIcons.CheckCircle(),
            tint = ElementTheme.colors.iconAccentPrimary,
        )
        Text(
            text = stringResource(CommonStrings.urban_action_select),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
        )
    }
}
