/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaplayer.test

import io.element.android.libraries.audio.api.ProximityAudioRouter

class FakeProximityAudioRouter(
    private val startResult: () -> Unit = {},
    private val stopResult: () -> Unit = {},
) : ProximityAudioRouter {
    override fun start() {
        startResult()
    }

    override fun stop() {
        stopResult()
    }
}
