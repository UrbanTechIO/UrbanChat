/*
 * UrbanChat: the "new version available" popup with download progress.
 */

package io.element.android.x.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.core.update.AppUpdateTrigger
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun AppUpdateDialog() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppUpdater.check(context) }
    DisposableEffect(Unit) {
        AppUpdateTrigger.listener = { AppUpdater.check(context, force = true) }
        onDispose { AppUpdateTrigger.listener = null }
    }
    val state by AppUpdater.state.collectAsState()
    when (val s = state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("Checking for updates") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("Latest version") },
            text = { Text("UrbanChat ${s.version} is the newest version.") },
            confirmButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("OK") } },
        )
        UpdateState.CheckFailed -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("Could not check for updates") },
            text = { Text("Check your connection and try again.") },
            confirmButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("OK") } },
        )
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("UrbanChat ${s.release.version} is available") },
            text = { Notes(s.release.notes) },
            confirmButton = { TextButton(onClick = { AppUpdater.startUpdate(context) }) { Text("Update") } },
            dismissButton = {
                Column {
                    TextButton(onClick = { AppUpdater.dismiss() }) { Text("Later") }
                    TextButton(onClick = { AppUpdater.skipVersion(context) }) { Text("Skip this version") }
                }
            },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading ${s.release.version}") },
            text = {
                Column {
                    val fraction = if (s.total > 0) (s.downloaded.toFloat() / s.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${mb(s.downloaded)} / ${mb(s.total)} MB")
                }
            },
            confirmButton = { TextButton(onClick = { AppUpdater.cancelDownload() }) { Text("Cancel") } },
        )
        is UpdateState.Installing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Installing ${s.release.version}") },
            text = { Text("Confirm the update when Android asks. UrbanChat restarts when it is done.") },
            confirmButton = {},
        )
        is UpdateState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("Allow installing updates") },
            text = { Text("Android needs your permission for UrbanChat to install updates. Turn it on, come back, and check for the update again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppUpdater.openInstallPermissionSettings(context)
                        AppUpdater.dismiss()
                    }
                ) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("Cancel") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss() },
            title = { Text("Update failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { AppUpdater.startUpdate(context) }) { Text("Try again") } },
            dismissButton = { TextButton(onClick = { AppUpdater.dismiss() }) { Text("Close") } },
        )
    }
}

@Composable
private fun Notes(notes: String) {
    if (notes.isBlank()) return
    Column(
        modifier = Modifier
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(notes.replace("**", "").replace("`", ""), color = ElementTheme.colors.textSecondary)
    }
}

private fun mb(bytes: Long) = "%.1f".format(bytes / 1_048_576.0)
