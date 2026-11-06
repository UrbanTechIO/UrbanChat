/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.translate

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import io.element.android.libraries.urbanextras.UrbanExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What came of translating one message. */
sealed interface TranslationOutcome {
    data class Done(val text: String, val from: String, val to: String, val online: Boolean) : TranslationOutcome

    /** The message is already in the chosen language. */
    data class SameLanguage(val language: String) : TranslationOutcome

    /** The message's language or the chosen language is not one the on-device translation knows. */
    data object Unsupported : TranslationOutcome

    data object Failed : TranslationOutcome
}

data class TranslateLanguageItem(val tag: String, val name: String)

/**
 * Translates a message into a language the user picks. Two engines:
 * - Online (the default): Google Translate's web service, much better than the on-device one, especially for Arabic.
 *   It needs the connection, and the text of the message is sent to Google.
 * - On the phone: Google's ML Kit models (about 30 MB per language, downloaded the first time). Nothing leaves the phone,
 *   but the quality is basic. It is also the fallback when the online engine cannot be reached.
 */
object MessageTranslator {
    private fun prefs() = UrbanExtras.context.getSharedPreferences("urban_translate", Context.MODE_PRIVATE)

    /** The language messages are translated into: the last one picked, otherwise the phone's language, otherwise English. */
    var targetTag: String
        get() = prefs().getString("target", null)
            ?: Locale.getDefault().language.takeIf { TranslateLanguage.fromLanguageTag(it) != null }
            ?: TranslateLanguage.ENGLISH
        set(value) {
            prefs().edit().putString("target", value).apply()
        }

    /** Whether the online engine is used (default) rather than only the one on the phone. */
    var online: Boolean
        get() = prefs().getBoolean("online", true)
        set(value) {
            prefs().edit().putBoolean("online", value).apply()
        }

    /** The languages to pick from, by name. */
    fun languages(): List<TranslateLanguageItem> = TranslateLanguage.getAllLanguages()
        .map { TranslateLanguageItem(it, displayName(it)) }
        .sortedBy { it.name }

    suspend fun translate(text: String, targetTag: String = this.targetTag, online: Boolean = this.online): TranslationOutcome {
        if (online) {
            val result = withContext(Dispatchers.IO) { runCatching { translateOnline(text, targetTag) }.onFailure { Timber.w(it, "Online translation failed") } }
            result.getOrNull()?.let { return it }
            // Could not reach the online engine: try the one on the phone.
        }
        return translateOnDevice(text, targetTag)
    }

    private fun translateOnline(text: String, targetTag: String): TranslationOutcome {
        val target = if (targetTag == "zh") "zh-CN" else targetTag
        val body = "client=gtx&sl=auto&tl=${URLEncoder.encode(target, "UTF-8")}&dt=t&dj=1&q=${URLEncoder.encode(text, "UTF-8")}"
        val connection = URL("https://translate.googleapis.com/translate_a/single").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val json = JSONObject(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
            val sentences = json.getJSONArray("sentences")
            val translated = buildString {
                for (i in 0 until sentences.length()) append(sentences.getJSONObject(i).optString("trans"))
            }
            val source = json.optString("src")
            if (translated.isBlank()) error("Empty translation")
            if (source.substringBefore('-') == targetTag.substringBefore('-')) return TranslationOutcome.SameLanguage(displayName(targetTag))
            return TranslationOutcome.Done(translated, displayName(source), displayName(targetTag), online = true)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun translateOnDevice(text: String, targetTag: String): TranslationOutcome {
        return try {
            val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return TranslationOutcome.Unsupported
            val detected = LanguageIdentification.getClient().identifyLanguage(text).await()
            if (detected == "und") return TranslationOutcome.Failed
            val source = TranslateLanguage.fromLanguageTag(detected) ?: return TranslationOutcome.Unsupported
            if (source == target) return TranslationOutcome.SameLanguage(displayName(target))
            val translator = Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
            )
            try {
                translator.downloadModelIfNeeded().await()
                TranslationOutcome.Done(translator.translate(text).await(), displayName(source), displayName(target), online = false)
            } finally {
                translator.close()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "On-device translation failed")
            TranslationOutcome.Failed
        }
    }

    private fun displayName(tag: String): String =
        Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }.ifEmpty { tag }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}
