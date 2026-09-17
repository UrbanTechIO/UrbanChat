/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import io.element.android.features.messages.impl.attachments.preview.imageeditor.EditorTool
import io.element.android.features.messages.impl.attachments.preview.imageeditor.ImageAnnotation
import io.element.android.features.messages.impl.attachments.preview.imageeditor.NormalizedCropRect

sealed interface ShareEvents {
    data object ClearError : ShareEvents
    data object CloseImageEditor : ShareEvents
    data class UpdateImageCropRect(val cropRect: NormalizedCropRect) : ShareEvents
    data object RotateImageToTheLeft : ShareEvents
    data object FlipImageHorizontally : ShareEvents
    data object FlipImageVertically : ShareEvents
    data object ResetImageEdits : ShareEvents
    data class SelectImageEditorTool(val tool: EditorTool) : ShareEvents
    data class SelectImageAnnotationColor(val color: Int) : ShareEvents
    data class SelectImageAnnotationStrokeWidth(val strokeWidth: Float) : ShareEvents
    data class AddImageAnnotation(val annotation: ImageAnnotation) : ShareEvents
    data object UndoImageAnnotation : ShareEvents
    data object ApplyImageEdits : ShareEvents
    data object ClearImageEditError : ShareEvents
    data object RetryAuth : ShareEvents
    data object CancelAuth : ShareEvents
}
