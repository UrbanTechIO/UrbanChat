/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaDownloadProgress
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.roundToInt

/**
 * Shown in the top end corner of a media message while it is being uploaded: a ring that fills as the upload progresses
 * (spinning when the progress is not known yet) with a cross inside that cancels the upload. It disappears once the
 * message is sent, because the send state is no longer [LocalEventSendState.Sending].
 */
@Composable
fun UploadProgressOverlay(
    sendState: LocalEventSendState.Sending,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (sendState as? LocalEventSendState.Sending.MediaWithProgress)
        ?.takeIf { it.total > 0 }
        ?.let { (it.progress.toFloat() / it.total.toFloat()).coerceIn(0f, 1f) }
    TransferProgressOverlay(
        fraction = fraction,
        label = null,
        onCancelClick = onCancelClick,
        modifier = modifier,
    )
}

/**
 * Shown in the top end corner of a media message while it is being downloaded: the same ring with a cross that cancels
 * it, and the downloaded and full sizes (for example "12/82 MB") next to it.
 */
@Composable
fun DownloadProgressOverlay(
    progress: MediaDownloadProgress.Progress?,
    fallbackTotalBytes: Long?,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = progress?.totalBytes ?: fallbackTotalBytes
    val fraction = progress?.downloadedBytes?.let { downloaded ->
        total?.takeIf { it > 0 }?.let { (downloaded.toFloat() / it).coerceIn(0f, 1f) }
    }
    val label = if (progress != null && total != null && total > 0) {
        val totalMb = total / (1024f * 1024f)
        val downloadedMb = (progress.downloadedBytes / (1024f * 1024f)).coerceAtMost(totalMb)
        "${downloadedMb.roundToInt()}/${totalMb.roundToInt()} MB"
    } else {
        null
    }
    TransferProgressOverlay(
        fraction = fraction,
        label = label,
        onCancelClick = onCancelClick,
        modifier = modifier,
    )
}

@Composable
private fun TransferProgressOverlay(
    fraction: Float?,
    label: String?,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = ElementTheme.typography.fontBodyXsMedium,
                color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onCancelClick),
            contentAlignment = Alignment.Center,
        ) {
            if (fraction != null) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(CommonStrings.action_cancel),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
