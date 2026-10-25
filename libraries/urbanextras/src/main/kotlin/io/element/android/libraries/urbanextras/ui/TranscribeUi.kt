/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.transcribe.ModelState
import io.element.android.libraries.urbanextras.transcribe.TranscriptState
import io.element.android.libraries.urbanextras.transcribe.Transcriber
import io.element.android.libraries.urbanextras.transcribe.WhisperSize

private val LANGUAGE_NAMES = listOf(
    "ar" to "Arabic", "en" to "English", "fr" to "French", "de" to "German", "es" to "Spanish", "tr" to "Turkish",
    "ru" to "Russian", "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "he" to "Hebrew", "fa" to "Persian",
    "ur" to "Urdu", "hi" to "Hindi", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "uk" to "Ukrainian",
    "pl" to "Polish", "id" to "Indonesian",
)

/** The transcript (or progress) shown under a voice message once "Transcribe" has been chosen. Shows nothing for other messages. */
@Composable
fun TranscriptText(eventKey: String?, modifier: Modifier = Modifier) {
    if (eventKey == null) return
    val context = LocalContext.current
    val transcripts by remember { Transcriber.get(context).transcripts }.collectAsState()
    when (val state = transcripts[eventKey]) {
        null -> Unit
        is TranscriptState.Running -> Text(
            text = "Transcribing… ${(state.progress * 100).toInt()}%",
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = modifier.padding(top = 4.dp),
        )
        is TranscriptState.Done -> Text(
            text = state.text,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            modifier = modifier
                .padding(top = 4.dp)
                .clickable { Transcriber.get(context).clearTranscript(eventKey) },
        )
        is TranscriptState.Failed -> Text(
            text = "Could not transcribe: ${state.message}",
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
            modifier = modifier.padding(top = 4.dp),
        )
    }
}

/** Place once in a screen: asks which model and languages to use the first time, and shows the model download. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscribeSetupHost() {
    val context = LocalContext.current
    val transcriber = remember { Transcriber.get(context) }
    val needsSetup by transcriber.needsSetup.collectAsState()
    if (!needsSetup) return
    val size by transcriber.size.collectAsState()
    val languages by transcriber.languages.collectAsState()
    val model by transcriber.model.collectAsState()
    val downloading = model as? ModelState.Downloading

    AlertDialog(
        onDismissRequest = { if (downloading == null) transcriber.dismissSetup() },
        title = { Text("Voice to text") },
        text = {
            Column {
                Text(
                    text = "Voice messages are turned into text on your phone. A speech model has to be downloaded once.",
                    color = ElementTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                WhisperSize.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = downloading == null) { transcriber.chooseSize(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = size == option, onClick = { transcriber.chooseSize(option) }, enabled = downloading == null)
                        Text("${option.label}, about ${option.approxMb} MB", color = ElementTheme.colors.textPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Languages you speak (helps accuracy)", color = ElementTheme.colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LANGUAGE_NAMES.forEach { (code, name) ->
                        FilterChip(
                            selected = code in languages,
                            onClick = {
                                transcriber.setLanguages(if (code in languages) languages - code else languages + code)
                            },
                            label = { Text(name) },
                            enabled = downloading == null,
                        )
                    }
                }
                if (downloading != null) {
                    Spacer(Modifier.height(12.dp))
                    val fraction = if (downloading.total > 0) downloading.downloaded.toFloat() / downloading.total else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${downloading.downloaded / 1_048_576} / ${downloading.total / 1_048_576} MB", color = ElementTheme.colors.textSecondary)
                }
                (model as? ModelState.Failed)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.message, color = ElementTheme.colors.textCriticalPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { transcriber.download() }, enabled = downloading == null) { Text("Download and transcribe") }
        },
        dismissButton = {
            TextButton(onClick = { transcriber.dismissSetup() }, enabled = downloading == null) { Text("Cancel") }
        },
    )
}
