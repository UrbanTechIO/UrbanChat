/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.audio.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.audio.api.ProximityAudioRouter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext

private const val DEBOUNCE_MS = 500L

// After a switch actually commits, a slow "moving away/closer" motion can keep the raw reading
// flickering across the threshold for a while longer — this keeps sensor events from re-arming
// the debounce again until at least this long after the last real switch, so those flickers get
// fully absorbed instead of each one independently reaching DEBOUNCE_MS and re-triggering.
private const val POST_SWITCH_LOCKOUT_MS = 500L

// How long the router stays set up after playback stops, so a following voice message keeps the earpiece and the dark screen.
private const val STOP_GRACE_MS = 3_000L

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultProximityAudioRouter(
    @ApplicationContext private val context: Context,
) : ProximityAudioRouter {
    private val sensorManager = context.getSystemService<SensorManager>()

    // Some devices (e.g. certain Samsung models) expose more than one TYPE_PROXIMITY sensor —
    // getDefaultSensor() can pick a non-wake-up one that Android's power management barely
    // updates while the screen is already on. Prefer a wake-up-capable one, which is the variant
    // actually driving the system's own "screen off during calls" behaviour.
    private val proximitySensor = sensorManager
        ?.getSensorList(Sensor.TYPE_PROXIMITY)
        ?.let { sensors -> sensors.firstOrNull { it.isWakeUpSensor } ?: sensors.firstOrNull() }
    private val audioManager = context.getSystemService<AudioManager>()
    private val powerManager = context.getSystemService<PowerManager>()
    private val debounceHandler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var isListening = false
    private var isNear = false
    private var previousMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var pendingSwitch: Runnable? = null
    private var lastSwitchAtMs = 0L
    private var pendingStop: Runnable? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val distance = event.values.firstOrNull() ?: return
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val near = distance < maxRange
            pendingSwitch?.let { debounceHandler.removeCallbacks(it) }
            pendingSwitch = null
            if (near == isNear) return
            // The reading tends to flicker for a moment while the phone is still mid-motion —
            // only commit to a switch once it's held steady for a while, otherwise audio visibly
            // bounces between earpiece and speaker a few times before settling. If we just
            // switched, extend the wait so lingering flicker right after don't re-trigger it.
            val sinceLastSwitch = SystemClock.elapsedRealtime() - lastSwitchAtMs
            // Going to the ear is acted on at once, so the sound moves to the earpiece before the phone touches the ear.
            // Only the move away from the ear waits, to absorb the flicker.
            val delay = if (near) {
                0L
            } else if (sinceLastSwitch < POST_SWITCH_LOCKOUT_MS) {
                DEBOUNCE_MS + (POST_SWITCH_LOCKOUT_MS - sinceLastSwitch)
            } else {
                DEBOUNCE_MS
            }
            val runnable = Runnable {
                isNear = near
                lastSwitchAtMs = SystemClock.elapsedRealtime()
                setSpeakerphoneOn(!near)
                pendingSwitch = null
            }
            pendingSwitch = runnable
            debounceHandler.postDelayed(runnable, delay)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    @Suppress("DEPRECATION")
    override fun start() {
        // The next voice message starts right after the previous one stopped: keep the route (and the dark screen) as it was.
        pendingStop?.let { debounceHandler.removeCallbacks(it) }
        pendingStop = null
        if (isListening) return
        val sensor = proximitySensor ?: return
        isListening = true
        isNear = false
        lastSwitchAtMs = 0L
        // The system's own proximity lock turns the screen off the moment the phone is at the ear and back on when it leaves,
        // far faster than waiting for our own reading. It does nothing while the phone is away from the ear.
        acquireWakeLock()
        // Track content is always voice-communication styled (see SimplePlayer) so the track
        // itself never needs touching again — from here on, only isSpeakerphoneOn moves audio
        // between the earpiece and the speaker, which is instant and glitch-free (no AudioTrack
        // recreation), unlike swapping AudioAttributes mid-playback.
        val manager = audioManager
        if (manager != null) {
            runCatchingExceptions {
                previousMode = manager.mode
                previousSpeakerphoneOn = manager.isSpeakerphoneOn
                manager.mode = AudioManager.MODE_IN_COMMUNICATION
                // A voice message only ever plays through the earpiece or the speaker, never Bluetooth. But
                // switching to MODE_IN_COMMUNICATION makes Android re-pick a "communication" route, and if a
                // Bluetooth headset is connected it can briefly grab that route before our isSpeakerphoneOn
                // below takes effect — heard as audio bouncing to the headset and back. Explicitly stopping
                // SCO first stops Android from ever offering it that route.
                manager.stopBluetoothSco()
                manager.isBluetoothScoOn = false
                manager.isSpeakerphoneOn = true
            }
        }
        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun stop() {
        if (!isListening) return
        // A voice message ending is followed by the next one a moment later: wait before really letting go.
        pendingStop?.let { debounceHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingStop = null
            stopNow()
        }
        pendingStop = runnable
        debounceHandler.postDelayed(runnable, STOP_GRACE_MS)
    }

    @Suppress("DEPRECATION")
    private fun stopNow() {
        if (!isListening) return
        isListening = false
        sensorManager?.unregisterListener(listener)
        pendingSwitch?.let { debounceHandler.removeCallbacks(it) }
        pendingSwitch = null
        releaseWakeLock()
        val manager = audioManager
        if (manager != null) {
            runCatchingExceptions {
                manager.isSpeakerphoneOn = previousSpeakerphoneOn ?: true
                manager.mode = previousMode ?: AudioManager.MODE_NORMAL
            }
        }
        previousMode = null
        previousSpeakerphoneOn = null
        isNear = false
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneOn(speakerOn: Boolean) {
        runCatchingExceptions {
            // Keep Bluetooth out of the way here too (see the comment in start()): switching to the
            // earpiece is also a route change that could otherwise get offered to a connected headset.
            audioManager?.stopBluetoothSco()
            audioManager?.isBluetoothScoOn = false
            audioManager?.isSpeakerphoneOn = speakerOn
        }
        // The proximity wake lock stays held for the whole playback (see start()).
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = powerManager?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) } ?: return
        runCatchingExceptions {
            @Suppress("WakeLock", "WakeLockTimeout")
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "${context.packageName}:VoiceMessageProximityWakeLock").apply {
                setReferenceCounted(false)
                acquire()
                wakeLock = this
            }
        }
    }

    private fun releaseWakeLock() {
        runCatchingExceptions {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }
}
