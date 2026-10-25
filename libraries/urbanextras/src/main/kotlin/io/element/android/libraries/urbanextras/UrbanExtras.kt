/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras

import android.content.Context
import io.element.android.libraries.urbanextras.reminder.Reminders
import io.element.android.libraries.urbanextras.scheduled.ScheduledMessages
import io.element.android.libraries.urbanextras.snooze.Snoozes

/** Entry point: the app calls [init] once when it starts, so presenters anywhere can reach the extras without injection. */
object UrbanExtras {
    lateinit var context: Context
        private set

    fun init(appContext: Context) {
        context = appContext.applicationContext
        // Alarms are lost when the app is updated or the phone restarts, so set them again.
        ScheduledMessages.get(context).rescheduleAll()
        Reminders.get(context).rescheduleAll()
        Snoozes.get(context).rescheduleAll()
    }
}
