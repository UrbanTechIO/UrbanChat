/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.viewonce

import android.content.Context
import io.element.android.libraries.urbanextras.store.JsonFileStore
import org.json.JSONArray

/**
 * View-once photos and videos. The sender picks "view once" before sending, and the file is sent with a marked file name.
 * UrbanChat on the other side hides such a picture behind a "tap to view once" cover, and after it has been opened the cover stays.
 */
class ViewOnce private constructor(context: Context) {
    private val store = JsonFileStore(context, "view_once_opened.json")
    private var opened: MutableSet<String> = store.readArray().let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() }

    @Synchronized
    fun isOpened(eventId: String): Boolean = eventId in opened

    @Synchronized
    fun markOpened(eventId: String) {
        if (opened.add(eventId)) store.writeArray(JSONArray(opened.toList()))
    }

    companion object {
        const val PREFIX = "UCVO-"

        fun isViewOnce(filename: String?): Boolean = filename?.startsWith(PREFIX) == true

        fun mark(filename: String): String = if (filename.startsWith(PREFIX)) filename else PREFIX + filename

        @Volatile
        private var instance: ViewOnce? = null

        fun get(context: Context): ViewOnce = instance ?: synchronized(this) {
            instance ?: ViewOnce(context.applicationContext).also { instance = it }
        }
    }
}
