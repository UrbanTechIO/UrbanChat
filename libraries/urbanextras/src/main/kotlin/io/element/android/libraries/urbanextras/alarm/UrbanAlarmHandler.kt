/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.zacsweers.metro.Inject
import io.element.android.libraries.deeplink.api.DeepLinkCreator
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.urbanextras.reminder.Reminders
import io.element.android.libraries.urbanextras.scheduled.ScheduledMessages
import io.element.android.libraries.urbanextras.snooze.Snoozes
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/** Does the work behind every [UrbanAlarms] alarm: sends due messages, shows reminders, ends snoozes. */
@Inject
class UrbanAlarmHandler(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val matrixClientProvider: MatrixClientProvider,
    private val deepLinkCreator: DeepLinkCreator,
    private val activeRoomsHolder: ActiveRoomsHolder,
) {
    fun handle(context: Context, intent: Intent, onDone: () -> Unit) {
        appCoroutineScope.launch {
            try {
                withTimeout(HANDLER_TIMEOUT_MILLIS) { process(context, intent) }
            } catch (_: CancellationException) {
                // timed out: whatever is left is retried by the next alarm
            } catch (t: Throwable) {
                Timber.e(t, "Urban alarm failed")
            } finally {
                onDone()
            }
        }
    }

    private suspend fun process(context: Context, intent: Intent) {
        val id = intent.getStringExtra(UrbanAlarms.EXTRA_ID)
        when (intent.action) {
            UrbanAlarms.ACTION_SEND_SCHEDULED -> if (id != null) sendScheduled(context, id)
            UrbanAlarms.ACTION_REMIND -> if (id != null) remind(context, id)
            UrbanAlarms.ACTION_UNSNOOZE -> if (id != null) unsnooze(context, id)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ScheduledMessages.get(context).rescheduleAll()
                Reminders.get(context).rescheduleAll()
                Snoozes.get(context).rescheduleAll()
            }
        }
    }

    private suspend fun sendScheduled(context: Context, id: String) {
        val scheduled = ScheduledMessages.get(context)
        val message = scheduled.get(id) ?: return
        val sessionId = SessionId(message.sessionId)
        val roomId = RoomId(message.roomId)
        // Use the chat that is already open when there is one, so the message joins the same send queue as the ones typed by hand.
        val room = activeRoomsHolder.getActiveRoomMatching(sessionId, roomId)
            ?: matrixClientProvider.getOrRestore(sessionId).getOrNull()?.getJoinedRoom(roomId)
        Timber.i("Sending scheduled message %s, room available: %s", id, room != null)
        val result = when {
            room == null -> Result.failure(IllegalStateException("Room not available"))
            message.replyToEventId != null -> room.liveTimeline.replyMessage(
                repliedToEventId = EventId(message.replyToEventId),
                body = message.body,
                htmlBody = message.htmlBody,
                intentionalMentions = emptyList(),
            )
            else -> room.liveTimeline.sendMessage(
                body = message.body,
                htmlBody = message.htmlBody,
                intentionalMentions = emptyList(),
            )
        }
        Timber.i("Scheduled message %s send result: %s", id, result.exceptionOrNull()?.message ?: "queued")
        if (result.isSuccess) {
            scheduled.remove(id)
            // The message is queued for sending. Keep the process alive a few seconds so the send queue can deliver it.
            delay(SEND_GRACE_MILLIS)
        } else {
            val updated = scheduled.markAttempt(id)
            if (updated != null && updated.attempts < MAX_ATTEMPTS) {
                UrbanAlarms.set(context, UrbanAlarms.ACTION_SEND_SCHEDULED, id, System.currentTimeMillis() + RETRY_DELAY_MILLIS)
            } else {
                scheduled.remove(id)
                notify(context, id.hashCode(), "A scheduled message could not be sent", message.body.take(120), null)
            }
        }
    }

    private suspend fun remind(context: Context, id: String) {
        val reminders = Reminders.get(context)
        val reminder = reminders.get(id) ?: return
        reminders.remove(id)
        val link = deepLinkCreator.create(SessionId(reminder.sessionId), RoomId(reminder.roomId), null, EventId(reminder.eventId))
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notify(context, id.hashCode(), "Reminder", reminder.snippet.ifBlank { "A message you wanted to be reminded about" }, open)
    }

    private suspend fun unsnooze(context: Context, roomId: String) {
        val snoozes = Snoozes.get(context)
        val sessionId = snoozes.sessionIdOf(roomId) ?: return snoozes.clear(roomId)
        val client = matrixClientProvider.getOrRestore(SessionId(sessionId)).getOrNull() ?: return
        client.notificationSettingsService.restoreDefaultRoomNotificationMode(RoomId(roomId))
        snoozes.clear(roomId)
    }

    private fun notify(context: Context, notificationId: Int, title: String, text: String, open: Intent?) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reminders and scheduled messages", NotificationManager.IMPORTANCE_HIGH))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (open != null) {
            builder.setContentIntent(PendingIntent.getActivity(context, notificationId, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        runCatching { manager.notify(notificationId, builder.build()) }
    }

    private companion object {
        const val CHANNEL_ID = "urban_extras"
        const val HANDLER_TIMEOUT_MILLIS = 25_000L
        const val SEND_GRACE_MILLIS = 6_000L
        const val RETRY_DELAY_MILLIS = 2 * 60 * 1000L
        const val MAX_ATTEMPTS = 6
    }
}
