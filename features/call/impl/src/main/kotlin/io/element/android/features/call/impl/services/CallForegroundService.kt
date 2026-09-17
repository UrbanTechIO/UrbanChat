/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.ui.ElementCallActivity
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.push.api.notifications.ForegroundServiceType
import io.element.android.libraries.push.api.notifications.NotificationIdProvider
import timber.log.Timber

/**
 * A foreground service that shows a notification for an ongoing call while the UI is in background.
 */
class CallForegroundService : Service() {
    companion object {
        private const val EXTRA_INCLUDE_MEDIA_PROJECTION_TYPE = "includeMediaProjectionType"

        /**
         * @param includeMediaProjectionType Whether to also declare the `mediaProjection`
         * foreground service type. Defaults to `false`: Android requires the app to already hold a
         * live screen-capture grant at the moment a `mediaProjection`-type foreground service is
         * started, or the whole `startForeground()` call throws `SecurityException` (confirmed on
         * Android 16) and the service is killed ~5s later — which silently broke the microphone
         * exemption too, since both types were requested in the same call. Stock Android WebView
         * doesn't actually implement screen-capture behind `getDisplayMedia()` in the first place
         * (confirmed: it always rejects), so there was never a working code path this type would
         * have enabled — this isn't a feature tradeoff, just a dead declaration removed.
         */
        fun start(context: Context, includeMediaProjectionType: Boolean = false) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(context, CallForegroundService::class.java)
                    .putExtra(EXTRA_INCLUDE_MEDIA_PROJECTION_TYPE, includeMediaProjectionType)
                ContextCompat.startForegroundService(context, intent)
            } else {
                Timber.w("Microphone permission is not granted, cannot start the call foreground service")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var notificationManagerCompat: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()

        notificationManagerCompat = NotificationManagerCompat.from(this)

        val foregroundServiceChannel = NotificationChannelCompat.Builder(
            "call_foreground_service_channel",
            NotificationManagerCompat.IMPORTANCE_LOW,
        ).setName(
            getString(R.string.call_foreground_service_channel_title_android).ifEmpty { "Ongoing call" }
        ).build()
        notificationManagerCompat.createNotificationChannel(foregroundServiceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val includeMediaProjectionType = intent?.getBooleanExtra(EXTRA_INCLUDE_MEDIA_PROJECTION_TYPE, false) ?: false

        val callActivityIntent = Intent(this, ElementCallActivity::class.java)
        val pendingIntent = PendingIntentCompat.getActivity(this, 0, callActivityIntent, 0, false)
        val notification = NotificationCompat.Builder(this, "call_foreground_service_channel")
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(getString(R.string.call_foreground_service_title_android))
            .setContentText(getString(R.string.call_foreground_service_message_android))
            .setContentIntent(pendingIntent)
            .build()
        val notificationId = NotificationIdProvider.getForegroundServiceNotificationId(ForegroundServiceType.ONGOING_CALL)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (includeMediaProjectionType) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        } else {
            0
        }
        runCatchingExceptions {
            ServiceCompat.startForeground(this, notificationId, notification, serviceType)
        }.onFailure {
            Timber.e(it, "Failed to start ongoing call foreground service")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
