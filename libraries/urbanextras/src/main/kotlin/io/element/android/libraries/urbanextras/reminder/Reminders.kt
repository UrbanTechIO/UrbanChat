/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.reminder

import android.content.Context
import io.element.android.libraries.urbanextras.alarm.UrbanAlarms
import io.element.android.libraries.urbanextras.store.JsonFileStore
import io.element.android.libraries.urbanextras.store.mapObjects
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Reminder(
    val id: String,
    val sessionId: String,
    val roomId: String,
    val eventId: String,
    val snippet: String,
    val atMillis: Long,
)

/** "Remind me about this message" entries, each fired by an alarm. */
class Reminders private constructor(private val context: Context) {
    private val store = JsonFileStore(context, "reminders.json")
    private var list: List<Reminder> = read()

    @Synchronized
    fun add(sessionId: String, roomId: String, eventId: String, snippet: String, atMillis: Long): Reminder {
        val reminder = Reminder(UUID.randomUUID().toString(), sessionId, roomId, eventId, snippet.take(160), atMillis)
        write(list + reminder)
        UrbanAlarms.set(context, UrbanAlarms.ACTION_REMIND, reminder.id, atMillis)
        return reminder
    }

    @Synchronized
    fun get(id: String): Reminder? = list.firstOrNull { it.id == id }

    @Synchronized
    fun remove(id: String) = write(list.filterNot { it.id == id })

    @Synchronized
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        list.forEach { UrbanAlarms.set(context, UrbanAlarms.ACTION_REMIND, it.id, maxOf(it.atMillis, now + 2_000)) }
    }

    private fun read(): List<Reminder> = store.readArray().mapObjects {
        Reminder(it.getString("id"), it.getString("sessionId"), it.getString("roomId"), it.getString("eventId"), it.getString("snippet"), it.getLong("at"))
    }

    private fun write(newList: List<Reminder>) {
        list = newList
        val array = JSONArray()
        newList.forEach {
            array.put(
                JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("roomId", it.roomId)
                    .put("eventId", it.eventId).put("snippet", it.snippet).put("at", it.atMillis)
            )
        }
        store.writeArray(array)
    }

    companion object {
        @Volatile
        private var instance: Reminders? = null

        fun get(context: Context): Reminders = instance ?: synchronized(this) {
            instance ?: Reminders(context.applicationContext).also { instance = it }
        }
    }
}
