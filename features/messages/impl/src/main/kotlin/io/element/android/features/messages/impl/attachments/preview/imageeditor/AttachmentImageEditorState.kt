/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.preview.imageeditor

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import io.element.android.libraries.mediaviewer.api.local.LocalMedia

private const val DEFAULT_CROP_MARGIN = 0f
private const val MIN_CROP_SIZE = 0.1f

@Immutable
data class AttachmentImageEditorState(
    val localMedia: LocalMedia,
    val edits: AttachmentImageEdits,
    val selectedTool: EditorTool = EditorTool.Crop,
    val annotationColor: Int = AnnotationColors.DEFAULT,
    val annotationStrokeWidth: Float = AnnotationDefaults.DEFAULT_STROKE_WIDTH,
    // For preview only
    val previewDebug: Boolean,
)

enum class EditorTool {
    Crop,
    Pen,
    Arrow,
    Text,
    Blur,
}

object AnnotationDefaults {
    // Stroke widths are normalized against the smallest image dimension.
    // The slider picks a value in [MIN_STROKE_WIDTH, MAX_STROKE_WIDTH]; arrows are drawn a bit
    // thicker than a pen stroke of the same slider value so the arrowhead stays legible.
    const val MIN_STROKE_WIDTH = 0.003f
    const val MAX_STROKE_WIDTH = 0.028f
    const val DEFAULT_STROKE_WIDTH = 0.008f
    const val ARROW_STROKE_WIDTH_FACTOR = 1.3f
    const val TEXT_SIZE = 0.07f
}

object AnnotationColors {
    const val RED = 0xFFFF3B30.toInt()
    const val YELLOW = 0xFFFFCC00.toInt()
    const val GREEN = 0xFF34C759.toInt()
    const val BLUE = 0xFF0A84FF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val DEFAULT = RED

    val all = listOf(RED, YELLOW, GREEN, BLUE, BLACK, WHITE)
}

@Immutable
data class AttachmentImageEdits(
    val cropRect: NormalizedCropRect = NormalizedCropRect.default(),
    val rotationQuarterTurns: Int = 0,
    val isFlippedHorizontally: Boolean = false,
    val isFlippedVertically: Boolean = false,
    val annotations: List<ImageAnnotation> = emptyList(),
) {
    val normalizedRotationQuarterTurns: Int
        get() = rotationQuarterTurns % 4

    val rotationDegrees: Int
        get() = normalizedRotationQuarterTurns * 90

    val hasChanges: Boolean
        get() = cropRect != NormalizedCropRect.default() ||
            normalizedRotationQuarterTurns != 0 ||
            isFlippedHorizontally ||
            isFlippedVertically ||
            annotations.isNotEmpty()

    fun rotateAntiClockwise(): AttachmentImageEdits {
        return copy(
            rotationQuarterTurns = (normalizedRotationQuarterTurns + 3) % 4,
            // Also update the crop rect and annotations to keep the same selected area
            cropRect = cropRect.rotateAntiClockwise(),
            annotations = annotations.map { it.rotateAntiClockwise() },
        )
    }

    fun flipHorizontally(): AttachmentImageEdits {
        return copy(
            isFlippedHorizontally = !isFlippedHorizontally,
            // Also update the crop rect and annotations to keep the same selected area
            cropRect = cropRect.flipHorizontally(),
            annotations = annotations.map { it.flipHorizontally() },
        )
    }

    fun flipVertically(): AttachmentImageEdits {
        return copy(
            isFlippedVertically = !isFlippedVertically,
            // Also update the crop rect and annotations to keep the same selected area
            cropRect = cropRect.flipVertically(),
            annotations = annotations.map { it.flipVertically() },
        )
    }
}

@Immutable
data class NormalizedPoint(
    @FloatRange(from = 0.0, to = 1.0) val x: Float,
    @FloatRange(from = 0.0, to = 1.0) val y: Float,
) {
    fun rotateAntiClockwise() = NormalizedPoint(x = y, y = 1f - x)
    fun flipHorizontally() = copy(x = 1f - x)
    fun flipVertically() = copy(y = 1f - y)
}

