/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.urlpreview

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.extensions.mapCatchingExceptions
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.CacheDirectory
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.urlpreview.UrlPreviewInfo
import io.element.android.libraries.matrix.api.urlpreview.UrlPreviewProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.URLEncoder

private const val URL_PREVIEW_CACHE_DIR_NAME = "url_preview_cache"

@Serializable
private data class UrlPreviewResponse(
    @SerialName("og:title") val title: String? = null,
    @SerialName("og:description") val description: String? = null,
    @SerialName("og:site_name") val siteName: String? = null,
    @SerialName("og:image") val image: String? = null,
    @SerialName("og:image:width") val imageWidth: Long? = null,
    @SerialName("og:image:height") val imageHeight: Long? = null,
)

@Serializable
private data class UrlPreviewCacheEntry(
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    val imageMxcUri: String? = null,
    val imageWidth: Long? = null,
    val imageHeight: Long? = null,
)

@ContributesBinding(SessionScope::class)
class DefaultUrlPreviewProvider(
    private val matrixClient: MatrixClient,
    private val jsonProvider: JsonProvider,
    @CacheDirectory private val cacheDirectory: File,
) : UrlPreviewProvider {
    private val diskCacheDir by lazy { File(cacheDirectory, URL_PREVIEW_CACHE_DIR_NAME) }

    override suspend fun getPreview(url: String): Result<UrlPreviewInfo> {
        readFromDisk(url)?.let { return Result.success(it) }
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val ts = System.currentTimeMillis()
        val homeserverUrl = matrixClient.homeserverUrl.trimEnd('/')
        // MSC3916's authenticated endpoint first; fall back to the legacy unauthenticated-proxy
        // path for older homeservers that don't yet serve the former.
        return fetch("$homeserverUrl/_matrix/client/v1/media/preview_url?url=$encodedUrl&ts=$ts", url)
            .recoverCatching { fetch("$homeserverUrl/_matrix/media/v3/preview_url?url=$encodedUrl&ts=$ts", url).getOrThrow() }
            .onSuccess { writeToDisk(url, it) }
    }

    private suspend fun fetch(requestUrl: String, originalUrl: String): Result<UrlPreviewInfo> {
        return matrixClient.getAuthenticatedUrl(requestUrl).mapCatchingExceptions { bytes ->
            val response = jsonProvider().decodeFromString<UrlPreviewResponse>(String(bytes, Charsets.UTF_8))
            UrlPreviewInfo(
                url = originalUrl,
                title = response.title,
                description = response.description,
                siteName = response.siteName,
                imageMxcUri = response.image,
                imageWidth = response.imageWidth,
                imageHeight = response.imageHeight,
            )
        }
    }

    // No expiry: once a link's preview is fetched, it's kept indefinitely (until app data is
    // cleared) so revisiting a chat loads it instantly and without network, as requested.
    private fun readFromDisk(url: String): UrlPreviewInfo? = runCatchingExceptions {
        val file = cacheFileFor(url)
        if (!file.exists()) return@runCatchingExceptions null
        val entry = jsonProvider().decodeFromString<UrlPreviewCacheEntry>(file.readText())
        UrlPreviewInfo(
            url = url,
            title = entry.title,
            description = entry.description,
            siteName = entry.siteName,
            imageMxcUri = entry.imageMxcUri,
            imageWidth = entry.imageWidth,
            imageHeight = entry.imageHeight,
        )
    }.getOrNull()

    private fun writeToDisk(url: String, info: UrlPreviewInfo) {
        runCatchingExceptions {
            diskCacheDir.mkdirs()
            val entry = UrlPreviewCacheEntry(
                title = info.title,
                description = info.description,
                siteName = info.siteName,
                imageMxcUri = info.imageMxcUri,
                imageWidth = info.imageWidth,
                imageHeight = info.imageHeight,
            )
            cacheFileFor(url).writeText(jsonProvider().encodeToString(entry))
        }
    }

    private fun cacheFileFor(url: String): File {
        val hash = url.hashCode().toUInt().toString(16)
        return File(diskCacheDir, "$hash.json")
    }
}
