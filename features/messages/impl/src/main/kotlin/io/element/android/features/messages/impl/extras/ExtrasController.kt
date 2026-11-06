/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.extras

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A GIF from the search: a small still for the grid and the address of the animated file to send. */
data class GifItem(val id: String, val previewUrl: String, val sendUrl: String)

/** Small attach-menu extras of one chat: sharing a contact and searching GIFs. */
class ExtrasController(
    private val context: Context,
    private val client: MatrixClient,
    private val room: JoinedRoom,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("urban_extras", Context.MODE_PRIVATE)

    /** People matching [term] on the user directory. */
    suspend fun searchUsers(term: String): List<MatrixUser> = withContext(Dispatchers.IO) {
        client.searchUsers(term, 20).getOrNull()?.results.orEmpty()
    }

    /**
     * Sends [user] as a contact: a message with their name and a link to them. The link shows as a tappable user on the
     * other side (in UrbanChat and other Matrix apps), which opens their profile.
     */
    fun sendContact(user: MatrixUser) {
        scope.launch {
            val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.userId.value
            val link = "https://matrix.to/#/${user.userId.value}"
            val content = JSONObject()
                .put("msgtype", "m.text")
                .put("body", "👤 $name\n$link")
                .put("format", "org.matrix.custom.html")
                .put("formatted_body", "👤 <b>${escapeHtml(name)}</b><br><a href=\"$link\">${escapeHtml(user.userId.value)}</a>")
            room.sendRaw("m.room.message", content.toString())
        }
    }

    private fun escapeHtml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** The Giphy API key: the one entered on this phone, or the built-in one. */
    var gifApiKey: String
        get() = prefs.getString(GIF_KEY_PREF, "").orEmpty().ifEmpty { DEFAULT_GIF_KEY }
        set(value) {
            prefs.edit().putString(GIF_KEY_PREF, value.trim()).apply()
        }

    /** Trending GIFs when [query] is blank, otherwise the search results. Empty when the key is missing or the request fails. */
    suspend fun searchGifs(query: String, stickers: Boolean = false): List<GifItem> = withContext(Dispatchers.IO) {
        val key = gifApiKey
        if (key.isEmpty()) return@withContext emptyList()
        runCatching {
            val q = query.trim()
            // Giphy has the same two requests for its GIFs and for its transparent stickers.
            val kind = if (stickers) "stickers" else "gifs"
            val address = if (q.isEmpty()) {
                "https://api.giphy.com/v1/$kind/trending?api_key=$key&limit=36&rating=pg-13"
            } else {
                "https://api.giphy.com/v1/$kind/search?api_key=$key&q=${URLEncoder.encode(q, "UTF-8")}&limit=36&rating=pg-13"
            }
            val json = JSONObject(httpGet(address).toString(Charsets.UTF_8))
            val data = json.getJSONArray("data")
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val images = item.getJSONObject("images")
                    val preview = images.optJSONObject("fixed_width_small_still")?.optString("url").orEmpty()
                    val send = images.optJSONObject("downsized")?.optString("url")?.takeIf { it.isNotEmpty() }
                        ?: images.optJSONObject("original")?.optString("url").orEmpty()
                    if (preview.isNotEmpty() && send.isNotEmpty()) add(GifItem(item.getString("id"), preview, send))
                }
            }
        }.onFailure { Timber.w(it, "GIF search failed") }.getOrDefault(emptyList())
    }

    /** The small still of [item] for the grid, or null if it could not be loaded. */
    suspend fun loadPreview(item: GifItem): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { httpGet(item.previewUrl) }.getOrNull()
    }

    /** Downloads the animated GIF of [item] and returns an address the app can send it from. */
    suspend fun downloadGif(item: GifItem): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "gifs").apply { mkdirs() }
            val file = File(dir, "giphy_${item.id}.gif")
            if (!file.exists() || file.length() == 0L) file.writeBytes(httpGet(item.sendUrl))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.onFailure { Timber.w(it, "GIF download failed") }.getOrNull()
    }

    private fun httpGet(address: String): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val GIF_KEY_PREF = "giphy_api_key"
        const val DEFAULT_GIF_KEY = "WWe4bRGmdouoRqm8M7sborpEhSF9IT5c"
    }
}
