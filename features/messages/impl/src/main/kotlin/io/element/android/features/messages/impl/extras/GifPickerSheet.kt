/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.extras

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The GIF tab of the sticker sheet: search Giphy for GIFs or for its transparent stickers and send one. Searches go to
 * Giphy; nothing else leaves the phone.
 */
@Composable
fun GiphyPanel(
    controller: ExtrasController,
    onGifChosen: (Uri) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stickers by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(query, stickers) {
        if (query.isNotBlank()) delay(400)
        results = controller.searchGifs(query, stickers)
        failed = results.isEmpty()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(label = "GIFs", selected = !stickers) { stickers = false }
            ModeChip(label = "Stickers", selected = stickers) { stickers = true }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(CommonStrings.urban_gif_search_hint),
            singleLine = true,
        )
        if (failed) {
            Text(
                text = stringResource(CommonStrings.urban_gif_none),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 380.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(results, key = { it.id }) { gif ->
                GifThumbnail(controller, gif) {
                    if (!sending) {
                        sending = true
                        coroutineScope.launch {
                            val uri = controller.downloadGif(gif)
                            sending = false
                            if (uri != null) {
                                onGifChosen(uri)
                                onDone()
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "Powered by GIPHY",
            style = ElementTheme.typography.fontBodyXsRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = if (selected) ElementTheme.colors.textOnSolidPrimary else ElementTheme.colors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun GifThumbnail(controller: ExtrasController, gif: GifItem, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, gif.id) {
        value = controller.loadPreview(gif)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
        }
    }
}
