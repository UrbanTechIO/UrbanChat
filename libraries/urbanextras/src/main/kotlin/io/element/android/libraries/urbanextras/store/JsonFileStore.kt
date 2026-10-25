/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A tiny JSON file in the app's private storage. Everything in the extras module that must survive a restart is kept this way. */
internal class JsonFileStore(context: Context, name: String) {
    private val file = File(context.applicationContext.filesDir, "urbanextras/$name").also { it.parentFile?.mkdirs() }

    @Synchronized
    fun readArray(): JSONArray = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())

    @Synchronized
    fun writeArray(array: JSONArray) {
        runCatching { file.writeText(array.toString()) }
    }

    @Synchronized
    fun readObject(): JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

    @Synchronized
    fun writeObject(obj: JSONObject) {
        runCatching { file.writeText(obj.toString()) }
    }
}

internal inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { obj -> transform(obj)?.let(out::add) }
    }
    return out
}
