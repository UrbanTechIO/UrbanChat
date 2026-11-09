/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.net.Uri
import android.text.Spanned
import android.text.SpannedString
import android.text.style.URLSpan
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayout
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContentProvider
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.urlpreview.LocalUrlPreviewContext
import io.element.android.features.messages.impl.utils.containsOnlyEmojis
import io.element.android.libraries.androidutils.text.LinkifyHelper
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.utils.LocalUiTestMode
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link

private val MATRIX_ID_IN_LINK = Regex("[@!#$][^\\s/:]+:[^\\s/]+")

@Composable
fun TimelineItemTextView(
    content: TimelineItemTextBasedContent,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    // The View <-> Compose interop is not working well with Compose UI tests (it loops indefinitely), so we skip it in the UI test mode.
    if (LocalUiTestMode.current) return

    val emojiOnly = content.formattedBody.toString() == content.body &&
        content.body.replace(" ", "").containsOnlyEmojis()
    val textStyle = when {
        emojiOnly -> ElementTheme.typography.fontHeadingXlRegular
        else -> ElementTheme.typography.fontBodyLgRegular
    }
    CompositionLocalProvider(
        LocalContentColor provides ElementTheme.colors.textPrimary,
        LocalTextStyle provides textStyle
    ) {
        val text = getTextWithResolvedMentions(content)
        val urlPreviewContext = LocalUrlPreviewContext.current
        // Derived from the content model's own formattedBody rather than the rendered `text`
        // above: that one is rebuilt by mention-span resolution on its own schedule, and doesn't
        // reliably preserve the original URLSpans, which briefly made the preview card flash and
        // then disappear once mentions resolved a moment after the initial render.
        val firstLinkUrl = remember(content.formattedBody) {
            val formattedBody = content.formattedBody
            (formattedBody as? Spanned)?.let { spanned ->
                spanned.getSpans(0, formattedBody.length, URLSpan::class.java)
                    .sortedBy { spanned.getSpanStart(it) }
                    .firstOrNull { span ->
                        // Mentions (@name), room links (#room, !room) and matrix.to links are links too, but a preview of them
                        // would be the page of a server, not of anything the sender shared (for example "Synapse is running").
                        val label = formattedBody.subSequence(spanned.getSpanStart(span), spanned.getSpanEnd(span)).toString()
                        val uri = Uri.parse(span.url)
                        // The permalink base can also be the homeserver's own address, so look for a Matrix id (@user:server, !room:server, #alias:server)
                        // anywhere in the link as well.
                        val decoded = Uri.decode(span.url.orEmpty())
                        // The auto-linker also turns the server part of an id such as "@test:matrix.example.com" into a link of its own.
                        val start = spanned.getSpanStart(span)
                        val partOfMatrixId = start > 0 && formattedBody[start - 1].let { it == ':' || it == '@' }
                        !(label.startsWith("@") || label.startsWith("#") || label.startsWith("!")) &&
                            uri.host?.lowercase() != "matrix.to" &&
                            uri.scheme?.lowercase() != "matrix" &&
                            !MATRIX_ID_IN_LINK.containsMatchIn(decoded) &&
                            !partOfMatrixId
                    }?.url
            }
        }
        Column(modifier.semantics { contentDescription = content.plainText }) {
            // A message with a link: the text view takes every touch, so a long press anywhere on it is caught here and opens the
            // message menu (instead of copying the link, which is what a long press on the link itself used to do).
            val menuOnLongPress = onLongClick != null && firstLinkUrl != null
            Box(if (menuOnLongPress) Modifier.longPressAnywhere { onLongClick?.invoke() } else Modifier) {
                EditorStyledText(
                    text = text,
                    onLinkClickedListener = onLinkClick,
                    onLinkLongClickedListener = if (menuOnLongPress) ({}) else onLinkLongClick,
                    style = ElementRichTextEditorStyle.textStyle(),
                    onTextLayout = ContentAvoidingLayout.measureLegacyLastTextLine(onContentLayoutChange = onContentLayoutChange),
                    releaseOnDetach = false,
                )
            }
            if (urlPreviewContext.enabled && firstLinkUrl != null) {
                LinkPreviewCard(url = firstLinkUrl)
            }
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun getTextWithResolvedMentions(content: TimelineItemTextBasedContent): CharSequence {
    val mentionSpanUpdater = LocalMentionSpanUpdater.current
    val bodyWithResolvedMentions = mentionSpanUpdater.rememberMentionSpans(content.formattedBody)
    return SpannedString.valueOf(bodyWithResolvedMentions)
}

@PreviewsDayNight
@Composable
internal fun TimelineItemTextViewPreview(
    @PreviewParameter(TimelineItemTextBasedContentProvider::class) content: TimelineItemTextBasedContent
) = ElementPreview {
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the first '?' (url: github.com/element-hq/element-x-android/README?)?.")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlAndNestedParenthesisPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the '(ME)' ((url: github.com/element-hq/element-x-android/READ(ME)))!")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
    )
}

/**
 * Calls [onLongPress] when a finger stays down in place for the long press time, anywhere on this view and without
 * consuming the touch, so taps on links and scrolling keep working as before.
 */
private fun Modifier.longPressAnywhere(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        }
        if (ended == null) onLongPress()
    }
}
