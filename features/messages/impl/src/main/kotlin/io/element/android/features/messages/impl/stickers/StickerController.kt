/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A local, per-device tray of the user's own stickers. Images are copied into the app's private storage;
 * sending uploads the image and posts a real Matrix `m.sticker` event to the room.
 *
 * In encrypted rooms the image is encrypted on the phone before it is uploaded, like any attachment.
 */
private const val UPLOAD_CACHE_MILLIS = 30L * 24 * 60 * 60 * 1000

class StickerController(
    private val context: Context,
    private val client: MatrixClient,
    private val room: JoinedRoom,
) {
    /** Online sticker packs added by link; their stickers are public images referenced by their mxc address. */
    val packStore = StickerPackStore(context)

    // Sends outlive the tray: the tray closes as soon as a sticker is tapped, and the upload goes on in the background.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sending = MutableStateFlow(0)

    /** How many stickers are being uploaded and sent right now. */
    val sending: StateFlow<Int> = _sending
    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once for each sticker that could not be sent. */
    val failures: SharedFlow<Unit> = _failures

    // What each sticker file was uploaded as, so a sticker that was sent before is sent again without uploading it again.
    private val uploads = context.getSharedPreferences("sticker_uploads", Context.MODE_PRIVATE)

    private val directory = File(context.filesDir, "stickers")
    private val _stickers = MutableStateFlow(loadStickers())
    val stickers: StateFlow<List<File>> = _stickers

    private fun loadStickers(): List<File> {
        return directory.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    /** Re-reads the sticker folder, for stickers saved elsewhere (for example lifted out of a photo). */
    fun refresh() {
        _stickers.value = loadStickers()
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

    /** Sends [file] without waiting for it: see [sending] and [failures]. */
    fun sendInBackground(file: File) {
        _sending.update { it + 1 }
        scope.launch {
            try {
                send(file).onFailure { _failures.tryEmit(Unit) }
            } finally {
                _sending.update { it - 1 }
            }
        }
    }

    private fun uploadKey(file: File, encrypted: Boolean) = "${client.sessionId.value}|${file.name}|${file.length()}|${file.lastModified()}|$encrypted"

    private fun cachedUpload(file: File, encrypted: Boolean): JSONObject? = runCatching {
        val json = JSONObject(uploads.getString(uploadKey(file, encrypted), null) ?: return@runCatching null)
        json.takeIf { System.currentTimeMillis() - it.getLong("at") < UPLOAD_CACHE_MILLIS }
    }.getOrNull()

    private fun rememberUpload(file: File, encrypted: Boolean, upload: JSONObject) {
        upload.put("at", System.currentTimeMillis())
        uploads.edit().putString(uploadKey(file, encrypted), upload.toString()).apply()
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
            val info = JSONObject()
                .put("mimetype", mimeType)
                .put("size", bytes.size)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                info.put("w", bounds.outWidth).put("h", bounds.outHeight)
            }
            val content = JSONObject()
                .put("body", "Sticker")
                .put("info", info)
            val encrypted = room.roomInfoFlow.value.isEncrypted == true
            val earlier = cachedUpload(file, encrypted)
            if (earlier != null) {
                // Sent before: point the new event at the copy that is already on the server.
                earlier.optJSONObject("file")?.let { content.put("file", it) }
                earlier.optString("url").takeIf { it.isNotEmpty() }?.let { content.put("url", it) }
            } else if (encrypted) {
                // Encrypted room: encrypt the image on the phone (AES-256-CTR) before uploading, so the server only ever
                // stores ciphertext, and send the key inside the (already end-to-end encrypted) event.
                val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
                // The Matrix spec wants the second half of the IV (the counter) to start at zero.
                val iv = ByteArray(16)
                SecureRandom().nextBytes(iv)
                for (i in 8 until 16) iv[i] = 0
                val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                }
                val encryptedBytes = cipher.doFinal(bytes)
                val hash = MessageDigest.getInstance("SHA-256").digest(encryptedBytes)
                val mxcUrl = client.uploadMedia("application/octet-stream", encryptedBytes).getOrThrow()
                val unpadded = Base64.NO_PADDING or Base64.NO_WRAP
                val fileInfo = JSONObject()
                    .put("url", mxcUrl)
                    .put(
                        "key",
                        JSONObject()
                            .put("kty", "oct")
                            .put("key_ops", JSONArray().put("encrypt").put("decrypt"))
                            .put("alg", "A256CTR")
                            .put("k", Base64.encodeToString(key, unpadded or Base64.URL_SAFE))
                            .put("ext", true)
                    )
                    .put("iv", Base64.encodeToString(iv, unpadded))
                    .put("hashes", JSONObject().put("sha256", Base64.encodeToString(hash, unpadded)))
                    .put("v", "v2")
                content.put("file", fileInfo)
                rememberUpload(file, encrypted = true, JSONObject().put("file", fileInfo))
            } else {
                val mxcUrl = client.uploadMedia(mimeType, bytes).getOrThrow()
                content.put("url", mxcUrl)
                rememberUpload(file, encrypted = false, JSONObject().put("url", mxcUrl))
            }
            room.sendRaw("m.sticker", content.toString()).getOrThrow()
        }
    }

    /**
     * Sends a sticker from an online pack. Pack stickers are public images, so the event just points at their mxc
     * address (as Matrix sticker pickers do); the event itself is still end-to-end encrypted in encrypted rooms.
     */
    suspend fun sendPackSticker(sticker: PackSticker): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val content = JSONObject()
                .put("body", sticker.body)
                .put("url", sticker.url)
            sticker.info?.let { content.put("info", JSONObject(it)) }
            room.sendRaw("m.sticker", content.toString()).getOrThrow()
        }
    }
}
