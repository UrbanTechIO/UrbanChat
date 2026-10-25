/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

/**
 * Things the app's presenters ask the screen to show, without needing the screen state classes to know about them.
 * A presenter calls [UrbanRequests.post]; [UrbanRequestHost] (placed once in a screen) shows the matching sheet.
 */
sealed interface UrbanRequest {
    /** Ask for a time to be reminded about a message; [onPicked] stores the reminder. */
    data class RemindMe(val onPicked: (Long) -> Unit) : UrbanRequest

    /** Ask for how long to snooze a chat; [onPicked] mutes it and stores the end time. */
    data class Snooze(val onPicked: (Long) -> Unit) : UrbanRequest
}

object UrbanRequests {
    private val _current = MutableStateFlow<UrbanRequest?>(null)
    val current: StateFlow<UrbanRequest?> = _current

    fun post(request: UrbanRequest) {
        _current.value = request
    }

    fun clear() {
        _current.value = null
    }
}

@Composable
fun UrbanRequestHost() {
    val request by UrbanRequests.current.collectAsState()
    when (val r = request) {
        null -> Unit
        is UrbanRequest.RemindMe -> QuickTimeSheet(
            title = "Remind me about this message",
            presets = reminderPresets(),
            onPicked = { at ->
                r.onPicked(at)
                UrbanRequests.clear()
            },
            onDismiss = UrbanRequests::clear,
        )
        is UrbanRequest.Snooze -> QuickTimeSheet(
            title = "Snooze this chat",
            presets = snoozePresets(),
            onPicked = { until ->
                r.onPicked(until)
                UrbanRequests.clear()
            },
            onDismiss = UrbanRequests::clear,
        )
    }
}

data class TimePreset(val label: String, val atMillis: Long)

private fun inHours(hours: Int) = System.currentTimeMillis() + hours * 3_600_000L

private fun nextAt(hour: Int, daysAhead: Int = 0): Long {
    val c = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysAhead)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis <= System.currentTimeMillis() && daysAhead == 0) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

private fun reminderPresets() = listOf(
    TimePreset("In 1 hour", inHours(1)),
    TimePreset("In 3 hours", inHours(3)),
    TimePreset("This evening, 20:00", nextAt(20)),
    TimePreset("Tomorrow, 09:00", nextAt(9, 1)),
)

private fun snoozePresets() = listOf(
    TimePreset("1 hour", inHours(1)),
    TimePreset("8 hours", inHours(8)),
    TimePreset("Until tomorrow, 08:00", nextAt(8, 1)),
    TimePreset("1 week", inHours(24 * 7)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTimeSheet(
    title: String,
    presets: List<TimePreset>,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        DateTimePickerSheet(title = title, confirmLabel = "Set", onConfirm = onPicked, onDismiss = onDismiss)
        return
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
                .padding(horizontal = 24.dp),
        ) {
            Text(text = title, style = ElementTheme.typography.fontHeadingMdBold, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            presets.forEach { preset ->
                TextButton(onClick = { onPicked(preset.atMillis) }, modifier = Modifier.fillMaxWidth()) {
                    Text(preset.label, style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textPrimary)
                }
            }
            TextButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Pick date and time", style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textActionAccent)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
