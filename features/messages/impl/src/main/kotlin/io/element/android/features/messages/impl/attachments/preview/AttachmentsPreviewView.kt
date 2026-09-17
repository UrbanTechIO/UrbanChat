/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.preview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.attachments.Attachment
import io.element.android.features.messages.impl.attachments.preview.error.sendAttachmentError
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditorView
import io.element.android.features.messages.impl.attachments.video.MediaOptimizationSelectorEvent
import io.element.android.features.messages.impl.attachments.video.MediaOptimizationSelectorState
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeVideo
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.ProgressDialogType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.AlertDialog
import io.element.android.libraries.designsystem.components.dialogs.RetryDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.theme.floatingDateBadgeBackground
import io.element.android.libraries.mediaviewer.api.local.LocalMediaRenderer
import io.element.android.libraries.textcomposer.TextComposer
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.VoiceMessageState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.formatter.rememberFileSizeFormatter
import io.element.android.wysiwyg.display.TextDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds

/**
 * Ref: https://www.figma.com/design/zftpgS6LjiczobJZ1GUNpt/Updates-to-Media---File-Upload?node-id=51-3514
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsPreviewView(
    state: AttachmentsPreviewState,
    localMediaRenderer: LocalMediaRenderer,
    modifier: Modifier = Modifier,
) {
    val canShowEditAction = when (state.sendActionState) {
        is SendActionState.Sending.Uploading -> false
        is SendActionState.Sending.Processing -> !state.sendActionState.displayProgress
        SendActionState.Done -> false
        else -> true
    }

    fun postSendAttachment() {
        state.eventSink(AttachmentsPreviewEvent.SendAttachment)
    }

    fun postCancel() {
        state.eventSink(AttachmentsPreviewEvent.CancelAndDismiss)
    }

    fun postClearSendState() {
        state.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
    }

    fun postOpenImageEditor() {
        state.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
    }

    fun postCloseImageEditor() {
        state.eventSink(AttachmentsPreviewEvent.CloseImageEditor)
    }

    fun postResetImageEditor() {
        state.eventSink(AttachmentsPreviewEvent.ResetImageEdits)
    }

    fun postApplyImageEdits() {
        state.eventSink(AttachmentsPreviewEvent.ApplyImageEdits)
    }

    BackHandler(enabled = state.sendActionState !is SendActionState.Sending.Uploading && state.sendActionState !is SendActionState.Done) {
        if (state.imageEditorState != null) {
            postCloseImageEditor()
        } else {
            postCancel()
        }
    }

    if (state.imageEditorState != null) {
        AttachmentImageEditorView(
            state = state.imageEditorState,
            onCropRectChange = { cropRect ->
                state.eventSink(AttachmentsPreviewEvent.UpdateImageCropRect(cropRect))
            },
            onRotateClick = { state.eventSink(AttachmentsPreviewEvent.RotateImageToTheLeft) },
            onFlipHorizontallyClick = { state.eventSink(AttachmentsPreviewEvent.FlipImageHorizontally) },
            onFlipVerticallyClick = { state.eventSink(AttachmentsPreviewEvent.FlipImageVertically) },
            onCancelClick = ::postCloseImageEditor,
            onResetClick = ::postResetImageEditor,
            onDoneClick = ::postApplyImageEdits,
            onToolSelect = { tool -> state.eventSink(AttachmentsPreviewEvent.SelectImageEditorTool(tool)) },
            onAnnotationColorSelect = { color -> state.eventSink(AttachmentsPreviewEvent.SelectImageAnnotationColor(color)) },
            onAnnotationStrokeWidthChange = { width -> state.eventSink(AttachmentsPreviewEvent.SelectImageAnnotationStrokeWidth(width)) },
            onAddAnnotation = { annotation -> state.eventSink(AttachmentsPreviewEvent.AddImageAnnotation(annotation)) },
            onUndoAnnotation = { state.eventSink(AttachmentsPreviewEvent.UndoImageAnnotation) },
            modifier = modifier,
        )
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        BackButton(
                            onClick = ::postCancel,
                        )
                    },
                    title = {
                        Text(
                            modifier = Modifier.semantics {
                                heading()
                            },
                            text = stringResource(R.string.screen_media_upload_preview_title),
                        )
                    },
                    actions = {
                        if (state.canEditImage && canShowEditAction) {
                            IconButton(
                                onClick = ::postOpenImageEditor,
                            ) {
                                Icon(
                                    imageVector = CompoundIcons.Edit(),
                                    contentDescription = stringResource(CommonStrings.action_edit),
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            AttachmentPreviewContent(
                modifier = Modifier.padding(paddingValues),
                state = state,
                localMediaRenderer = localMediaRenderer,
                onSendClick = ::postSendAttachment,
            )
        }
    }
    AttachmentSendStateView(
        sendActionState = state.sendActionState,
        isApplyingImageEdits = state.isApplyingImageEdits,
        displayImageEditError = state.displayImageEditError,
        onDismissImageEditError = { state.eventSink(AttachmentsPreviewEvent.ClearImageEditError) },
        onDismissClick = ::postClearSendState,
        onRetryClick = ::postSendAttachment
    )
}

@Composable
private fun AttachmentSendStateView(
    sendActionState: SendActionState,
    isApplyingImageEdits: Boolean,
    displayImageEditError: Boolean,
    onDismissImageEditError: () -> Unit,
    onDismissClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    when {
        isApplyingImageEdits -> {
            ProgressDialog(
                type = ProgressDialogType.Indeterminate,
                text = stringResource(CommonStrings.common_preparing),
                showCancelButton = false,
                onDismissRequest = {},
            )
        }
        displayImageEditError -> {
            AlertDialog(
                title = stringResource(CommonStrings.common_error),
                content = stringResource(CommonStrings.common_something_went_wrong_message),
                onDismiss = onDismissImageEditError,
            )
        }
        else -> when (sendActionState) {
            is SendActionState.Sending.Processing -> {
                if (sendActionState.displayProgress) {
                    ProgressDialog(
                        type = ProgressDialogType.Indeterminate,
                        text = stringResource(CommonStrings.common_preparing),
                        showCancelButton = true,
                        onDismissRequest = onDismissClick,
                    )
                }
            }
            is SendActionState.Sending.Uploading -> {
                ProgressDialog(
                    type = ProgressDialogType.Indeterminate,
                    text = stringResource(id = CommonStrings.common_sending),
                    showCancelButton = true,
                    onDismissRequest = onDismissClick,
                )
            }
            is SendActionState.Failure -> {
                RetryDialog(
                    content = stringResource(sendAttachmentError(sendActionState.error)),
                    onDismiss = onDismissClick,
                    onRetry = onRetryClick
                )
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AttachmentPreviewContent(
    state: AttachmentsPreviewState,
    localMediaRenderer: LocalMediaRenderer,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isGallery) {
                val pagerState = rememberPagerState(
                    initialPage = state.currentIndex,
                    pageCount = { state.attachments.size },
                )
                var isPillVisible by remember { mutableStateOf(true) }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 10.dp,
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val attachment = state.attachments[page]
                        when (attachment) {
                            is Attachment.Media -> {
                                localMediaRenderer.Render(attachment.localMedia)
                            }
                        }
                    }
                }

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.isScrollInProgress }
                        .collectLatest { isScrolling ->
                            if (isScrolling) {
                                isPillVisible = true
                            } else {
                                delay(2000.milliseconds)
                                isPillVisible = false
                            }
                        }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isPillVisible,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(300)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                ) {
                    GalleryCarouselPill(
                        currentIndex = pagerState.currentPage + 1,
                        totalCount = state.totalCount,
                    )
                }

                LaunchedEffect(pagerState.currentPage) {
                    state.eventSink(AttachmentsPreviewEvent.SetCurrentCarouselIndex(pagerState.currentPage))
                }
            } else {
                val firstAttachment = state.attachments.first()
                when (firstAttachment) {
                    is Attachment.Media -> {
                        localMediaRenderer.Render(firstAttachment.localMedia)
                    }
                }
            }
        }
        val mediaInfo = (state.attachments[state.currentIndex] as? Attachment.Media)?.localMedia?.info
        if (mediaInfo?.isImageAttachment() == true) {
            HdToggleChip(state = state.mediaOptimizationSelectorState, isVideo = false)
        } else if (mediaInfo?.mimeType?.isMimeTypeVideo() == true) {
            HdToggleChip(state = state.mediaOptimizationSelectorState, isVideo = true)
        }

        val sizeFormatter = rememberFileSizeFormatter()
        if (state.displayFileTooLargeError) {
            val maxFileUploadSize = state.mediaOptimizationSelectorState.maxUploadSize.dataOrNull()
            if (maxFileUploadSize != null) {
                val content = stringResource(CommonStrings.dialog_file_too_large_to_upload_subtitle, sizeFormatter.format(maxFileUploadSize, true))
                AlertDialog(
                    title = stringResource(CommonStrings.dialog_file_too_large_to_upload_title),
                    content = content,
                    onDismiss = { state.eventSink(AttachmentsPreviewEvent.CancelAndDismiss) },
                )
            }
        }

        AttachmentsPreviewBottomActions(
            state = state,
            onSendClick = onSendClick,
            modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgCanvasDefault)
                .height(IntrinsicSize.Min)
                .imePadding(),
        )
    }
}

/**
 * A single WhatsApp-style "HD" chip: off (default) sends the app's normal compressed quality,
 * tapping it switches this attachment to original/high quality. Applies per-attachment, matching
 * how [MediaOptimizationSelectorState] itself is scoped (one instance per item in the pager).
 */
