/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * This class manages the audio devices for a WebView.
 *
 * It listens for audio device changes and updates the WebView with the available devices.
 * It also handles the selection of the audio device by the user in the WebView and the default audio device based on the device type.
 *
 * See also: [Element Call controls docs.](https://github.com/element-hq/element-call/blob/livekit/docs/controls.md#audio-devices)
 */
class WebViewAudioManager(
    private val webView: WebView,
    private val coroutineScope: CoroutineScope,
    private val onInvalidAudioDeviceAdded: (InvalidAudioDeviceReason) -> Unit,
) {
    private val json by lazy {
        Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }

    /**
     * Whether to disable bluetooth audio devices. This must be done on Android versions lower than Android 12,
     * since the WebView approach breaks when using the legacy Bluetooth audio APIs.
     */
    private val disableBluetoothAudioDevices = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    /**
     * This flag indicates whether the WebView audio is enabled or not. By default, it is enabled.
     */
    private val isWebViewAudioEnabled = AtomicBoolean(true)

    /**
     * Store the device id requested by EC, and re-set it if something try to switch (only android S+).
     */
    private var ecRequestedDeviceId: String? = null
        set(value) {
            field = value
            _selectedDeviceId.value = value
        }

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    private val _availableDevices = MutableStateFlow<List<CallAudioOption>>(emptyList())

    /** The currently selected audio device id, for the native audio picker. */
    val selectedDeviceId: StateFlow<String?> = _selectedDeviceId

    /** The currently available audio devices, for the native audio picker. */
    val availableDevices: StateFlow<List<CallAudioOption>> = _availableDevices

    /**
     * Makes Element Call's own UI (button icon and colour) show [deviceId] as the selected output.
     *
     * `controls.setAudioDevice` is only wired up in Element Call's iOS audio code, so on Android it does
     * nothing. Android's code instead keeps its current selection if that device is still in the list it
     * was given, and otherwise falls back to the first entry. So: hand it a list with only the wanted
     * device (forcing it to be selected), then the full list with the wanted device first (so it keeps
     * it, and doesn't jump to a newly "added" headset). Both calls run back to back in one JS task.
     */
    private fun syncSelectionToWebUi(deviceId: String) {
        val all = listAudioDevices().map(SerializableAudioDevice::fromAudioDeviceInfo)
        val wanted = all.find { it.id == deviceId } ?: return
        val allWantedFirst = listOf(wanted) + all.filter { it.id != deviceId }
        webView.evaluateJavascript(
            "controls.setAvailableAudioDevices(${json.encodeToString(listOf(wanted))});" +
                "controls.setAvailableAudioDevices(${json.encodeToString(allWantedFirst)});",
            null,
        )
    }

    /** Refreshes [availableDevices]/[selectedDeviceId] from the OS, for when the native picker is opened. */
    fun refreshAudioOptions() {
        _availableDevices.value = listAudioDevices().map {
            CallAudioOption(id = it.id.toString(), name = it.productName.toString(), type = it.type)
        }
        if (_selectedDeviceId.value == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            _selectedDeviceId.value = audioManager.communicationDevice?.id?.toString()
        }
    }

    /** Selects a device from the native audio picker, the same way a selection in the web UI does. */
    fun selectDeviceFromNativeUi(deviceId: String) {
        previousSelectedDevice = listAudioDevices().find { it.id.toString() == deviceId }
        ecRequestedDeviceId = deviceId
        audioManager.selectAudioDevice(deviceId)
        // Also tell Element Call's UI, otherwise its own button (icon and white/dark styling) keeps
        // showing the previous device since the pick never went through the web UI.
        syncSelectionToWebUi(deviceId)
    }

    /**
     * The list of device types that are considered as communication devices, sorted by likelihood of it being used for communication.
     *
     * Bluetooth LE Audio devices (many modern true-wireless earbuds use this instead of the older,
     * classic Bluetooth SCO profile) are only added on Android 12+ ([Build.VERSION_CODES.S]),
     * matching [disableBluetoothAudioDevices]'s own cutoff for classic Bluetooth SCO below that.
     */
    private val wantedDeviceTypes = buildList {
        // Paired bluetooth device with microphone
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }
        // USB devices which can play or record audio
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
        add(AudioDeviceInfo.TYPE_USB_ACCESSORY)
        // Wired audio devices
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        // The built-in earpiece of the device: this is the expected default when nothing else is
        // connected (same as a normal phone call) — the user opts into the speaker explicitly,
        // rather than a call defaulting to it.
        add(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        // The built-in speaker of the device
        add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    }

    private fun rankOf(type: Int): Int = wantedDeviceTypes.indexOf(type).let { if (it == -1) Int.MAX_VALUE else it }

    private val audioDeviceComparator = Comparator<AudioDeviceInfo> { a, b ->
        // If the device type is not in the wantedDeviceTypes list, we give it a high index, (i.e. low priority)
        rankOf(a.type).compareTo(rankOf(b.type))
    }

    private val audioManager = webView.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * This wake lock is used to turn off the screen when the proximity sensor is triggered during a call,
     * if the selected audio device is the built-in earpiece.
     */
    private val proximitySensorWakeLock by lazy {
        webView.context.getSystemService<PowerManager>()
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "${webView.context.packageName}:ProximitySensorCallWakeLock")
    }

    /**
     * Used to ensure that only one coroutine can access the proximity sensor wake lock at a time, preventing re-acquiring or re-releasing it.
     */
    private val proximitySensorMutex = Mutex()

    /**
     * This listener tracks the current communication device and updates the WebView when it changes.
     */
    @get:RequiresApi(Build.VERSION_CODES.S)
    private val commsDeviceChangedListener by lazy {
        AudioManager.OnCommunicationDeviceChangedListener { device ->
            Timber.d("Audio device changed, type: ${device?.id}")
            val wantedDevice = this.ecRequestedDeviceId
            if (wantedDevice != null && this.ecRequestedDeviceId != device?.id?.toString()) {
                // We want to ensure that we stick to what EC selected even if it was changed outside
                Timber.d("Audio device changed to unwanted device ${device?.id}, enforce using the expected device $wantedDevice")
                audioManager.selectAudioDevice(wantedDevice)
            }
        }
    }

    /**
     * This callback is used to listen for audio device changes coming from the OS.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val validNewDevices = addedDevices.orEmpty().filter { it.type in wantedDeviceTypes && it.isSink }
            if (validNewDevices.isEmpty()) return

            // We need to calculate the available devices ourselves, since calling `listAudioDevices` will return an outdated list
            val audioDevices = dedupeDevices((listAudioDevices() + validNewDevices).distinctBy { it.id }.sortedWith(audioDeviceComparator))
            setAvailableAudioDevices(audioDevices.map(SerializableAudioDevice::fromAudioDeviceInfo))

            // If one of the newly connected devices outranks whatever is currently selected (e.g. a
            // Bluetooth headset was just connected while the call was on speaker), switch to it
            // automatically — this is what makes plugging in Bluetooth earphones actually redirect
            // the call's audio to them, instead of waiting on Element Call's own JS to decide.
            val currentRank = rankOf(listAudioDevices().find { it.id.toString() == ecRequestedDeviceId }?.type ?: -1)
            val bestNewDevice = validNewDevices.minByOrNull { rankOf(it.type) }
            if (bestNewDevice != null && rankOf(bestNewDevice.type) < currentRank) {
                Timber.d("Audio: newly connected device outranks current selection, switching to it: ${bestNewDevice.type}")
                ecRequestedDeviceId = bestNewDevice.id.toString()
                audioManager.selectAudioDevice(bestNewDevice)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            // Update the available devices
            // Element Call will then decide to switch devices if needed
            setAvailableAudioDevices()
        }
    }

    /**
     * Previously selected device, used to restore the selection when the selected device is removed.
     */
    private var previousSelectedDevice: AudioDeviceInfo? = null

    private var hasRegisteredCallbacks = false

    /**
     * Marks if the WebView audio is in call mode or not.
     */
    val isInCallMode = AtomicBoolean(false)

    init {
        // Apparently, registering the javascript interface takes a while, so registering and immediately using it doesn't work
        // We register it ahead of time to avoid this issue
        registerWebViewDeviceSelectedCallback()
    }

    /**
     * Call this method when the call starts to enable in-call audio mode.
     *
     * It'll set the audio mode to [AudioManager.MODE_IN_COMMUNICATION] if possible, register the audio device callback and set the available audio devices.
     */
    fun onCallStarted() {
        if (!isInCallMode.compareAndSet(false, true)) {
            Timber.w("Audio: tried to enable webview in-call audio mode while already in it")
            return
        }

        Timber.d("Audio: enabling webview in-call audio mode")

        audioManager.mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Set 'voice call' mode so volume keys actually control the call volume
            AudioManager.MODE_IN_COMMUNICATION
        } else {
            // Workaround for Android 12 and lower, otherwise changing the audio device doesn't work
            AudioManager.MODE_NORMAL
        }

        setWebViewAndroidNativeBridge()
    }

    /**
     * Call this method when the call stops to disable in-call audio mode.
     *
     * It's the counterpart of [onCallStarted], and should be called as a pair with it once the call has ended.
     */
    fun onCallStopped() {
        if (!isInCallMode.compareAndSet(true, false)) {
            Timber.w("Audio: tried to disable webview in-call audio mode while already disabled")
            return
        }

        // Since this should run when the call is no longer running, it should be OK to not use the mutex here
        if (proximitySensorWakeLock?.isHeld == true) {
            proximitySensorWakeLock?.release()
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        if (!hasRegisteredCallbacks) {
            Timber.w("Audio: tried to disable webview in-call audio mode without registering callbacks")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
            audioManager.removeOnCommunicationDeviceChangedListener(commsDeviceChangedListener)
        }

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    /**
     * Registers the WebView audio device selected callback.
     *
     * This should be called when the WebView is created to ensure that the callback is set before any audio device selection is made.
     */
    private fun registerWebViewDeviceSelectedCallback() {
        val webViewAudioDeviceSelectedCallback = AndroidWebViewAudioBridge(
            onAudioDeviceSelected = { selectedDeviceId ->
                previousSelectedDevice = listAudioDevices().find { it.id.toString() == selectedDeviceId }
                this.ecRequestedDeviceId = selectedDeviceId
                audioManager.selectAudioDevice(selectedDeviceId)
            },
            onAudioPlaybackStarted = {
                coroutineScope.launch(Dispatchers.Main) {
                    // Even with the callback, it seems like starting the audio takes a bit on the webview side,
                    // so we add an extra delay here to make sure it's ready
                    delay(2.seconds)

                    // Calling this ahead of time makes the default audio device to not use the right audio stream
                    setAvailableAudioDevices()

                    if (!hasRegisteredCallbacks) {
                        // Registering the audio devices changed callback will also set the default audio device
                        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            audioManager.addOnCommunicationDeviceChangedListener(Executors.newSingleThreadExecutor(), commsDeviceChangedListener)
                        }
                    }

                    // This can fire again when the call is answered (a new audio track starts), so if the
                    // user already picked a device while it was ringing (e.g. switched to speaker) and it's
                    // still available, keep it instead of falling back to the default (earpiece).
                    val alreadyChosenDevice = ecRequestedDeviceId?.let { requestedId ->
                        listAudioDevices().find { it.id.toString() == requestedId }
                    }
                    val deviceToUse = alreadyChosenDevice ?: listAudioDevices().firstOrNull()
                    deviceToUse?.let { device ->
                        Timber.d("Audio: using device at playback start: ${device.type} (kept user's choice: ${alreadyChosenDevice != null})")
                        ecRequestedDeviceId = device.id.toString()
                        audioManager.selectAudioDevice(device)
                    }

                    hasRegisteredCallbacks = true
                }
            }
        )
        Timber.d("Setting androidNativeBridge javascript interface in webview")
        webView.addJavascriptInterface(webViewAudioDeviceSelectedCallback, "androidNativeBridge")
    }

    /**
     * Assigns the callback in the WebView to be called when the user selects an audio device.
     *
     * It should be called with some delay after [registerWebViewDeviceSelectedCallback].
     */
    private fun setWebViewAndroidNativeBridge() {
        Timber.d("Adding callback in controls.onAudioPlaybackStarted")
        webView.evaluateJavascript("controls.onAudioPlaybackStarted = () => { androidNativeBridge.onTrackReady(); };", null)
        Timber.d("Adding callback in controls.onAudioDeviceSelect")
        webView.evaluateJavascript("controls.onAudioDeviceSelect = (id) => { androidNativeBridge.setAudioDevice(id); };", null)
    }

    /**
     * Returns the list of available audio devices, sorted by likelihood of it being used for communication.
     *
     * On Android 11 ([Build.VERSION_CODES.R]) and lower, it returns the list of output devices as a fallback.
     */
    private fun listAudioDevices(): List<AudioDeviceInfo> {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            val rawAudioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            rawAudioDevices.filter { it.type in wantedDeviceTypes && it.isSink }
        }.sortedWith(audioDeviceComparator)
        return dedupeDevices(devices)
    }

    /**
     * Modern true-wireless earbuds often advertise themselves through both the classic Bluetooth
     * SCO profile and a BLE Audio profile at the same time, which the OS surfaces as two separate
     * [AudioDeviceInfo] entries sharing the same product name. Keep only the highest-priority one
     * (the input must already be sorted) so the same physical device doesn't show up twice in the
     * device picker. Built-in devices are never deduped this way since each is already unique.
     */
    private fun dedupeDevices(sortedDevices: List<AudioDeviceInfo>): List<AudioDeviceInfo> {
        return sortedDevices.distinctBy { if (isBuiltIn(it.type)) it.id else it.productName.toString() }
    }

    /**
     * Sets the available audio devices in the WebView.
     *
     * @param devices The list of audio devices to set. If not provided, it will use the current list of audio devices.
     */
    private fun setAvailableAudioDevices(
        devices: List<SerializableAudioDevice> = listAudioDevices().map(SerializableAudioDevice::fromAudioDeviceInfo),
    ) {
        Timber.d("Updating available audio devices")
        _availableDevices.value = devices.map { CallAudioOption(id = it.id, name = it.name, type = it.type) }
        val deviceList = json.encodeToString(devices)
        webView.evaluateJavascript("controls.setAvailableAudioDevices($deviceList);", {
            Timber.d("Audio: setAvailableAudioDevices result: $it")
        })
    }

    /**
     * Selects the audio device on the OS based on the provided device id.
     *
     * It will select the device only if it is available in the list of audio devices.
     *
     * @param device The id of the audio device to select.
     */
    private fun AudioManager.selectAudioDevice(device: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val audioDevice = availableCommunicationDevices.find { it.id.toString() == device }
            selectAudioDevice(audioDevice)
        } else {
            val rawAudioDevices = getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val audioDevice = rawAudioDevices.find { it.id.toString() == device }
            selectAudioDevice(audioDevice)
        }
    }

    /**
     * Selects the audio device on the OS based on the provided device info.
     *
     * @param device The info of the audio device to select, or none to clear the selected device.
     */
    private fun AudioManager.selectAudioDevice(device: AudioDeviceInfo?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (device != null) {
                runCatchingExceptions {
                    Timber.d("Setting communication device: ${device.id} - ${deviceName(device.type, device.productName.toString())}")
                    if (!setCommunicationDevice(device)) {
                        Timber.w("Failed to setCommunication device")
                    }
                }.onFailure {
                    Timber.e(it, "Could not set communication device.")
                }
            } else {
                runCatchingExceptions {
                    clearCommunicationDevice()
                }.onFailure {
                    Timber.e(it, "Could not clear communication device.")
                }
            }
        } else {
            // On Android 11 and lower, we don't have the concept of communication devices
            // We have to call the right methods based on the device type
            @Suppress("DEPRECATION")
            if (device != null) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && disableBluetoothAudioDevices) {
                    Timber.w("Bluetooth audio devices are disabled on this Android version")
                    setAudioEnabled(false)
                    onInvalidAudioDeviceAdded(InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED)
                    return
                }
                setAudioEnabled(true)
                isSpeakerphoneOn = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                isBluetoothScoOn = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } else {
                isSpeakerphoneOn = false
                isBluetoothScoOn = false
            }
        }

        coroutineScope.launch {
            proximitySensorMutex.withLock {
                @Suppress("WakeLock", "WakeLockTimeout")
                if (device?.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                    if (proximitySensorWakeLock?.isHeld == false) {
                        // If the device is the built-in earpiece, we need to acquire the proximity sensor wake lock
                        proximitySensorWakeLock?.acquire()
                    }
                } else if (proximitySensorWakeLock?.isHeld == true) {
                    // If the device is no longer the earpiece, we need to release the wake lock
                    proximitySensorWakeLock?.release()
                }
            }
        }
    }

    /**
     * Sets whether the audio is enabled for Element Call in the WebView.
     * It will only perform the change if the audio state has changed.
     */
    private fun setAudioEnabled(enabled: Boolean) {
        coroutineScope.launch(Dispatchers.Main) {
            Timber.d("Setting audio enabled in Element Call: $enabled")
            if (isWebViewAudioEnabled.getAndSet(enabled) != enabled) {
                webView.evaluateJavascript("controls.setAudioEnabled($enabled);", null)
            }
        }
    }
}

