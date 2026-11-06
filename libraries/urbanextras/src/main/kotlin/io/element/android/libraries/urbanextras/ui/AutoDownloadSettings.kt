/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.autodownload.AutoDownloadKind
import io.element.android.libraries.urbanextras.autodownload.AutoDownloadMode
import io.element.android.libraries.urbanextras.autodownload.AutoDownloadRules

private fun AutoDownloadMode.label() = when (this) {
    AutoDownloadMode.Always -> "Always"
    AutoDownloadMode.WifiOnly -> "Wi-Fi only"
    AutoDownloadMode.Never -> "Never"
}

/** Two rows, for photos and videos, each choosing when received media is downloaded automatically. */
@Composable
fun AutoDownloadSettings() {
    AutoDownloadRow(AutoDownloadKind.Photos, "Auto-download photos")
    AutoDownloadRow(AutoDownloadKind.Videos, "Auto-download videos")
}

@Composable
private fun AutoDownloadRow(kind: AutoDownloadKind, title: String) {
    var mode by remember { mutableStateOf(AutoDownloadRules.mode(kind)) }
    var showDialog by remember { mutableStateOf(false) }
    ListItem(
        content = { Text(text = title) },
        supportingContent = { Text(text = mode.label() + ". Applies when Download to Gallery is on.") },
        onClick = { showDialog = true },
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title, color = ElementTheme.colors.textPrimary) },
            text = {
                Column {
                    AutoDownloadMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AutoDownloadRules.setMode(kind, option)
                                    mode = option
                                    showDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == mode, onClick = null)
                            Text(text = option.label(), modifier = Modifier.padding(start = 12.dp), color = ElementTheme.colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Close") } },
        )
    }
}