/**
 * Annotations drawn on top of the image. Coordinates are normalized (0..1) in the
 * transformed image space, i.e. after rotation/flip have been applied — which is
 * also the space the user sees while drawing. Sizes ([ImageAnnotation.Stroke.strokeWidth],
 * [ImageAnnotation.Text.textSize]) are normalized against the smallest image dimension
 * so they stay stable across rotations.
 */
@Immutable
sealed interface ImageAnnotation {
    fun rotateAntiClockwise(): ImageAnnotation
    fun flipHorizontally(): ImageAnnotation
    fun flipVertically(): ImageAnnotation

    @Immutable
    data class Stroke(
        val points: List<NormalizedPoint>,
        val color: Int,
        val strokeWidth: Float,
    ) : ImageAnnotation {
        override fun rotateAntiClockwise() = copy(points = points.map { it.rotateAntiClockwise() })
        override fun flipHorizontally() = copy(points = points.map { it.flipHorizontally() })
        override fun flipVertically() = copy(points = points.map { it.flipVertically() })
    }

    @Immutable
    data class Arrow(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val color: Int,
        val strokeWidth: Float,
    ) : ImageAnnotation {
        override fun rotateAntiClockwise() = copy(start = start.rotateAntiClockwise(), end = end.rotateAntiClockwise())
        override fun flipHorizontally() = copy(start = start.flipHorizontally(), end = end.flipHorizontally())
        override fun flipVertically() = copy(start = start.flipVertically(), end = end.flipVertically())
    }

    @Immutable
    data class Text(
        val center: NormalizedPoint,
        val text: String,
        val color: Int,
        val textSize: Float,
    ) : ImageAnnotation {
        override fun rotateAntiClockwise() = copy(center = center.rotateAntiClockwise())
        override fun flipHorizontally() = copy(center = center.flipHorizontally())
        override fun flipVertically() = copy(center = center.flipVertically())
    }

    /**
     * Pixelation of the given region, used to redact parts of an image.
     */
    @Immutable
    data class Blur(
        @FloatRange(from = 0.0, to = 1.0) val left: Float,
        @FloatRange(from = 0.0, to = 1.0) val top: Float,
        @FloatRange(from = 0.0, to = 1.0) val right: Float,
        @FloatRange(from = 0.0, to = 1.0) val bottom: Float,
    ) : ImageAnnotation {
        override fun rotateAntiClockwise() = Blur(
            left = top,
            top = 1f - right,
            right = bottom,
            bottom = 1f - left,
        )

        override fun flipHorizontally() = copy(
            left = 1f - right,
            right = 1f - left,
        )

        override fun flipVertically() = copy(
            top = 1f - bottom,
            bottom = 1f - top,
        )

        companion object {
            fun fromCorners(a: NormalizedPoint, b: NormalizedPoint): Blur? {
                val left = minOf(a.x, b.x)
                val top = minOf(a.y, b.y)
                val right = maxOf(a.x, b.x)
                val bottom = maxOf(a.y, b.y)
                return if (left < right && top < bottom) {
                    Blur(left = left, top = top, right = right, bottom = bottom)
                } else {
                    null
                }
            }
        }
    }
}

