/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.snooze

import android.content.Context
import io.element.android.libraries.urbanextras.alarm.UrbanAlarms
import io.element.android.libraries.urbanextras.store.JsonFileStore
import org.json.JSONObject

/** Chats that are muted until a given time. When the time is up, the chat goes back to its normal notification setting. */
class Snoozes private constructor(private val context: Context) {
    private val store = JsonFileStore(context, "snoozes.json")

    @Synchronized
    fun snooze(sessionId: String, roomId: String, untilMillis: Long) {
        val obj = store.readObject()
        obj.put(roomId, JSONObject().put("sessionId", sessionId).put("until", untilMillis))
        store.writeObject(obj)
        UrbanAlarms.set(context, UrbanAlarms.ACTION_UNSNOOZE, roomId, untilMillis)
    }

    @Synchronized
    fun untilMillis(roomId: String): Long? =
        store.readObject().optJSONObject(roomId)?.optLong("until", 0)?.takeIf { it > System.currentTimeMillis() }

    @Synchronized
    fun sessionIdOf(roomId: String): String? =
        store.readObject().optJSONObject(roomId)?.optString("sessionId")?.takeIf { it.isNotEmpty() }

    @Synchronized
    fun clear(roomId: String) {
        val obj = store.readObject()
        obj.remove(roomId)
        store.writeObject(obj)
        UrbanAlarms.cancel(context, UrbanAlarms.ACTION_UNSNOOZE, roomId)
    }

    @Synchronized
    fun rescheduleAll() {
        val obj = store.readObject()
        val now = System.currentTimeMillis()
        obj.keys().forEach { roomId ->
            val until = obj.optJSONObject(roomId)?.optLong("until", 0) ?: return@forEach
            UrbanAlarms.set(context, UrbanAlarms.ACTION_UNSNOOZE, roomId, maxOf(until, now + 2_000))
        }
    }

    companion object {
        @Volatile
        private var instance: Snoozes? = null

        fun get(context: Context): Snoozes = instance ?: synchronized(this) {
            instance ?: Snoozes(context.applicationContext).also { instance = it }
        }
    }
}
