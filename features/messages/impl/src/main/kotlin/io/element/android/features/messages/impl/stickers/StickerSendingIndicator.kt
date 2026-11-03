/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * A small pill shown while a sticker is being uploaded and sent, and a message if that fails. The sticker tray closes as
 * soon as a sticker is tapped, so this is the only sign that it is on its way.
 */
@Composable
fun StickerSendingIndicator(
    controller: StickerController,
    modifier: Modifier = Modifier,
) {
    val sending by controller.sending.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(controller) {
        controller.failures.collect {
            Toast.makeText(context, "Could not send the sticker", Toast.LENGTH_SHORT).show()
        }
    }
    if (sending > 0) {
        Row(
            modifier = modifier
                .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = ElementTheme.colors.iconSecondary,
            )
            Text(
                text = "Sending sticker…",
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