/**
 * This class is used to handle the audio device selection in the WebView.
 * It listens for the audio device selection event and calls the callback with the selected device ID.
 */
private class AndroidWebViewAudioBridge(
    private val onAudioDeviceSelected: (String) -> Unit,
    private val onAudioPlaybackStarted: () -> Unit,
) {
    @JavascriptInterface
    fun setAudioDevice(id: String) {
        Timber.d("Audio device selected in webview, id: $id")
        onAudioDeviceSelected(id)
    }

    @JavascriptInterface
    fun onTrackReady() {
        // This method can be used to notify the WebView that the audio track is ready
        // It can be used to start playing audio or to update the UI
        Timber.d("Audio track is ready")

        onAudioPlaybackStarted()
    }
}

private fun deviceName(type: Int, name: String): String {
    // TODO maybe translate these?
    val typePart = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earpiece"
        else -> "Unknown"
    }
    return if (isBuiltIn(type)) {
        typePart
    } else {
        "$typePart - $name"
    }
}

private fun isBuiltIn(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    AudioDeviceInfo.TYPE_BUILTIN_MIC,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> true
    else -> false
}

enum class InvalidAudioDeviceReason {
    BT_AUDIO_DEVICE_DISABLED,
}

/** An audio output the user can pick from the native call audio picker. */
data class CallAudioOption(
    val id: String,
    val name: String,
    /** An [AudioDeviceInfo] TYPE_* constant. */
    val type: Int,
)

/**
 * This class is used to serialize the audio device information to JSON.
 */
@Suppress("unused")
@Serializable
internal data class SerializableAudioDevice(
    val id: String,
    val name: String,
    @Transient val type: Int = 0,
    // These have to be part of the constructor for the JSON serializer to pick them up
    val isEarpiece: Boolean = type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    val isSpeaker: Boolean = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    val isExternalHeadset: Boolean = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
        type == AudioDeviceInfo.TYPE_BLE_BROADCAST,
) {
    companion object {
        fun fromAudioDeviceInfo(audioDeviceInfo: AudioDeviceInfo): SerializableAudioDevice {
            return SerializableAudioDevice(
                id = audioDeviceInfo.id.toString(),
                name = deviceName(type = audioDeviceInfo.type, name = audioDeviceInfo.productName.toString()),
                type = audioDeviceInfo.type,
            )
        }
    }
}
