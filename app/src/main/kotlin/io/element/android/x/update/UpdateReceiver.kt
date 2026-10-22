/*
 * UrbanChat: receives the package installer result, and reopens the app after it has been updated.
 */

package io.element.android.x.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppUpdater.ACTION_INSTALL_RESULT -> onInstallResult(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> onUpdated(context)
        }
    }

    private fun onInstallResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android asks the person to confirm the update.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }.onFailure { Timber.w(it, "Could not open the install confirmation") }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> AppUpdater.reportInstallFailure(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
            )
        }
    }

    /** The app has just been replaced: open it again, or leave a notification if Android refuses to start it from the background. */
    private fun onUpdated(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        runCatching { context.startActivity(launch) }.onFailure { Timber.w(it, "Could not reopen the app after the update") }
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("UrbanChat updated")
            .setContentText("Tap to open")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "app_update"
        const val NOTIFICATION_ID = 9001
    }
}
