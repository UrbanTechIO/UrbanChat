/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A local, per-device tray of the user's own stickers. Images are copied into the app's private storage;
 * sending uploads the image and posts a real Matrix `m.sticker` event to the room.
 *
 * Note: the image is uploaded unencrypted (the `m.sticker` event itself is encrypted in encrypted rooms).
 */
class StickerController(
    private val context: Context,
    private val client: MatrixClient,
    private val room: JoinedRoom,
) {
    private val directory = File(context.filesDir, "stickers")
    private val _stickers = MutableStateFlow(loadStickers())
    val stickers: StateFlow<List<File>> = _stickers

    private fun loadStickers(): List<File> {
        return directory.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    /** Copies the picked images into the tray. Returns how many were added. */
    suspend fun add(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        directory.mkdirs()
        var added = 0
        uris.forEach { uri ->
            val mimeType = context.contentResolver.getType(uri)
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/jpeg" -> "jpg"
                else -> return@forEach
            }
            val target = File(directory, "${UUID.randomUUID()}.$extension")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (copied) added++ else target.delete()
        }
        _stickers.value = loadStickers()
        added
    }

    suspend fun remove(file: File) = withContext(Dispatchers.IO) {
        file.delete()
        _stickers.value = loadStickers()
    }

    suspend fun send(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val bytes = file.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val mxcUrl = client.uploadMedia(mimeType, bytes).getOrThrow()
            val info = JSONObject()
                .put("mimetype", mimeType)
                .put("size", bytes.size)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                info.put("w", bounds.outWidth).put("h", bounds.outHeight)
            }
            val content = JSONObject()
                .put("body", "Sticker")
                .put("url", mxcUrl)
                .put("info", info)
            room.sendRaw("m.sticker", content.toString()).getOrThrow()
        }
    }
}
