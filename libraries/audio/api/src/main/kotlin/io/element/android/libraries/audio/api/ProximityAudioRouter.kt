/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.audio.api

/**
 * WhatsApp-style "raise to ear" behaviour: while active, listens to the device's proximity
 * sensor and, when the phone is held close (e.g. brought up to the ear), switches the current
 * audio session to the earpiece and dims the screen; moving it away switches back to the
 * speaker. No-op on a device with no proximity sensor.
 *
 * Assumes the audio track already being played uses voice-communication-styled AudioAttributes
 * throughout (fixed, never swapped mid-playback) — this only ever flips
 * [android.media.AudioManager.isSpeakerphoneOn], which is instant, glitch-free, and has no effect
 * on a track using ordinary media attributes.
 */
interface ProximityAudioRouter {
    /** Starts listening. Call when audio playback that should support this behaviour begins. */
    fun start()

    /** Stops listening and reverts any routing/screen change back to how it was before [start]. */
    fun stop()
}
