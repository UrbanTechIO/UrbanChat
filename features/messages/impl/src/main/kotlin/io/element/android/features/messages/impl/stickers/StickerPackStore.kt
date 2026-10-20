/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One sticker of an online pack: its text, the mxc url of the image and the image info to send along with it. */
data class PackSticker(
    val body: String,
    val url: String,
    val info: String?,
)

data class StickerPack(
    /** The address the pack was added from (an index file or a single pack file). */
    val sourceUrl: String,
    val title: String,
    val stickers: List<PackSticker>,
)

/**
 * Online sticker packs in the standard Matrix sticker picker format: a pack file is
 * `{"title": ..., "stickers": [{"body": ..., "url": "mxc://...", "info": {...}}]}`, and an index file lists pack files
 * as `{"packs": ["a.json", "b.json"]}`. Only the addresses are stored on the phone; the packs are fetched when asked.
 */
class StickerPackStore(context: Context) {
    private val file = File(context.filesDir, "sticker_packs.json")

    // A copy of the fetched packs is kept on disk, so the tabs appear immediately when the sheet is opened.
    // [refresh] then updates them in the background instead of showing nothing until every pack is downloaded again.
    private val cacheFile = File(context.filesDir, "sticker_packs_cache.json")

    private val _packs = MutableStateFlow(readCache())
    val packs: StateFlow<List<StickerPack>> = _packs

    private val orderFile = File(context.filesDir, "sticker_tab_order.json")

    /** The user's order of the sticker tabs (keys from [packKey], plus "mine"). Unknown or missing keys are ignored by the UI. */
    private val _order = MutableStateFlow(readOrder())
    val order: StateFlow<List<String>> = _order

    private fun readOrder(): List<String> = runCatching {
        val array = JSONArray(orderFile.readText())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    fun setOrder(keys: List<String>) {
        _order.value = keys
        runCatching { orderFile.writeText(JSONArray(keys).toString()) }
    }

    companion object {
        /** Identifies a pack: an index file can hold several packs, so the address alone is not enough. */
        fun packKey(pack: StickerPack): String = pack.sourceUrl + "|" + pack.title
    }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private fun readCache(): List<StickerPack> = runCatching {
        val array = JSONArray(cacheFile.readText())
        List(array.length()) { index ->
            val pack = array.getJSONObject(index)
            val stickers = pack.getJSONArray("stickers")
            StickerPack(
                sourceUrl = pack.getString("sourceUrl"),
                title = pack.getString("title"),
                stickers = List(stickers.length()) { i ->
                    val sticker = stickers.getJSONObject(i)
                    PackSticker(
                        body = sticker.getString("body"),
                        url = sticker.getString("url"),
                        info = sticker.optString("info").takeIf { it.isNotEmpty() },
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    private fun writeCache(packs: List<StickerPack>) {
        runCatching {
            val array = JSONArray()
            packs.forEach { pack ->
                array.put(
                    JSONObject()
                        .put("sourceUrl", pack.sourceUrl)
                        .put("title", pack.title)
                        .put(
                            "stickers",
                            JSONArray().also { list ->
                                pack.stickers.forEach { sticker ->
                                    list.put(
                                        JSONObject()
                                            .put("body", sticker.body)
                                            .put("url", sticker.url)
                                            .put("info", sticker.info.orEmpty())
                                    )
                                }
                            }
                        )
                )
            }
            cacheFile.writeText(array.toString())
        }
    }

    private fun savedUrls(): List<String> = runCatching {
        val array = JSONArray(file.readText())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveUrls(urls: List<String>) {
        file.writeText(JSONArray(urls).toString())
    }

    /** Fetches every saved pack again. A pack that cannot be fetched is simply left out for now. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _loading.value = true
        try {
            val urls = savedUrls()
            val previous = _packs.value
            val fresh = urls.flatMap { url ->
                // If a pack cannot be fetched right now, keep the copy we already have for it.
                runCatching { fetch(url) }.getOrNull() ?: previous.filter { it.sourceUrl == url }
            }
            if (fresh != previous) {
                _packs.value = fresh
                writeCache(fresh)
            }
        } finally {
            _loading.value = false
        }
    }

    /** Adds a pack (or every pack of an index) by its address. Fails if nothing usable is found there. */
    suspend fun add(address: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = address.trim()
            if (!url.startsWith("https://")) throw IOException("The link must start with https://")
            val fetched = fetch(url)
            if (fetched.isEmpty()) throw IOException("No stickers found at that link")
            val urls = savedUrls()
            if (url !in urls) saveUrls(urls + url)
            _packs.value = (_packs.value.filter { it.sourceUrl != url }) + fetched
            writeCache(_packs.value)
            fetched.size
        }
    }

    suspend fun remove(pack: StickerPack) = withContext(Dispatchers.IO) {
        // Remove the address only when no other pack still comes from it (an index can hold several packs).
        val remaining = _packs.value.filter { it != pack }
        _packs.value = remaining
        writeCache(remaining)
        if (remaining.none { it.sourceUrl == pack.sourceUrl }) {
            saveUrls(savedUrls().filter { it != pack.sourceUrl })
        }
    }

    private fun fetch(address: String): List<StickerPack> {
        val json = JSONObject(download(address))
        if (json.has("packs")) {
            val files = json.getJSONArray("packs")
            return List(files.length()) { files.getString(it) }.mapNotNull { name ->
                runCatching { parsePack(address, JSONObject(download(URL(URL(address), name).toString()))) }.getOrNull()
            }
        }
        return listOfNotNull(parsePack(address, json))
    }

    private fun parsePack(sourceUrl: String, json: JSONObject): StickerPack? {
        val array = json.optJSONArray("stickers") ?: return null
        val stickers = List(array.length()) { array.getJSONObject(it) }.mapNotNull { sticker ->
            val url = sticker.optString("url").takeIf { it.startsWith("mxc://") } ?: return@mapNotNull null
            PackSticker(
                body = sticker.optString("body", "Sticker"),
                url = url,
                info = sticker.optJSONObject("info")?.toString(),
            )
        }
        if (stickers.isEmpty()) return null
        return StickerPack(sourceUrl = sourceUrl, title = json.optString("title", "Stickers"), stickers = stickers)
    }

    private fun download(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
