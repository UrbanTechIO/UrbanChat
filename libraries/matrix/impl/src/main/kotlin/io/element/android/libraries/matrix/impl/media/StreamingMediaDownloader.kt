/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.media

import android.util.Base64
import android.webkit.MimeTypeMap
import io.element.android.libraries.matrix.api.media.MediaFile
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val BUFFER_SIZE = 64 * 1024
private const val MAX_CACHE_BYTES = 600L * 1024 * 1024
private const val PROGRESS_INTERVAL_MS = 100L

/**
 * Downloads a media file over the authenticated media endpoint while reporting the real number of bytes received,
 * which the Rust SDK download call cannot do. Encrypted media is decrypted on the fly (AES-CTR) and its SHA-256 is
 * checked, like the SDK does. Callers fall back to the SDK if this throws.
 */
internal class StreamingMediaDownloader(
    private val innerClient: Client,
    private val cacheRoot: File,
) {
    private val cacheDirectory: File
        get() = File(cacheRoot, "stream-cache-v2").apply { if (!exists()) mkdirs() }

    /** A downloaded file. When [keepCached] it stays in the app cache (and is pruned later), otherwise it is deleted on close. */
    private class StreamedMediaFile(private val file: File, private val keepCached: Boolean) : MediaFile {
        override fun path(): String = file.path

        override fun persist(path: String): Boolean = runCatching { file.copyTo(File(path), overwrite = true) }.isSuccess

        override fun close() {
            if (!keepCached) file.delete()
        }
    }

    suspend fun download(
        source: MediaSource,
        mimeType: String?,
        filename: String?,
        keepCached: Boolean,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): MediaFile {
        val mxc = source.safeUrl
        if (!mxc.startsWith("mxc://")) throw IOException("Not an mxc url")
        val serverName = mxc.removePrefix("mxc://").substringBefore('/')
        val mediaId = mxc.removePrefix("mxc://").substringAfter('/', "")
        if (serverName.isEmpty() || mediaId.isEmpty()) throw IOException("Invalid mxc url")

        val extension = extensionFor(filename, mimeType)
        val finalFile = File(cacheDirectory, "${sha256Hex(mxc.toByteArray())}.$extension")
        if (finalFile.exists() && finalFile.length() > 0) {
            finalFile.setLastModified(System.currentTimeMillis())
            onProgress(finalFile.length(), finalFile.length())
            return StreamedMediaFile(finalFile, keepCached = true)
        }

        val encryption = parseEncryption(source.json)
        val partFile = File(cacheDirectory, "${finalFile.name}.part")
        partFile.delete()

        val base = innerClient.homeserver().trimEnd('/')
        val accessToken = innerClient.session().accessToken
        val url = URL("$base/_matrix/client/v1/media/download/${encode(serverName)}/${encode(mediaId)}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            val cipher = encryption?.let {
                Cipher.getInstance("AES/CTR/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(it.key, "AES"), IvParameterSpec(it.iv))
                }
            }
            var received = 0L
            var lastReport = 0L
            onProgress(0, total)
            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        received += read
                        if (cipher != null) {
                            digest.update(buffer, 0, read)
                            cipher.update(buffer, 0, read)?.let { output.write(it) }
                        } else {
                            output.write(buffer, 0, read)
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            onProgress(received, total)
                        }
                    }
                    cipher?.doFinal()?.let { output.write(it) }
                }
            }
            if (total != null && received != total) throw IOException("Incomplete download: $received of $total bytes")
            if (encryption?.sha256 != null && !digest.digest().contentEquals(encryption.sha256)) {
                throw IOException("Encrypted file hash mismatch")
            }
            onProgress(received, total ?: received)
            if (!partFile.renameTo(finalFile)) throw IOException("Could not store the downloaded file")
        } catch (e: Throwable) {
            partFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        pruneCache(keep = finalFile)
        Timber.d("Streamed media download finished (${finalFile.length()} bytes)")
        return StreamedMediaFile(finalFile, keepCached)
    }

    private class Encryption(val key: ByteArray, val iv: ByteArray, val sha256: ByteArray?)

    private fun parseEncryption(json: String?): Encryption? {
        if (json == null) return null
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: throw IOException("Unreadable media source")
        // Look for the encrypted-file object (url, key, iv, hashes) wherever the serialisation nests it.
        val root = findEncryptedFile(parsed)
        if (root == null) {
            // A plain source has no encryption fields at all; anything that looks encrypted but cannot be read must not be saved raw.
            if (json.contains("\"iv\"") || json.contains("\"key\"")) throw IOException("Unreadable media encryption info")
            return null
        }
        // From here the media is encrypted: never store the raw bytes if we cannot decrypt them.
        val keyObject = root.optJSONObject("key") ?: throw IOException("Missing media key")
        val k = keyObject.optString("k").takeIf { it.isNotEmpty() } ?: throw IOException("Missing media key")
        val iv = root.optString("iv").takeIf { it.isNotEmpty() } ?: throw IOException("Missing media iv")
        val hash = root.optJSONObject("hashes")?.optString("sha256")?.takeIf { it.isNotEmpty() }
        return Encryption(
            key = decodeBase64(k, urlSafe = true),
            iv = decodeBase64(iv, urlSafe = false),
            sha256 = hash?.let { decodeBase64(it, urlSafe = false) },
        )
    }

    private fun findEncryptedFile(node: JSONObject): JSONObject? {
        if (node.has("iv") && node.optJSONObject("key") != null) return node
        for (name in node.keys()) {
            val child = node.optJSONObject(name) ?: continue
            findEncryptedFile(child)?.let { return it }
        }
        return null
    }

    private fun decodeBase64(value: String, urlSafe: Boolean): ByteArray {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        return Base64.decode(padded, if (urlSafe) Base64.URL_SAFE else Base64.DEFAULT)
    }

    private fun extensionFor(filename: String?, mimeType: String?): String {
        val fromName = filename?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 && it.all { c -> c.isLetterOrDigit() } }
        if (fromName != null) return fromName.lowercase()
        return mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
    }

    private fun pruneCache(keep: File) {
        val files = cacheDirectory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            if (file == keep) continue
            total -= file.length()
            file.delete()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
