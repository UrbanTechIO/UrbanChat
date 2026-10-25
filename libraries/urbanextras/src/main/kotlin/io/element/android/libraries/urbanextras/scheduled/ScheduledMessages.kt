/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.scheduled

import android.content.Context
import io.element.android.libraries.urbanextras.alarm.UrbanAlarms
import io.element.android.libraries.urbanextras.store.JsonFileStore
import io.element.android.libraries.urbanextras.store.mapObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScheduledMessage(
    val id: String,
    val sessionId: String,
    val roomId: String,
    val body: String,
    val htmlBody: String?,
    val replyToEventId: String?,
    val sendAtMillis: Long,
    val attempts: Int = 0,
    val createdAtMillis: Long = sendAtMillis - 3_600_000L,
)

/** Messages waiting for their send time. A due message is sent by [io.element.android.libraries.urbanextras.alarm.UrbanAlarmHandler]. */
class ScheduledMessages private constructor(private val context: Context) {
    private val store = JsonFileStore(context, "scheduled_messages.json")
    private val _messages = MutableStateFlow(read())
    val messages: StateFlow<List<ScheduledMessage>> = _messages

    fun forRoom(roomId: String): List<ScheduledMessage> = _messages.value.filter { it.roomId == roomId }.sortedBy { it.sendAtMillis }

    fun get(id: String): ScheduledMessage? = _messages.value.firstOrNull { it.id == id }

    fun schedule(sessionId: String, roomId: String, body: String, htmlBody: String?, replyToEventId: String?, atMillis: Long): ScheduledMessage {
        val message = ScheduledMessage(UUID.randomUUID().toString(), sessionId, roomId, body, htmlBody, replyToEventId, atMillis, 0, System.currentTimeMillis())
        write(_messages.value + message)
        UrbanAlarms.set(context, UrbanAlarms.ACTION_SEND_SCHEDULED, message.id, atMillis)
        return message
    }

    fun cancel(id: String) {
        UrbanAlarms.cancel(context, UrbanAlarms.ACTION_SEND_SCHEDULED, id)
        write(_messages.value.filterNot { it.id == id })
    }

    internal fun markAttempt(id: String): ScheduledMessage? {
        val updated = _messages.value.map { if (it.id == id) it.copy(attempts = it.attempts + 1) else it }
        write(updated)
        return updated.firstOrNull { it.id == id }
    }

    internal fun remove(id: String) = write(_messages.value.filterNot { it.id == id })

    /** Sets the alarms again, for example after a reboot or when the clock changes. Messages already overdue go out straight away. */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        _messages.value.forEach {
            UrbanAlarms.set(context, UrbanAlarms.ACTION_SEND_SCHEDULED, it.id, maxOf(it.sendAtMillis, now + 2_000))
        }
    }

    private fun read(): List<ScheduledMessage> = store.readArray().mapObjects {
        ScheduledMessage(
            id = it.getString("id"),
            sessionId = it.getString("sessionId"),
            roomId = it.getString("roomId"),
            body = it.getString("body"),
            htmlBody = it.optString("htmlBody").takeIf { html -> html.isNotEmpty() },
            replyToEventId = it.optString("replyTo").takeIf { reply -> reply.isNotEmpty() },
            sendAtMillis = it.getLong("sendAt"),
            attempts = it.optInt("attempts"),
            createdAtMillis = it.optLong("created", it.getLong("sendAt") - 3_600_000L),
        )
    }

    private fun write(list: List<ScheduledMessage>) {
        _messages.value = list
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id).put("sessionId", it.sessionId).put("roomId", it.roomId).put("body", it.body)
                    .put("htmlBody", it.htmlBody.orEmpty()).put("replyTo", it.replyToEventId.orEmpty())
                    .put("sendAt", it.sendAtMillis).put("attempts", it.attempts).put("created", it.createdAtMillis)
            )
        }
        store.writeArray(array)
    }

    companion object {
        @Volatile
        private var instance: ScheduledMessages? = null

        fun get(context: Context): ScheduledMessages = instance ?: synchronized(this) {
            instance ?: ScheduledMessages(context.applicationContext).also { instance = it }
        }
    }
}
