/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.scheduled.ScheduledMessage
import java.text.DateFormat
import java.util.Date

/** A small pill above the composer: "N scheduled". Tap to see and cancel them. */
@Composable
fun ScheduledChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(start = 16.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 1) "1 scheduled message" else "$count scheduled messages",
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesSheet(
    messages: List<ScheduledMessage>,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
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
        ) {
            Text(text = "Scheduled messages", style = ElementTheme.typography.fontHeadingMdBold, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            if (messages.isEmpty()) {
                Text(text = "Nothing is scheduled in this chat.", color = ElementTheme.colors.textSecondary)
            }
            messages.forEach { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = format.format(Date(message.sendAtMillis)),
                            style = ElementTheme.typography.fontBodySmMedium,
                            color = ElementTheme.colors.textSecondary,
                        )
                        Text(
                            text = message.body,
                            maxLines = 2,
                            color = ElementTheme.colors.textPrimary,
                        )
                    }
                    TextButton(onClick = { onCancel(message.id) }) { Text("Cancel") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
