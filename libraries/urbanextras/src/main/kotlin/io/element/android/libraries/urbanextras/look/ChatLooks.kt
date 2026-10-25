/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.look

import android.content.Context
import io.element.android.libraries.urbanextras.store.JsonFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** How one chat looks on this phone. Null fields mean "use the app-wide setting". */
data class ChatLook(
    /** ARGB colour used to tint the chat's glass and bubbles. */
    val tintArgb: Int? = null,
    /** One of [ChatLooks.BACKGROUND_PRESETS], or "photo" when [photoPath] is set. Null for the normal background. */
    val background: String? = null,
    val photoPath: String? = null,
    /** 0 = solid bubbles, 1 = very see-through. */
    val bubbleOpacity: Float? = null,
    /** Notification sound for this chat (a content or file uri string); null for the default. */
    val soundUri: String? = null,
) {
    val isDefault: Boolean get() = tintArgb == null && background == null && bubbleOpacity == null && soundUri == null
}

class ChatLooks private constructor(private val context: Context) {
    private val store = JsonFileStore(context, "chat_looks.json")
    private val _looks = MutableStateFlow(read())
    val looks: StateFlow<Map<String, ChatLook>> = _looks

    fun lookOf(roomId: String): ChatLook = _looks.value[roomId] ?: ChatLook()

    /** The background style chosen for every chat in the theme settings, if any. */
    fun globalBackground(): String? = _looks.value[GLOBAL_KEY]?.background

    fun update(roomId: String, transform: (ChatLook) -> ChatLook) {
        val newLook = transform(lookOf(roomId))
        write(if (newLook.isDefault) _looks.value - roomId else _looks.value + (roomId to newLook))
    }

    fun reset(roomId: String) {
        lookOf(roomId).photoPath?.let { runCatching { File(it).delete() } }
        write(_looks.value - roomId)
    }

    /** Copies a picked photo into private storage and returns its path. */
    fun importPhoto(roomId: String, uri: android.net.Uri): String? = runCatching {
        val dir = File(context.filesDir, "chat_backgrounds").apply { mkdirs() }
        val target = File(dir, "${roomId.hashCode()}-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        lookOf(roomId).photoPath?.let { runCatching { File(it).delete() } }
        target.absolutePath
    }.getOrNull()

    private fun read(): Map<String, ChatLook> {
        val obj = store.readObject()
        val out = HashMap<String, ChatLook>()
        obj.keys().forEach { roomId ->
            val r = obj.optJSONObject(roomId) ?: return@forEach
            out[roomId] = ChatLook(
                tintArgb = if (r.has("tint")) r.getInt("tint") else null,
                background = r.optString("background").takeIf { it.isNotEmpty() },
                photoPath = r.optString("photo").takeIf { it.isNotEmpty() },
                bubbleOpacity = if (r.has("opacity")) r.getDouble("opacity").toFloat() else null,
                soundUri = r.optString("sound").takeIf { it.isNotEmpty() },
            )
        }
        return out
    }

    private fun write(map: Map<String, ChatLook>) {
        _looks.value = map
        val obj = JSONObject()
        map.forEach { (roomId, look) ->
            val r = JSONObject()
            look.tintArgb?.let { r.put("tint", it) }
            look.background?.let { r.put("background", it) }
            look.photoPath?.let { r.put("photo", it) }
            look.bubbleOpacity?.let { r.put("opacity", it.toDouble()) }
            look.soundUri?.let { r.put("sound", it) }
            obj.put(roomId, r)
        }
        store.writeObject(obj)
    }

    companion object {
        /** Key under which the app-wide (all chats) look is stored. */
        const val GLOBAL_KEY = "*"

        /** What a chat really looks like: its own choices, with the app-wide background style filling in what it leaves open. */
        fun effective(looks: Map<String, ChatLook>, roomId: String): ChatLook {
            val own = looks[roomId] ?: ChatLook()
            val global = looks[GLOBAL_KEY]
            return if (own.background == null && global?.background != null) own.copy(background = global.background) else own
        }

        /** Built-in backgrounds: gradients drawn by the app, so nothing needs to be downloaded. */
        val BACKGROUND_PRESETS = listOf("aurora", "sunset", "ocean", "forest", "midnight", "rose")

        @Volatile
        private var instance: ChatLooks? = null

        fun get(context: Context): ChatLooks = instance ?: synchronized(this) {
            instance ?: ChatLooks(context.applicationContext).also { instance = it }
        }
    }
}
