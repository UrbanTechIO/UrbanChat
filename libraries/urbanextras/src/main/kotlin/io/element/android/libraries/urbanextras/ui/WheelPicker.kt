/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import kotlin.math.abs

/**
 * A scrolling "drum" in the style of the iPhone date and time selector: the item in the middle band is the chosen one, and the
 * list snaps to it. [onSelected] is called with the chosen index whenever scrolling settles.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleItems: Int = 5,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val padding = itemHeight * (visibleItems / 2)
    val currentIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            // The item whose centre is closest to the middle of the viewport.
            val viewportCentre = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - viewportCentre) }?.index ?: initialIndex
        }
    }
    val latestOnSelected by rememberUpdatedState(onSelected)
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) latestOnSelected(currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        }
    }
    Box(modifier = modifier.height(itemHeight * visibleItems), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(10.dp)),
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = padding),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items) { index, label ->
                val distance = abs(index - currentIndex)
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .alpha(
                            when (distance) {
                                0 -> 1f
                                1 -> 0.6f
                                2 -> 0.3f
                                else -> 0.15f
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
