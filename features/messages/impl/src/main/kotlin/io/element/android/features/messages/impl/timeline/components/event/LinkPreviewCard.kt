/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.urlpreview.LocalUrlPreviewContext
import io.element.android.features.messages.impl.timeline.urlpreview.UrlPreviewLoadState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

@Composable
fun LinkPreviewCard(
    url: String,
    modifier: Modifier = Modifier,
) {
    val urlPreviewContext = LocalUrlPreviewContext.current
    LaunchedEffect(url) {
        urlPreviewContext.coordinator.ensureFetched(url)
    }
    val loadState = urlPreviewContext.coordinator.stateFor(url)
    val context = LocalContext.current
    when (loadState) {
        null, UrlPreviewLoadState.Loading -> {
            Column(
                modifier = modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary),
            ) {
                // Deliberately minimal: a full spinner/skeleton for every link would be noisy
                // while scrolling a timeline full of links.
            }
        }
        UrlPreviewLoadState.Failed -> Unit
        is UrlPreviewLoadState.Loaded -> {
            val info = loadState.info
            Column(
                modifier = modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary)
                    .clickable {
                        runCatchingExceptions {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                        }
                    }
                    .padding(bottom = 8.dp),
            ) {
                val imageMxcUri = info.imageMxcUri
                val siteName = info.siteName
                val title = info.title
                val description = info.description
                if (imageMxcUri != null) {
                    AsyncImage(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        model = MediaRequestData(
                            source = MediaSource(url = imageMxcUri),
                            kind = MediaRequestData.Kind.Thumbnail(800L, 400L),
                        ),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        contentDescription = null,
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!siteName.isNullOrBlank()) {
                        Text(
                            text = siteName,
                            style = ElementTheme.typography.fontBodyXsMedium,
                            color = ElementTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = ElementTheme.typography.fontBodySmMedium,
                            color = ElementTheme.colors.textPrimary,
                            maxLines = 2,
                        )
                    }
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = ElementTheme.typography.fontBodyXsRegular,
                            color = ElementTheme.colors.textSecondary,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
