/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAYS_AHEAD = 366

/**
 * A bottom sheet with an iPhone-style date and time selector. [onConfirm] gets the chosen moment in epoch milliseconds,
 * which is always in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerSheet(
    title: String,
    confirmLabel: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    startInMinutes: Int = 60,
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val start = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, startInMinutes) } }
    val today = remember { Calendar.getInstance() }
    val dayLabels = remember {
        val format = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        List(DAYS_AHEAD) { offset ->
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            when (offset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> format.format(day.time)
            }
        }
    }
    val hourLabels = remember(is24Hour) {
        if (is24Hour) List(24) { "%02d".format(it) } else List(12) { (if (it == 0) 12 else it).toString() }
    }
    val minuteLabels = remember { List(60) { "%02d".format(it) } }

    val startHour = start.get(Calendar.HOUR_OF_DAY)
    var dayIndex by remember { mutableIntStateOf(if (start.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) 0 else 1) }
    var hourIndex by remember { mutableIntStateOf(if (is24Hour) startHour else startHour % 12) }
    var minuteIndex by remember { mutableIntStateOf(start.get(Calendar.MINUTE)) }
    var isPm by remember { mutableIntStateOf(if (startHour >= 12) 1 else 0) }

    val chosen = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayIndex)
        val hour24 = if (is24Hour) hourIndex else (hourIndex % 12) + if (isPm == 1) 12 else 0
        set(Calendar.HOUR_OF_DAY, hour24)
        set(Calendar.MINUTE, minuteIndex)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val isInFuture = chosen.timeInMillis > System.currentTimeMillis() + 30_000
    val summaryFormat = remember(is24Hour) { SimpleDateFormat(if (is24Hour) "EEEE d MMMM, HH:mm" else "EEEE d MMMM, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = ElementTheme.typography.fontHeadingMdBold, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelPicker(items = dayLabels, initialIndex = dayIndex, onSelected = { dayIndex = it }, modifier = Modifier.weight(2.2f))
                WheelPicker(items = hourLabels, initialIndex = hourIndex, onSelected = { hourIndex = it }, modifier = Modifier.weight(1f))
                WheelPicker(items = minuteLabels, initialIndex = minuteIndex, onSelected = { minuteIndex = it }, modifier = Modifier.weight(1f))
                if (!is24Hour) {
                    WheelPicker(items = listOf("AM", "PM"), initialIndex = isPm, onSelected = { isPm = it }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isInFuture) summaryFormat.format(Date(chosen.timeInMillis)) else "Pick a time in the future",
                style = ElementTheme.typography.fontBodyMdRegular,
                color = if (isInFuture) ElementTheme.colors.textSecondary else ElementTheme.colors.textCriticalPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onConfirm(chosen.timeInMillis) }, enabled = isInFuture) { Text(confirmLabel) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A bottom sheet with two wheels, hours and minutes, for picking a length of time. [onConfirm] gets the total in seconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Disappear after",
) {
    val hourLabels = remember { List(73) { if (it == 1) "1 hour" else "$it hours" } }
    val minuteLabels = remember { List(60) { if (it == 1) "1 minute" else "$it minutes" } }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(30) }
    val seconds = hours * 3_600L + minutes * 60L
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ElementTheme.colors.bgCanvasDefault,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, style = ElementTheme.typography.fontHeadingMdBold, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelPicker(items = hourLabels, initialIndex = hours, onSelected = { hours = it }, modifier = Modifier.weight(1f))
                WheelPicker(items = minuteLabels, initialIndex = minutes, onSelected = { minutes = it }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onConfirm(seconds) }, enabled = seconds >= 60) { Text("Set") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