@Composable
private fun HdToggleChip(state: MediaOptimizationSelectorState, isVideo: Boolean) {
    if (state.displayMediaSelectorViews != true) return
    val isHd = if (isVideo) {
        if (state.isVideoOriginalQuality == null) return
        state.isVideoOriginalQuality
    } else {
        if (state.isImageOptimizationEnabled == null) return
        state.isImageOptimizationEnabled == false
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isHd) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtleSecondary)
                .clickable {
                    if (isVideo) {
                        // "Original quality" bypasses the whole compression pipeline (no size
                        // estimation/upload-limit check applies), matching the image HD toggle.
                        state.eventSink(MediaOptimizationSelectorEvent.SetVideoOriginalQuality(!isHd))
                    } else {
                        state.eventSink(MediaOptimizationSelectorEvent.SelectImageOptimization(isHd))
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_media_upload_preview_hd_toggle),
                style = ElementTheme.typography.fontBodySmMedium,
                color = if (isHd) ElementTheme.colors.textOnSolidPrimary else ElementTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun AttachmentsPreviewBottomActions(
    state: AttachmentsPreviewState,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextComposer(
        modifier = modifier,
        state = state.textEditorState,
        voiceMessageState = VoiceMessageState.Idle,
        composerMode = MessageComposerMode.Attachment,
        onRequestFocus = {},
        onSendMessage = onSendClick,
        showTextFormatting = false,
        onResetComposerMode = {},
        onAddAttachment = {},
        onDismissTextFormatting = {},
        onVoiceRecorderEvent = {},
        onVoicePlayerEvent = {},
        onSendVoiceMessage = {},
        onDeleteVoiceMessage = {},
        onReceiveSuggestion = {},
        resolveMentionDisplay = { _, _ -> TextDisplay.Plain },
        resolveAtRoomMentionDisplay = { TextDisplay.Plain },
        onError = {},
        onTyping = {},
        onSelectRichContent = {},
    )
}

// Only preview in dark, dark theme is forced on the Node.
@Preview
@Composable
internal fun AttachmentsPreviewViewPreview(@PreviewParameter(AttachmentsPreviewStateProvider::class) state: AttachmentsPreviewState) = ElementPreviewDark {
    AttachmentsPreviewView(
        state = state,
        localMediaRenderer = SampleMediaRenderer(),
    )
}

@Preview
@Composable
internal fun AttachmentsPreviewGalleryViewPreview() = ElementPreviewDark {
    AttachmentsPreviewView(
        state = anAttachmentsPreviewGalleryState(),
        localMediaRenderer = SampleMediaRenderer(),
    )
}

@Composable
internal fun GalleryCarouselPill(
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ElementTheme.colors.floatingDateBadgeBackground,
        shadowElevation = 4.dp,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(R.string.screen_media_upload_preview_item_count, currentIndex, totalCount),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textPrimary,
        )
    }
}
