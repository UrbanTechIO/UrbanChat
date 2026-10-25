/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.urbanextras.viewonce.ViewOnceDraft

/**
 * What a view-once photo or video looks like in the chat, for both people: a "Photo" bubble instead of the picture.
 * The person who receives it can tap it once to open it; the sender only sees that it was sent that way.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewOnceCover(
    opened: Boolean,
    isVideo: Boolean,
    isMine: Boolean,
    onOpen: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val kind = if (isVideo) "Video" else "Photo"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .combinedClickable(
                enabled = !opened && !isMine,
                onClick = onOpen,
                onLongClick = onLongClick,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (opened) CompoundIcons.Check() else CompoundIcons.VisibilityOn(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
        )
        Text(
            text = kind,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = when {
                opened -> "Opened"
                isMine -> "View once"
                else -> "Tap to view once"
            },
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

/** A small "Closing in 7s" pill shown over a view-once photo; calls [onFinished] when the time is up. */
@Composable
fun ViewOnceCountdown(seconds: Int, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var remaining by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(seconds) }
    val latestFinished by androidx.compose.runtime.rememberUpdatedState(onFinished)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (remaining > 0) {
            kotlinx.coroutines.delay(1_000)
            remaining--
        }
        latestFinished()
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = CompoundIcons.Time(), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text = "  Closing in ${remaining}s", style = ElementTheme.typography.fontBodyMdMedium, color = Color.White)
    }
}

/** The "View once" switch shown while previewing a photo or video that is about to be sent. */
@Composable
fun ViewOnceChip(modifier: Modifier = Modifier) {
    val enabled by ViewOnceDraft.enabled.collectAsState()
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtleSecondary)
            .combinedClickable(onClick = { ViewOnceDraft.enabled.value = !enabled })
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = "View once",
            style = ElementTheme.typography.fontBodySmMedium,
            color = if (enabled) ElementTheme.colors.textOnSolidPrimary else ElementTheme.colors.textPrimary,
        )
    }
}
