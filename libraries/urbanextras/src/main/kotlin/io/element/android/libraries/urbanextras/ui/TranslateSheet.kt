/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.translate.MessageTranslator
import io.element.android.libraries.urbanextras.translate.TranslationOutcome

/** Shows the original message and its translation, with the language to translate into and the engine to use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSheet(text: String, onDismiss: () -> Unit) {
    var targetTag by remember { mutableStateOf(MessageTranslator.targetTag) }
    var online by remember { mutableStateOf(MessageTranslator.online) }
    var pickingLanguage by remember { mutableStateOf(false) }
    val languages = remember { MessageTranslator.languages() }
    val targetName = languages.firstOrNull { it.tag == targetTag }?.name ?: targetTag
    val outcome by produceState<TranslationOutcome?>(null, text, targetTag, online) {
        value = null
        value = MessageTranslator.translate(text, targetTag, online)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { pickingLanguage = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Translate to", color = ElementTheme.colors.textSecondary)
                Text("$targetName  ▾", style = ElementTheme.typography.fontBodyLgMedium, color = ElementTheme.colors.textActionAccent)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Better quality (online)", color = ElementTheme.colors.textPrimary)
                    Text(
                        if (online) "Sends this message to Google to translate it." else "Translated on the phone only. Basic quality.",
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                Switch(checked = online, onCheckedChange = {
                    online = it
                    MessageTranslator.online = it
                })
            }
            Spacer(Modifier.height(16.dp))
            when (val result = outcome) {
                null -> CircularProgressIndicator()
                is TranslationOutcome.Done -> {
                    Text(result.to, style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
                    Text(result.text, style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text(result.from, style = ElementTheme.typography.fontBodySmMedium, color = ElementTheme.colors.textSecondary)
                    Text(text, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
                    if (online && !result.online) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The online service could not be reached, so this was translated on the phone.",
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                }
                is TranslationOutcome.SameLanguage ->
                    Text("This message is already in ${result.language}.", color = ElementTheme.colors.textPrimary)
                TranslationOutcome.Unsupported ->
                    Text("This language cannot be translated on the phone. Turn on the online option.", color = ElementTheme.colors.textPrimary)
                TranslationOutcome.Failed ->
                    Text("Could not translate. Check the connection and try again.", color = ElementTheme.colors.textPrimary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickingLanguage) {
        AlertDialog(
            onDismissRequest = { pickingLanguage = false },
            title = { Text("Translate to", color = ElementTheme.colors.textPrimary) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(languages, key = { it.tag }) { language ->
                        Text(
                            text = language.name,
                            style = ElementTheme.typography.fontBodyLgRegular,
                            color = if (language.tag == targetTag) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetTag = language.tag
                                    MessageTranslator.targetTag = language.tag
                                    pickingLanguage = false
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingLanguage = false }) { Text("Close") } },
        )
    }
}
