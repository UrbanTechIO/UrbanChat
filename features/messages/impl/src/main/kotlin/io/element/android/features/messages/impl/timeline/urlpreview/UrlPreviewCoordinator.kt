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

    override fun stateFor(url: String): UrlPreviewLoadState? = states[url]

    override fun ensureFetched(url: String) {
        if (states.containsKey(url)) return
        states[url] = UrlPreviewLoadState.Loading
        sessionCoroutineScope.launch {
            urlPreviewProvider.getPreview(url).fold(
                onSuccess = { info ->
                    val isUseless = info.title.isNullOrBlank() && info.description.isNullOrBlank() && info.imageMxcUri.isNullOrBlank()
                    states[url] = if (isUseless) UrlPreviewLoadState.Failed else UrlPreviewLoadState.Loaded(info)
                },
                onFailure = { states[url] = UrlPreviewLoadState.Failed },
            )
        }
    }
}
