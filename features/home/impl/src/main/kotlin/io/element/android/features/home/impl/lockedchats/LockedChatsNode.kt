/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.lockedchats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.coroutines.launch

@ContributesNode(SessionScope::class)
@AssistedInject
class LockedChatsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: LockedChatsPresenter,
    private val sessionPreferencesStore: SessionPreferencesStore,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onRoomClick(roomId: RoomId)
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val coroutineScope = rememberCoroutineScope()
        LockedChatsView(
            state = state,
            onBackClick = ::navigateUp,
            onRoomClick = callback::onRoomClick,
            onUnlockRoom = { roomId ->
                coroutineScope.launch { sessionPreferencesStore.setRoomLocked(roomId, false) }
            },
            modifier = modifier,
        )
    }
}
