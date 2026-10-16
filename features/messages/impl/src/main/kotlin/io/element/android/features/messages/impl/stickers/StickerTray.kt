/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_PICKED_STICKERS = 30

/** Bottom sheet with the user's own stickers: tap to send, long-press to remove, first tile adds new ones. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StickerTray(
    controller: StickerController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stickers by controller.stickers.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var fileToRemove by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_STICKERS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch { controller.add(uris) }
        }
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        scrollable = false,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Stickers",
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = message ?: "Tap to send. Long-press to remove.",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier.heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElementTheme.colors.bgSubtleSecondary)
                            .combinedClickable(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            style = ElementTheme.typography.fontHeadingXlRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                }
                items(stickers, key = { it.name }) { file ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    coroutineScope.launch {
                                        controller.send(file)
                                            .onSuccess { onDismiss() }
                                            .onFailure { message = "Could not send the sticker" }
                                    }
                                },
                                onLongClick = { fileToRemove = file },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Sticker",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    fileToRemove?.let { file ->
        io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog(
            title = "Remove sticker?",
            content = "This removes it from your sticker tray only.",
            submitText = "Remove",
            onSubmitClick = {
                coroutineScope.launch { controller.remove(file) }
                fileToRemove = null
            },
            onDismiss = { fileToRemove = null },
        )
    }
}
