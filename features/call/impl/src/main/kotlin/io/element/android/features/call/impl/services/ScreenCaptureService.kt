/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.services

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.ui.ElementCallActivity
import io.element.android.libraries.designsystem.utils.CommonDrawables
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Captures the device screen (with the user's consent, given through the system screen-capture dialog)
 * and hands JPEG frames, Base64 encoded, to [listener].
 *
 * Android's WebView has no `getDisplayMedia()`, so Element Call's "Share screen" button never shows up.
 * The call screen injects a small replacement for it (see `ScreenShareBridge`) that draws these frames on a
 * canvas and publishes the canvas as the screen-share video track.
 */
class ScreenCaptureService : Service() {
    interface Listener {
        /** Capture is running; frames are about to arrive at this size. */
        fun onStarted(width: Int, height: Int)

        fun onFrame(jpegBase64: String)

        /** Capture ended: stopped by the user (from the system, or the notification), or by the app. */
        fun onStopped()
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"
        private const val ACTION_STOP = "io.element.android.features.call.STOP_SCREEN_CAPTURE"
        private const val CHANNEL_ID = "screen_capture_service_channel"
        private const val NOTIFICATION_ID = 0x5c4e

        // Frames are sent through the JavaScript bridge as text, so keep them small and sparse.
        private const val MAX_LONG_SIDE = 1280
        private const val MIN_FRAME_INTERVAL_MS = 140L
        private const val JPEG_QUALITY = 60

        @Volatile
        var listener: Listener? = null

        @Volatile
        var isRunning: Boolean = false
            private set

        fun createConsentIntent(context: Context): Intent {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            return manager.createScreenCaptureIntent()
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var lastFrameAt = 0L
    private var captureWidth = 0
    private var captureHeight = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter the foreground first: the system kills the app if a service started with startForegroundService() does not.
        showNotification()
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (resultCode != Activity.RESULT_OK || data == null) {
            listener?.onStopped()
            stopSelf()
            return START_NOT_STICKY
        }


        runCatching {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newProjection = checkNotNull(manager.getMediaProjection(resultCode, data)) { "No media projection" }
            projection = newProjection
            newProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Timber.d("Screen capture stopped by the system")
                        stopSelf()
                    }
                },
                Handler(mainLooper),
            )

            val captureThread = HandlerThread("screen-capture").also { it.start() }
            thread = captureThread
            val (width, height, densityDpi) = currentCaptureSize()
            val reader = newReader(width, height)
            imageReader = reader
            virtualDisplay = newProjection.createVirtualDisplay(
                "UrbanChatScreenShare",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null,
            )
            captureWidth = width
            captureHeight = height
            isRunning = true
            listener?.onStarted(width, height)
        }.onFailure {
            Timber.e(it, "Failed to start screen capture")
            listener?.onStopped()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** The size of the screen as it is right now (it swaps when the phone rotates), scaled down for sending. */
    private fun currentCaptureSize(): Triple<Int, Int, Int> {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        val ratio = (MAX_LONG_SIDE.toFloat() / max(metrics.widthPixels, metrics.heightPixels)).coerceAtMost(1f)
        // Even sizes only, some encoders refuse odd ones.
        val width = ((metrics.widthPixels * ratio).roundToInt() / 2) * 2
        val height = ((metrics.heightPixels * ratio).roundToInt() / 2) * 2
        return Triple(width, height, metrics.densityDpi)
    }

    private fun newReader(width: Int, height: Int): ImageReader {
        val captureHandler = Handler(checkNotNull(thread).looper)
        return ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also {
            it.setOnImageAvailableListener({ reader -> onImage(reader, width, height) }, captureHandler)
        }
    }

    /** Follows the phone's rotation: the shared picture turns with it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val display = virtualDisplay ?: return
        runCatching {
            val (width, height, densityDpi) = currentCaptureSize()
            if (width == captureWidth && height == captureHeight) return
            val oldReader = imageReader
            val reader = newReader(width, height)
            imageReader = reader
            display.resize(width, height, densityDpi)
            display.surface = reader.surface
            captureWidth = width
            captureHeight = height
            oldReader?.setOnImageAvailableListener(null, null)
            oldReader?.close()
            listener?.onStarted(width, height)
        }.onFailure { Timber.w(it, "Failed to resize the screen capture") }
    }

    private fun onImage(reader: ImageReader, width: Int, height: Int) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) return
            lastFrameAt = now

            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * width
            val padded = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val bitmap = if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
            val out = ByteArrayOutputStream(64 * 1024)
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            listener?.onFrame(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
        } catch (e: Exception) {
            Timber.w(e, "Failed to encode a screen capture frame")
        } finally {
            image.close()
        }
    }

    private fun showNotification() {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.call_screen_share_channel_title))
                .build()
        )
        val openCall = PendingIntentCompat.getActivity(this, 0, Intent(this, ElementCallActivity::class.java), 0, false)
        val stop = PendingIntentCompat.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            0,
            false,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(getString(R.string.call_screen_share_notification_title))
            .setContentText(getString(R.string.call_screen_share_notification_message))
            .setContentIntent(openCall)
            .addAction(0, getString(R.string.call_screen_share_stop), stop)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        val wasRunning = isRunning
        isRunning = false
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { imageReader?.close() }
        runCatching { projection?.stop() }
        thread?.quitSafely()
        virtualDisplay = null
        imageReader = null
        projection = null
        thread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (wasRunning) listener?.onStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
