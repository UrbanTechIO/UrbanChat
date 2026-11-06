/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.urlpreview

import androidx.compose.runtime.mutableStateMapOf
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.urlpreview.UrlPreviewInfo
import io.element.android.libraries.matrix.api.urlpreview.UrlPreviewProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface UrlPreviewLoadState {
    data object Loading : UrlPreviewLoadState
    data class Loaded(val info: UrlPreviewInfo) : UrlPreviewLoadState
    data object Failed : UrlPreviewLoadState
}

/**
 * Fetches and caches [UrlPreviewInfo] for the timeline's link-preview cards
 * ([io.element.android.features.messages.impl.timeline.components.event.LinkPreviewCard]), so
 * scrolling a URL back into view doesn't re-fetch it.
 */
interface UrlPreviewCoordinator {
    /** Read inside a `@Composable`: backed by Compose snapshot state, so reads are observed. Null until [ensureFetched] is called for this URL. */
    fun stateFor(url: String): UrlPreviewLoadState?

    /** No-ops if already fetched (or in flight) for this exact URL. */
    fun ensureFetched(url: String)
}

@ContributesBinding(RoomScope::class)
class DefaultUrlPreviewCoordinator(
    private val urlPreviewProvider: UrlPreviewProvider,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : UrlPreviewCoordinator {
    private val states = mutableStateMapOf<String, UrlPreviewLoadState>()
    private val failedAt = mutableMapOf<String, Long>()

    override fun stateFor(url: String): UrlPreviewLoadState? = states[url]

    override fun ensureFetched(url: String) {
        val current = states[url]
        if (current != null) {
            // A link that failed (typically because the server was still fetching the page right after it was sent)
            // is tried again when it comes back into view, instead of staying empty until the chat is reopened.
            val failedLongAgo = System.currentTimeMillis() - (failedAt[url] ?: 0L) > RETRY_AFTER_FAILURE_MILLIS
            if (current != UrlPreviewLoadState.Failed || !failedLongAgo) return
        }
        states[url] = UrlPreviewLoadState.Loading
        sessionCoroutineScope.launch {
            // Retry a few times with a growing pause while the card stays in its loading state.
            for (delayMillis in RETRY_DELAYS_MILLIS) {
                val info = urlPreviewProvider.getPreview(url).getOrNull()
                val isUseless = info == null || (info.title.isNullOrBlank() && info.description.isNullOrBlank() && info.imageMxcUri.isNullOrBlank())
                if (!isUseless) {
                    states[url] = UrlPreviewLoadState.Loaded(info!!)
                    return@launch
                }
                delay(delayMillis)
            }
            failedAt[url] = System.currentTimeMillis()
            states[url] = UrlPreviewLoadState.Failed
        }
    }

    private companion object {
        val RETRY_DELAYS_MILLIS = listOf(1_500L, 4_000L, 8_000L)
        const val RETRY_AFTER_FAILURE_MILLIS = 30_000L
    }
}
