/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.utils.CallAudioOption
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.delay

/** Call duration (or "Calling…" while it hasn't been answered yet). */
@Composable
internal fun CallTimerLabel(
    answeredAtMillis: Long?,
    modifier: Modifier = Modifier,
) {
    // Element Call already shows its own "Calling…" while ringing, so only show the timer once answered.
    if (answeredAtMillis == null) return
    val elapsedSeconds by produceState(initialValue = elapsedSeconds(answeredAtMillis), answeredAtMillis) {
        while (true) {
            value = elapsedSeconds(answeredAtMillis)
            delay(1_000)
        }
    }
    val text = formatCallDuration(elapsedSeconds)
    Text(
        modifier = modifier,
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = Color.White.copy(alpha = 0.8f),
    )
}

private fun elapsedSeconds(answeredAtMillis: Long): Long =
    ((System.currentTimeMillis() - answeredAtMillis) / 1_000).coerceAtLeast(0)

internal fun formatCallDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** Small round button that shows the currently selected audio output and opens the picker. */
@Composable
internal fun CallAudioOutputButton(
    selected: CallAudioOption?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Icon(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        imageVector = selected?.icon() ?: CompoundIcons.VolumeOnSolid(),
        contentDescription = stringResource(R.string.call_audio_output_button),
        tint = Color.White,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
/** Full-width bottom sheet with one large icon per available audio output, WhatsApp style. */
@Composable
internal fun CallAudioOutputSheet(
    options: List<CallAudioOption>,
    selectedId: String?,
    onSelect: (CallAudioOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            options.forEach { option ->
                val isSelected = option.id == selectedId
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtleSecondary
                            )
                            .padding(20.dp),
                        imageVector = option.icon(),
                        contentDescription = null,
                        tint = if (isSelected) ElementTheme.colors.iconOnSolidPrimary else ElementTheme.colors.iconPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = option.label(),
                        style = ElementTheme.typography.fontBodyMdMedium,
                        color = ElementTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CallAudioOption.icon(): ImageVector = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CompoundIcons.VoiceCall()
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CompoundIcons.VolumeOnSolid()
    else -> CompoundIcons.HeadphonesSolid()
}

@Composable
private fun CallAudioOption.label(): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> stringResource(R.string.call_audio_output_earpiece)
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> stringResource(R.string.call_audio_output_speaker)
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST -> name.takeIf { it.isNotBlank() } ?: stringResource(R.string.call_audio_output_bluetooth)
    else -> stringResource(R.string.call_audio_output_wired)
}