@Immutable
data class NormalizedCropRect(
    @FloatRange(from = 0.0, to = 1.0) val left: Float,
    @FloatRange(from = 0.0, to = 1.0) val top: Float,
    @FloatRange(from = 0.0, to = 1.0) val right: Float,
    @FloatRange(from = 0.0, to = 1.0) val bottom: Float,
) {
    init {
        require(left in 0f..1f)
        require(top in 0f..1f)
        require(right in 0f..1f)
        require(bottom in 0f..1f)
        require(left < right)
        require(top < bottom)
    }

    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    fun applyChange(
        dragTarget: CropDragTarget,
        deltaX: Float,
        deltaY: Float,
    ): NormalizedCropRect = when (dragTarget) {
        is CropDragTarget.Move -> translate(deltaX, deltaY)
        is CropDragTarget.Corner -> dragWithCorner(dragTarget, deltaX, deltaY)
        is CropDragTarget.Edge -> dragWithEdge(dragTarget, deltaX, deltaY)
    }

    private fun translate(deltaX: Float, deltaY: Float): NormalizedCropRect {
        val clampedLeft = (left + deltaX).coerceIn(0f, 1f - width)
        val clampedTop = (top + deltaY).coerceIn(0f, 1f - height)
        return copy(
            left = clampedLeft,
            top = clampedTop,
            right = clampedLeft + width,
            bottom = clampedTop + height,
        )
    }

    private fun dragWithCorner(
        dragTarget: CropDragTarget.Corner,
        deltaX: Float,
        deltaY: Float,
    ) = when (dragTarget) {
        CropDragTarget.Corner.TopLeft -> copy(
            left = (left + deltaX).coerceIn(0f, right - MIN_CROP_SIZE),
            top = (top + deltaY).coerceIn(0f, bottom - MIN_CROP_SIZE),
        )
        CropDragTarget.Corner.TopRight -> copy(
            right = (right + deltaX).coerceIn(left + MIN_CROP_SIZE, 1f),
            top = (top + deltaY).coerceIn(0f, bottom - MIN_CROP_SIZE),
        )
        CropDragTarget.Corner.BottomRight -> copy(
            right = (right + deltaX).coerceIn(left + MIN_CROP_SIZE, 1f),
            bottom = (bottom + deltaY).coerceIn(top + MIN_CROP_SIZE, 1f),
        )
        CropDragTarget.Corner.BottomLeft -> copy(
            left = (left + deltaX).coerceIn(0f, right - MIN_CROP_SIZE),
            bottom = (bottom + deltaY).coerceIn(top + MIN_CROP_SIZE, 1f),
        )
    }

    private fun dragWithEdge(
        dragTarget: CropDragTarget.Edge,
        deltaX: Float,
        deltaY: Float,
    ) = when (dragTarget) {
        CropDragTarget.Edge.Top -> copy(
            top = (top + deltaY).coerceIn(0f, bottom - MIN_CROP_SIZE),
        )
        CropDragTarget.Edge.Right -> copy(
            right = (right + deltaX).coerceIn(left + MIN_CROP_SIZE, 1f),
        )
        CropDragTarget.Edge.Bottom -> copy(
            bottom = (bottom + deltaY).coerceIn(top + MIN_CROP_SIZE, 1f),
        )
        CropDragTarget.Edge.Left -> copy(
            left = (left + deltaX).coerceIn(0f, right - MIN_CROP_SIZE),
        )
    }

    fun rotateAntiClockwise() = copy(
        left = top,
        top = 1f - right,
        right = bottom,
        bottom = 1f - left,
    )

    fun flipHorizontally() = copy(
        left = 1f - right,
        right = 1f - left,
    )

    fun flipVertically() = copy(
        top = 1f - bottom,
        bottom = 1f - top,
    )

    companion object {
        fun default() = NormalizedCropRect(
            left = DEFAULT_CROP_MARGIN,
            top = DEFAULT_CROP_MARGIN,
            right = 1f - DEFAULT_CROP_MARGIN,
            bottom = 1f - DEFAULT_CROP_MARGIN,
        )
    }
}

sealed interface CropDragTarget {
    data object Move : CropDragTarget

    sealed interface Corner : CropDragTarget {
        data object TopLeft : Corner
        data object TopRight : Corner
        data object BottomRight : Corner
        data object BottomLeft : Corner
    }

    sealed interface Edge : CropDragTarget {
        data object Top : Edge
        data object Right : Edge
        data object Bottom : Edge
        data object Left : Edge
    }
}
