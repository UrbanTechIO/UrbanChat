/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.preview.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.net.toUri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.mediaviewer.api.anImageMediaInfo
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultAttachmentImageEditorAnnotationsTest : RobolectricTest() {
    private val context = RuntimeEnvironment.getApplication() as Context

    private fun TestScope.createEditor() = DefaultAttachmentImageEditor(
        context = context,
        dispatchers = testCoroutineDispatchers(),
    )

    private fun createSourceImage(width: Int = 200, height: Int = 120): LocalMedia {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.GRAY)
        val file = File(context.cacheDir, "source-${System.nanoTime()}.jpeg")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        bitmap.recycle()
        return LocalMedia(
            uri = file.toUri(),
            info = anImageMediaInfo(),
        )
    }

    private suspend fun TestScope.exportOrThrow(annotations: List<ImageAnnotation>): File {
        val result = createEditor().exportEdits(
            localMedia = createSourceImage(),
            edits = AttachmentImageEdits(annotations = annotations),
        )
        // Surface the real cause instead of a silent failure
        val edited = result.getOrElse { error("exportEdits failed: ${it.stackTraceToString()}") }
        return edited.file
    }

    @Test
    fun `export with a pen stroke succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Stroke(
                    points = listOf(
                        NormalizedPoint(0.1f, 0.1f),
                        NormalizedPoint(0.5f, 0.5f),
                        NormalizedPoint(0.9f, 0.2f),
                    ),
                    color = AnnotationColors.RED,
                    strokeWidth = AnnotationDefaults.DEFAULT_STROKE_WIDTH,
                )
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with a single point stroke succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Stroke(
                    points = listOf(NormalizedPoint(0.5f, 0.5f)),
                    color = AnnotationColors.BLUE,
                    strokeWidth = AnnotationDefaults.DEFAULT_STROKE_WIDTH,
                )
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with an arrow succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Arrow(
                    start = NormalizedPoint(0.2f, 0.2f),
                    end = NormalizedPoint(0.8f, 0.7f),
                    color = AnnotationColors.GREEN,
                    strokeWidth = AnnotationDefaults.DEFAULT_STROKE_WIDTH * AnnotationDefaults.ARROW_STROKE_WIDTH_FACTOR,
                )
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with text succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Text(
                    center = NormalizedPoint(0.5f, 0.5f),
                    text = "Hello",
                    color = AnnotationColors.YELLOW,
                    textSize = AnnotationDefaults.TEXT_SIZE,
                )
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with a pixelated area succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Blur(
                    left = 0.2f,
                    top = 0.2f,
                    right = 0.6f,
                    bottom = 0.5f,
                )
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with a pixelated area covering the whole image succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Blur(
                    left = 0f,
                    top = 0f,
                    right = 1f,
                    bottom = 1f,
                )
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with every annotation type at once succeeds`() = runTest {
        val file = exportOrThrow(
            listOf(
                ImageAnnotation.Blur(left = 0.05f, top = 0.05f, right = 0.4f, bottom = 0.3f),
                ImageAnnotation.Stroke(
                    points = listOf(NormalizedPoint(0.1f, 0.9f), NormalizedPoint(0.9f, 0.9f)),
                    color = AnnotationColors.WHITE,
                    strokeWidth = AnnotationDefaults.DEFAULT_STROKE_WIDTH,
                ),
                ImageAnnotation.Arrow(
                    start = NormalizedPoint(0.9f, 0.1f),
                    end = NormalizedPoint(0.6f, 0.4f),
                    color = AnnotationColors.RED,
                    strokeWidth = AnnotationDefaults.DEFAULT_STROKE_WIDTH * AnnotationDefaults.ARROW_STROKE_WIDTH_FACTOR,
                ),
                ImageAnnotation.Text(
                    center = NormalizedPoint(0.5f, 0.6f),
                    text = "Redacted",
                    color = AnnotationColors.BLACK,
                    textSize = AnnotationDefaults.TEXT_SIZE,
                ),
            )
        )
        assertThat(file.length()).isGreaterThan(0)
    }

    @Test
    fun `export with annotations and a crop succeeds`() = runTest {
        val result = createEditor().exportEdits(
            localMedia = createSourceImage(),
            edits = AttachmentImageEdits(
                cropRect = NormalizedCropRect(left = 0.1f, top = 0.1f, right = 0.9f, bottom = 0.9f),
                annotations = listOf(
                    ImageAnnotation.Stroke(
                        points = listOf(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.7f, 0.8f)),
                        color = AnnotationColors.RED,
                        strokeWidth = AnnotationDefaults.DEFAULT_STROKE_WIDTH,
                    )
                ),
            ),
        )
        val edited = result.getOrElse { error("exportEdits failed: ${it.stackTraceToString()}") }
        assertThat(edited.file.length()).isGreaterThan(0)
    }
}
