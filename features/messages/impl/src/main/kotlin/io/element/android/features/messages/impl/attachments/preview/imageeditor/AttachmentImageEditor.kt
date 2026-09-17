/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.preview.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.impl.attachments.preview.resolvedImageMimeType
import io.element.android.libraries.androidutils.bitmap.rotateToExifMetadataOrientation
import io.element.android.libraries.androidutils.bitmap.writeBitmap
import io.element.android.libraries.androidutils.file.createTmpFile
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeAnimatedImage
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeImage
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val EDITED_MEDIA_DIR_NAME = "edited-media"

interface AttachmentImageEditor {
    suspend fun canEdit(localMedia: LocalMedia): Boolean

    suspend fun exportEdits(
        localMedia: LocalMedia,
        edits: AttachmentImageEdits,
    ): Result<EditedLocalMedia>

    /**
     * Copies the media to a file owned by the editor.
     * The media pre-processor deletes temporary sources (camera captures for instance) as soon as
     * it has processed them, so the editor needs its own copy to still be able to read the pixels
     * when the user opens the editor later on.
     */
    suspend fun createEditableCopy(localMedia: LocalMedia): Result<EditedLocalMedia>
}

data class EditedLocalMedia(
    val localMedia: LocalMedia,
    val file: File,
)

@ContributesBinding(AppScope::class)
class DefaultAttachmentImageEditor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : AttachmentImageEditor {
    override suspend fun canEdit(localMedia: LocalMedia): Boolean = withContext(dispatchers.io) {
        localMedia.info.resolvedImageMimeType()
            ?.takeIf { it.isEditableStillImageMimeType() }
            ?.let { return@withContext true }

        val decodedMimeType = context.contentResolver.openInputStream(localMedia.uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            options.outMimeType
        }

        decodedMimeType.isEditableStillImageMimeType()
    }

    override suspend fun createEditableCopy(
        localMedia: LocalMedia,
    ): Result<EditedLocalMedia> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val editedMediaDir = File(context.cacheDir, EDITED_MEDIA_DIR_NAME).apply { mkdirs() }
            val outputFile = context.createTmpFile(
                baseDir = editedMediaDir,
                extension = localMedia.info.fileExtension,
            )
            context.contentResolver.openInputStream(localMedia.uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read image from ${localMedia.uri}")
            EditedLocalMedia(
                localMedia = localMedia.copy(uri = Uri.fromFile(outputFile)),
                file = outputFile,
            )
        }
    }

    override suspend fun exportEdits(
        localMedia: LocalMedia,
        edits: AttachmentImageEdits,
    ): Result<EditedLocalMedia> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val sourceMimeType = localMedia.info.resolvedImageMimeType() ?: localMedia.info.mimeType
            val exportedMimeType = exportedMimeTypeFor(sourceMimeType)
            val exifOrientation = context.contentResolver.openInputStream(localMedia.uri)?.let { input ->
                input.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                }
            } ?: ExifInterface.ORIENTATION_UNDEFINED

            val decodedBitmap = context.contentResolver.openInputStream(localMedia.uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: error("Unable to decode image from ${localMedia.uri}")

            val normalizedBitmap = decodedBitmap.rotateToExifMetadataOrientation(exifOrientation)
            if (normalizedBitmap !== decodedBitmap) {
                decodedBitmap.recycle()
            }

            val transformedBitmap = normalizedBitmap.applyEdits(edits)
            if (transformedBitmap !== normalizedBitmap) {
                normalizedBitmap.recycle()
            }

            val annotatedBitmap = transformedBitmap.applyAnnotations(edits.annotations)
            if (annotatedBitmap !== transformedBitmap) {
                transformedBitmap.recycle()
            }

            val cropRect = edits.cropRect.toPixelRect(
                imageWidth = annotatedBitmap.width,
                imageHeight = annotatedBitmap.height,
            )
            val isCropUnchanged = cropRect.left == 0 && cropRect.top == 0 &&
                cropRect.width() == annotatedBitmap.width && cropRect.height() == annotatedBitmap.height
            val croppedBitmap = if (isCropUnchanged) {
                annotatedBitmap
            } else {
                Bitmap.createBitmap(
                    annotatedBitmap,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width(),
                    cropRect.height(),
                )
            }
            if (croppedBitmap !== annotatedBitmap) {
                annotatedBitmap.recycle()
            }

            val editedMediaDir = File(context.cacheDir, EDITED_MEDIA_DIR_NAME).apply { mkdirs() }
            val outputFile = context.createTmpFile(baseDir = editedMediaDir, extension = compressFileExtension(exportedMimeType))
            outputFile.writeBitmap(
                bitmap = croppedBitmap,
                format = compressFormat(exportedMimeType),
                quality = 90,
            )
            croppedBitmap.recycle()

            EditedLocalMedia(
                localMedia = localMedia.copy(
                    uri = Uri.fromFile(outputFile),
                    info = localMedia.info.copy(mimeType = exportedMimeType),
                ),
                file = outputFile,
            )
        }
    }
}

