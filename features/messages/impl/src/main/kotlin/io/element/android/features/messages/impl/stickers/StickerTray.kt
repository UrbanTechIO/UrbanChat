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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_PICKED_STICKERS = 30
private const val PACK_THUMBNAIL_SIZE = 256L
private const val TAB_ICON_SIZE = 128L
private const val MINE_KEY = "mine"

/**
 * Bottom sheet for stickers, laid out like WhatsApp: a row of tabs on top, each tab showing the first sticker of its
 * source as its icon, and the selected tab showing all the stickers of that source. The first source is "My stickers"
 * (images kept on this phone); the others are online packs added by link. Long-press a tab to move it or remove a pack.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StickerTray(
    controller: StickerController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stickers by controller.stickers.collectAsState()
    val packs by controller.packStore.packs.collectAsState()
    val savedOrder by controller.packStore.order.collectAsState()
    val loadingPacks by controller.packStore.loading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedKey by remember { mutableStateOf(MINE_KEY) }
    var message by remember { mutableStateOf<String?>(null) }
    var fileToRemove by remember { mutableStateOf<File?>(null) }
    var packToRemove by remember { mutableStateOf<StickerPack?>(null) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var showAddPack by remember { mutableStateOf(false) }
    var packAddress by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        controller.refresh()
        controller.packStore.refresh()
    }

    // The tabs in the user's order. New sources go at the end; sources that are gone are dropped.
    val packsByKey = packs.associateBy { StickerPackStore.packKey(it) }
    val baseKeys = listOf(MINE_KEY) + packsByKey.keys
    val orderedKeys = savedOrder.filter { it in baseKeys } + baseKeys.filter { it !in savedOrder }
    val activeKey = if (selectedKey in orderedKeys) selectedKey else MINE_KEY

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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItemsIndexed(orderedKeys, key = { _, key -> key }) { index, key ->
                    val pack = packsByKey[key]
                    Box {
                        StickerTab(
                            selected = key == activeKey,
                            onClick = {
                                selectedKey = key
                                message = null
                            },
                            onLongClick = { menuKey = key },
                        ) {
                            if (pack != null) {
                                AsyncImage(
                                    model = MediaRequestData(
                                        source = MediaSource(pack.stickers.first().url),
                                        kind = MediaRequestData.Kind.Thumbnail(TAB_ICON_SIZE),
                                    ),
                                    contentDescription = pack.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(36.dp),
                                )
                            } else if (stickers.isNotEmpty()) {
                                AsyncImage(
                                    model = stickers.first(),
                                    contentDescription = "My stickers",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(36.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = CompoundIcons.Favourite(),
                                    contentDescription = "My stickers",
                                    tint = ElementTheme.colors.iconSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        DropdownMenu(expanded = menuKey == key, onDismissRequest = { menuKey = null }) {
                            DropdownMenuItem(
                                text = { Text("Move left") },
                                enabled = index > 0,
                                onClick = {
                                    menuKey = null
                                    controller.packStore.setOrder(orderedKeys.moved(index, index - 1))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move right") },
                                enabled = index < orderedKeys.lastIndex,
                                onClick = {
                                    menuKey = null
                                    controller.packStore.setOrder(orderedKeys.moved(index, index + 1))
                                },
                            )
                            if (pack != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove pack") },
                                    onClick = {
                                        menuKey = null
                                        packToRemove = pack
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "add") {
                    StickerTab(selected = showAddPack, onClick = { showAddPack = !showAddPack }, onLongClick = null) {
                        Text(
                            text = "+",
                            style = ElementTheme.typography.fontHeadingXlRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                }
            }

            if (showAddPack) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = packAddress,
                        onValueChange = { packAddress = it },
                        placeholder = "Link of a sticker pack or index.json",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Add",
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = ElementTheme.colors.textActionAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = {
                                val address = packAddress
                                packAddress = ""
                                showAddPack = false
                                coroutineScope.launch {
                                    controller.packStore.add(address)
                                        .onSuccess { count -> message = if (count == 1) "Pack added" else "$count packs added" }
                                        .onFailure { message = it.message ?: "Could not add the pack" }
                                }
                            })
                            .padding(8.dp),
                    )
                }
            }

            val hint = message ?: when {
                activeKey == MINE_KEY -> "My stickers. Tap to send, long-press to remove."
                else -> "${packsByKey[activeKey]?.title.orEmpty()}. Tap to send."
            }
            Text(
                text = hint,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            if (loadingPacks && packs.isEmpty() && savedOrder.isNotEmpty()) {
                Text("Loading packs…", color = ElementTheme.colors.textSecondary, style = ElementTheme.typography.fontBodyMdRegular)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier.heightIn(max = 380.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val activePack = packsByKey[activeKey]
                if (activePack == null) {
                    item(key = "add-image") {
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
                    itemsIndexed(stickers, key = { _, file -> file.name }) { _, file ->
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
                } else {
                    itemsIndexed(activePack.stickers, key = { index, sticker -> "$index|${sticker.url}" }) { _, sticker ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        coroutineScope.launch {
                                            controller.sendPackSticker(sticker)
                                                .onSuccess { onDismiss() }
                                                .onFailure { message = "Could not send the sticker" }
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = MediaRequestData(
                                    source = MediaSource(sticker.url),
                                    kind = MediaRequestData.Kind.Thumbnail(PACK_THUMBNAIL_SIZE),
                                ),
                                contentDescription = sticker.body,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    fileToRemove?.let { file ->
        ConfirmationDialog(
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

    packToRemove?.let { pack ->
        ConfirmationDialog(
            title = "Remove pack?",
            content = "\"${pack.title}\" is removed from this phone. You can add it again by its link.",
            submitText = "Remove",
            onSubmitClick = {
                coroutineScope.launch { controller.packStore.remove(pack) }
                selectedKey = MINE_KEY
                message = null
                packToRemove = null
            },
            onDismiss = { packToRemove = null },
        )
    }
}

/** One tab of the sticker sheet: a square with the source's icon, highlighted when selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTab(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ElementTheme.colors.bgSubtleSecondary else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun List<String>.moved(from: Int, to: Int): List<String> {
    if (to !in indices) return this
    return toMutableList().also { list -> list.add(to, list.removeAt(from)) }
}
