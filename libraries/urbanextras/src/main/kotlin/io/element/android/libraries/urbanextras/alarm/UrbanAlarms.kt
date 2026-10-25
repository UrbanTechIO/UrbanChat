/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Sets and cancels the alarms that wake the app for scheduled messages, reminders and snooze endings. */
object UrbanAlarms {
    const val ACTION_SEND_SCHEDULED = "io.element.android.urbanextras.SEND_SCHEDULED"
    const val ACTION_REMIND = "io.element.android.urbanextras.REMIND"
    const val ACTION_UNSNOOZE = "io.element.android.urbanextras.UNSNOOZE"
    const val ACTION_SWEEP = "io.element.android.urbanextras.SWEEP"
    const val EXTRA_ID = "id"

    fun set(context: Context, action: String, id: String, atMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, action, id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        }
    }

    fun cancel(context: Context, action: String, id: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, action, id))
    }

    private fun pendingIntent(context: Context, action: String, id: String): PendingIntent {
        val intent = Intent(context, UrbanAlarmReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("urbanextras://$action/$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