internal fun exportedMimeTypeFor(sourceMimeType: String?): String {
    return if (sourceMimeType == MimeTypes.Png) {
        MimeTypes.Png
    } else {
        MimeTypes.Jpeg
    }
}

private fun Bitmap.applyEdits(edits: AttachmentImageEdits): Bitmap {
    val normalizedTurns = (edits.rotationQuarterTurns % 4 + 4) % 4
    if (normalizedTurns == 0 && !edits.isFlippedHorizontally && !edits.isFlippedVertically) {
        return this
    }
    val centerX = width / 2f
    val centerY = height / 2f
    val matrix = Matrix().apply {
        val scaleX = if (edits.isFlippedHorizontally) -1f else 1f
        val scaleY = if (edits.isFlippedVertically) -1f else 1f
        if (scaleX < 0f || scaleY < 0f) {
            postScale(scaleX, scaleY, centerX, centerY)
        }
        if (normalizedTurns != 0) {
            postRotate(normalizedTurns * 90f, centerX, centerY)
        }
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.applyAnnotations(annotations: List<ImageAnnotation>): Bitmap {
    if (annotations.isEmpty()) {
        return this
    }
    val output = copy(Bitmap.Config.ARGB_8888, true) ?: return this
    val canvas = Canvas(output)
    val minDimension = min(output.width, output.height).toFloat()
    for (annotation in annotations) {
        when (annotation) {
            is ImageAnnotation.Blur -> canvas.drawPixelated(output, annotation)
            is ImageAnnotation.Stroke -> canvas.drawStroke(output, annotation, minDimension)
            is ImageAnnotation.Arrow -> canvas.drawArrow(output, annotation, minDimension)
            is ImageAnnotation.Text -> canvas.drawTextAnnotation(output, annotation, minDimension)
        }
    }
    return output
}

private fun strokePaint(color: Int, strokeWidthPx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = strokeWidthPx
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun Canvas.drawStroke(bitmap: Bitmap, stroke: ImageAnnotation.Stroke, minDimension: Float) {
    if (stroke.points.isEmpty()) return
    val paint = strokePaint(stroke.color, stroke.strokeWidth * minDimension)
    val path = Path()
    stroke.points.forEachIndexed { index, point ->
        val x = point.x * bitmap.width
        val y = point.y * bitmap.height
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    if (stroke.points.size == 1) {
        // A tap: draw a dot
        val point = stroke.points.first()
        drawCircle(point.x * bitmap.width, point.y * bitmap.height, paint.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
    } else {
        drawPath(path, paint)
    }
}

private fun Canvas.drawArrow(bitmap: Bitmap, arrow: ImageAnnotation.Arrow, minDimension: Float) {
    val strokeWidthPx = arrow.strokeWidth * minDimension
    val paint = strokePaint(arrow.color, strokeWidthPx)
    val startX = arrow.start.x * bitmap.width
    val startY = arrow.start.y * bitmap.height
    val endX = arrow.end.x * bitmap.width
    val endY = arrow.end.y * bitmap.height
    drawLine(startX, startY, endX, endY, paint)
    // Arrow head: two segments at 30° from the shaft, pointing back from the end
    val angle = atan2(endY - startY, endX - startX)
    val headLength = (strokeWidthPx * 4f).coerceAtLeast(strokeWidthPx * 2f)
    val headAngle = Math.toRadians(30.0).toFloat()
    drawLine(
        endX,
        endY,
        endX - headLength * cos(angle - headAngle),
        endY - headLength * sin(angle - headAngle),
        paint,
    )
    drawLine(
        endX,
        endY,
        endX - headLength * cos(angle + headAngle),
        endY - headLength * sin(angle + headAngle),
        paint,
    )
}

private fun Canvas.drawTextAnnotation(bitmap: Bitmap, text: ImageAnnotation.Text, minDimension: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text.color
        textSize = text.textSize * minDimension
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(textSize / 12f, 0f, 0f, 0x80000000.toInt())
    }
    val x = text.center.x * bitmap.width
    val baselineY = text.center.y * bitmap.height - (paint.ascent() + paint.descent()) / 2f
    drawText(text.text, x, baselineY, paint)
}

private const val PIXELATION_BLOCK_COUNT = 24

private fun Canvas.drawPixelated(bitmap: Bitmap, blur: ImageAnnotation.Blur) {
    val left = (blur.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val top = (blur.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
    val right = (blur.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
    val bottom = (blur.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
    val regionWidth = right - left
    val regionHeight = bottom - top
    val region = Bitmap.createBitmap(bitmap, left, top, regionWidth, regionHeight)
    val downWidth = (regionWidth / (regionWidth.coerceAtLeast(regionHeight) / PIXELATION_BLOCK_COUNT.toFloat())).roundToInt().coerceAtLeast(1)
    val downHeight = (regionHeight / (regionWidth.coerceAtLeast(regionHeight) / PIXELATION_BLOCK_COUNT.toFloat())).roundToInt().coerceAtLeast(1)
    val downscaled = region.scale(downWidth, downHeight)
    // createBitmap returns the source itself when the region covers the whole image: recycling it
    // then would destroy the bitmap this canvas draws into.
    if (region !== bitmap) {
        region.recycle()
    }
    val paint = Paint().apply { isFilterBitmap = false }
    drawBitmap(
        downscaled,
        null,
        android.graphics.Rect(left, top, right, bottom),
        paint,
    )
    downscaled.recycle()
}

private data class PixelCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun width() = right - left
    fun height() = bottom - top
}

private fun NormalizedCropRect.toPixelRect(imageWidth: Int, imageHeight: Int): PixelCropRect {
    val leftPx = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
    val topPx = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
    val rightPx = (right * imageWidth).roundToInt().coerceIn(leftPx + 1, imageWidth)
    val bottomPx = (bottom * imageHeight).roundToInt().coerceIn(topPx + 1, imageHeight)
    return PixelCropRect(
        left = leftPx,
        top = topPx,
        right = rightPx,
        bottom = bottomPx,
    )
}

private fun compressFormat(mimeType: String) = when (mimeType) {
    MimeTypes.Png -> Bitmap.CompressFormat.PNG
    else -> Bitmap.CompressFormat.JPEG
}

private fun compressFileExtension(mimeType: String) = when (mimeType) {
    MimeTypes.Png -> "png"
    else -> "jpeg"
}

private fun String?.isEditableStillImageMimeType(): Boolean {
    return this != null &&
        this.isMimeTypeImage() &&
        !this.isMimeTypeAnimatedImage() &&
        this != MimeTypes.Svg
}
