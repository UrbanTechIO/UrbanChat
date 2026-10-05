/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import androidx.activity.compose.BackHandler
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.features.lockscreen.impl.biometric.DeviceAuthState
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditorView
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.ProgressDialogType
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.dialogs.AlertDialog
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ListOption
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import kotlinx.collections.immutable.persistentListOf
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun ShareView(
    state: ShareState,
    onShareSuccess: (List<RoomId>) -> Unit,
) {
    val imageEditorState = state.imageEditorState
    BackHandler(enabled = imageEditorState != null) {
        state.eventSink(ShareEvents.CloseImageEditor)
    }
    if (imageEditorState != null) {
        AttachmentImageEditorView(
            state = imageEditorState,
            onCropRectChange = { cropRect -> state.eventSink(ShareEvents.UpdateImageCropRect(cropRect)) },
            onRotateClick = { state.eventSink(ShareEvents.RotateImageToTheLeft) },
            onFlipHorizontallyClick = { state.eventSink(ShareEvents.FlipImageHorizontally) },
            onFlipVerticallyClick = { state.eventSink(ShareEvents.FlipImageVertically) },
            onCancelClick = { state.eventSink(ShareEvents.CloseImageEditor) },
            onResetClick = { state.eventSink(ShareEvents.ResetImageEdits) },
            onDoneClick = { state.eventSink(ShareEvents.ApplyImageEdits) },
            onToolSelect = { tool -> state.eventSink(ShareEvents.SelectImageEditorTool(tool)) },
            onAnnotationColorSelect = { color -> state.eventSink(ShareEvents.SelectImageAnnotationColor(color)) },
            onAnnotationStrokeWidthChange = { width -> state.eventSink(ShareEvents.SelectImageAnnotationStrokeWidth(width)) },
            onAddAnnotation = { annotation -> state.eventSink(ShareEvents.AddImageAnnotation(annotation)) },
            onUndoAnnotation = { state.eventSink(ShareEvents.UndoImageAnnotation) },
        )
    } else {
        AsyncActionView(
            async = state.shareAction,
            onSuccess = {
                onShareSuccess(it)
            },
            onErrorDismiss = {
                state.eventSink(ShareEvents.ClearError)
            },
        )
    }
    when {
        state.isApplyingImageEdits -> {
            ProgressDialog(
                type = ProgressDialogType.Indeterminate,
                text = stringResource(CommonStrings.common_preparing),
                showCancelButton = false,
                onDismissRequest = {},
            )
        }
        state.displayImageEditError -> {
            AlertDialog(
                title = stringResource(CommonStrings.common_error),
                content = stringResource(CommonStrings.common_something_went_wrong_message),
                onDismiss = { state.eventSink(ShareEvents.ClearImageEditError) },
            )
        }
        state.videoQualityPrompt != null -> {
            VideoQualityDialog(
                prompt = state.videoQualityPrompt,
                onSelect = { preset -> state.eventSink(ShareEvents.SelectVideoQuality(preset)) },
                onDismiss = { state.eventSink(ShareEvents.DismissVideoQuality) },
            )
        }
        state.pendingAuthState is DeviceAuthState.Authenticating -> {
            ProgressDialog(
                type = ProgressDialogType.Indeterminate,
                text = stringResource(CommonStrings.common_please_wait),
                showCancelButton = false,
                onDismissRequest = {},
            )
        }
        state.pendingAuthState is DeviceAuthState.Failed -> {
            val failed = state.pendingAuthState
            ConfirmationDialog(
                title = stringResource(
                    if (failed.noDeviceSecurity) {
                        R.string.screen_share_lock_no_device_security_title
                    } else {
                        R.string.screen_share_lock_auth_failed_title
                    }
                ),
                content = if (failed.noDeviceSecurity) {
                    stringResource(R.string.screen_share_lock_no_device_security_subtitle)
                } else {
                    stringResource(CommonStrings.common_something_went_wrong_message)
                },
                submitText = stringResource(CommonStrings.action_retry),
                cancelText = stringResource(CommonStrings.action_cancel),
                onSubmitClick = { state.eventSink(ShareEvents.RetryAuth) },
                onCancelClick = { state.eventSink(ShareEvents.CancelAuth) },
                onDismiss = { state.eventSink(ShareEvents.CancelAuth) },
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ShareViewPreview(@PreviewParameter(ShareStateProvider::class) state: ShareState) = ElementPreview {
    ShareView(
        state = state,
        onShareSuccess = {}
    )
}

/**
 * Lets the user pick the quality of a shared video before sending it, like the HD button on the in-app attachment
 * preview. The first option sends the original; every option is fitted under the server's upload limit if needed.
 */
@Composable
private fun VideoQualityDialog(
    prompt: ShareVideoQualityPrompt,
    onSelect: (VideoCompressionPreset?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val choices = listOf<VideoCompressionPreset?>(
        null,
        VideoCompressionPreset.HIGH,
        VideoCompressionPreset.STANDARD,
        VideoCompressionPreset.LOW,
    )
    // Informational only: the hint always says 100 MB (roughly what Cloudflare lets through). What really limits the
    // upload is the server's own setting, which the video processing reads and fits the video under regardless.
    val maxSizeText = "100 MB"
    val options = persistentListOf(
        ListOption(
            title = stringResource(R.string.screen_share_video_quality_original),
            subtitle = if (prompt.isOverLimit) {
                stringResource(R.string.screen_share_video_quality_original_hint_reduced, maxSizeText)
            } else {
                stringResource(R.string.screen_share_video_quality_original_hint, maxSizeText)
            },
        ),
        ListOption(title = stringResource(R.string.screen_share_video_quality_high)),
        ListOption(title = stringResource(R.string.screen_share_video_quality_standard)),
        ListOption(title = stringResource(R.string.screen_share_video_quality_low)),
    )
    val subtitle = if (prompt.isOverLimit && prompt.fileSizeBytes != null && prompt.maxUploadSizeBytes != null) {
        stringResource(
            R.string.screen_share_video_quality_too_large,
            Formatter.formatFileSize(context, prompt.fileSizeBytes),
            Formatter.formatFileSize(context, prompt.maxUploadSizeBytes),
        )
    } else {
        null
    }
    SingleSelectionDialog(
        title = stringResource(R.string.screen_share_video_quality_title),
        subtitle = subtitle,
        options = options,
        onSelectOption = { index -> onSelect(choices[index]) },
        onDismissRequest = onDismiss,
    )
}
