/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.disappearing

import android.content.Context
import io.element.android.libraries.urbanextras.store.JsonFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** The disappearing-messages rule of one chat: messages sent after [sinceMillis] are removed [seconds] after they were sent. 0 means off. */
data class DisappearingRule(val seconds: Long, val sinceMillis: Long, val announcedAtMillis: Long)

/**
 * Every UrbanChat phone keeps this rule per chat. The person who changes the setting sends a normal message that carries an invisible
 * tag (see [DisappearingCodec]); phones that receive it switch their own rule to match. Messages are then hidden on every phone when
 * they expire, and removed for everyone by the phone that sent them.
 */
class DisappearingMessages private constructor(context: Context) {
    private val store = JsonFileStore(context, "disappearing.json")
    private val _rules = MutableStateFlow(read())
    val rules: StateFlow<Map<String, DisappearingRule>> = _rules

    fun rule(roomId: String): DisappearingRule? = _rules.value[roomId]?.takeIf { it.seconds > 0 }

    /** Sets the rule from this phone's own choice, starting now. */
    fun setLocal(roomId: String, seconds: Long) {
        val now = System.currentTimeMillis()
        write(_rules.value + (roomId to DisappearingRule(seconds, now, now)))
    }

    /** A setting announcement arrived (or was read from history) from the chat. Only the newest one counts. */
    fun applyAnnouncement(roomId: String, seconds: Long, sentAtMillis: Long) {
        val current = _rules.value[roomId]
        if (current != null && current.announcedAtMillis >= sentAtMillis) return
        write(_rules.value + (roomId to DisappearingRule(seconds, sentAtMillis, sentAtMillis)))
    }

    /** True when a message sent at [sentAtMillis] in [roomId] should no longer be shown. */
    fun isExpired(roomId: String, sentAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val rule = rule(roomId) ?: return false
        return sentAtMillis >= rule.sinceMillis && nowMillis - sentAtMillis > rule.seconds * 1000
    }

    private fun read(): Map<String, DisappearingRule> {
        val obj = store.readObject()
        val out = HashMap<String, DisappearingRule>()
        obj.keys().forEach { roomId ->
            val r = obj.optJSONObject(roomId) ?: return@forEach
            out[roomId] = DisappearingRule(r.optLong("seconds"), r.optLong("since"), r.optLong("announced"))
        }
        return out
    }

    private fun write(map: Map<String, DisappearingRule>) {
        _rules.value = map
        val obj = JSONObject()
        map.forEach { (roomId, rule) ->
            obj.put(roomId, JSONObject().put("seconds", rule.seconds).put("since", rule.sinceMillis).put("announced", rule.announcedAtMillis))
        }
        store.writeObject(obj)
    }

    companion object {
        val OPTIONS = listOf(
            "Off" to 0L,
            "5 minutes" to 300L,
            "1 hour" to 3_600L,
            "24 hours" to 86_400L,
            "7 days" to 604_800L,
            "90 days" to 7_776_000L,
        )

        @Volatile
        private var instance: DisappearingMessages? = null

        fun get(context: Context): DisappearingMessages = instance ?: synchronized(this) {
            instance ?: DisappearingMessages(context.applicationContext).also { instance = it }
        }

        fun label(seconds: Long): String {
            OPTIONS.firstOrNull { it.second == seconds }?.first?.let { return it }
            val days = seconds / 86_400
            val hours = (seconds % 86_400) / 3_600
            val minutes = (seconds % 3_600) / 60
            return buildList {
                if (days > 0) add(if (days == 1L) "1 day" else "$days days")
                if (hours > 0) add(if (hours == 1L) "1 hour" else "$hours hours")
                if (minutes > 0) add(if (minutes == 1L) "1 minute" else "$minutes minutes")
            }.joinToString(" ").ifEmpty { "$seconds seconds" }
        }
    }
}

/** Hides and reads the setting announcement inside an ordinary message body, using invisible characters. */
object DisappearingCodec {
    private const val MARK = '⁤'
    private val DIGITS = charArrayOf('​', '‌', '‍', '⁠', '⁡', '⁢', '⁣', '﻿', '᠎')

    fun announcementText(seconds: Long): String {
        val visible = if (seconds <= 0) "⏱ Disappearing messages turned off" else "⏱ Disappearing messages: ${DisappearingMessages.label(seconds)}"
        return visible + tag(seconds)
    }

    private fun tag(seconds: Long): String {
        val digits = StringBuilder()
        var n = seconds
        if (n == 0L) digits.append(DIGITS[0])
        while (n > 0) {
            digits.append(DIGITS[(n % 9).toInt()])
            n /= 9
        }
        return "$MARK$MARK$digits$MARK"
    }

    /** The seconds carried by [body], or null when it is an ordinary message. */
    fun decode(body: String): Long? {
        val start = body.indexOf("$MARK$MARK")
        if (start < 0) return null
        val end = body.indexOf(MARK, start + 2)
        if (end < 0) return null
        var value = 0L
        var multiplier = 1L
        for (c in body.substring(start + 2, end)) {
            val d = DIGITS.indexOf(c)
            if (d < 0) return null
            value += d * multiplier
            multiplier *= 9
        }
        return value
    }

    fun strip(body: String): String {
        val start = body.indexOf("$MARK$MARK")
        if (start < 0) return body
        val end = body.indexOf(MARK, start + 2)
        return if (end < 0) body else body.removeRange(start, end + 1)
    }
}
