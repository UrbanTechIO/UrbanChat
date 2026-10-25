/*
 * UrbanChat extras: lifts objects out of a photo so they can become stickers.
 */

package io.element.android.libraries.urbanextras.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One object found in a photo: its picture (transparent around it) and where its top-left corner sits in the analysed photo. */
class LiftedSubject(val bitmap: Bitmap, val startX: Int, val startY: Int)

/** Every object found in a photo, and the size of the (scaled) photo the positions refer to. */
class SubjectSet(val width: Int, val height: Int, val subjects: List<LiftedSubject>)

object StickerMaker {
    private const val MAX_INPUT_SIDE = 1280
    private const val MAX_STICKER_SIDE = 512

    /** Finds the separate objects in the picture at [uri]. Uses Google's on-device model, which Google Play services downloads the first time. */
    suspend fun findSubjects(context: Context, uri: Uri): Result<SubjectSet> = withContext(Dispatchers.Default) {
        runCatching {
            val source = decode(context, uri)
            val options = SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableSubjectBitmap().build())
                .build()
            val subjects = segment(source, options)
            check(subjects.isNotEmpty()) { "No object found in the picture" }
            SubjectSet(source.width, source.height, subjects)
        }
    }

    /** Saves [subject] into the sticker tray as a transparent PNG. */
    suspend fun saveAsSticker(context: Context, subject: LiftedSubject): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val sticker = cropAndScale(subject.bitmap)
            val directory = File(context.filesDir, "stickers").apply { mkdirs() }
            val target = File(directory, "${UUID.randomUUID()}.png")
            target.outputStream().use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
            target
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_INPUT_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not read the picture")
    }

    private suspend fun segment(bitmap: Bitmap, options: SubjectSegmenterOptions): List<LiftedSubject> = suspendCancellableCoroutine { continuation ->
        val segmenter = SubjectSegmentation.getClient(options)
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                continuation.resume(result.subjects.mapNotNull { subject -> subject.bitmap?.let { LiftedSubject(it, subject.startX, subject.startY) } })
            }
            .addOnFailureListener { continuation.resumeWithException(it) }
            .addOnCompleteListener { segmenter.close() }
    }

    /** Trims the transparent border around the subject, adds a small margin and scales it down to sticker size. */
    private fun cropAndScale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (Color.alpha(pixels[y * width + x]) > 16) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        check(maxX >= minX && maxY >= minY) { "No object found in the picture" }
        val margin = (maxOf(maxX - minX, maxY - minY) * 0.04f).toInt().coerceAtLeast(2)
        val left = (minX - margin).coerceAtLeast(0)
        val top = (minY - margin).coerceAtLeast(0)
        val right = (maxX + margin).coerceAtMost(width - 1)
        val bottom = (maxY + margin).coerceAtMost(height - 1)
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        val side = maxOf(cropped.width, cropped.height)
        if (side <= MAX_STICKER_SIDE) return cropped
        val scale = MAX_STICKER_SIDE / side.toFloat()
        return Bitmap.createScaledBitmap(cropped, (cropped.width * scale).toInt().coerceAtLeast(1), (cropped.height * scale).toInt().coerceAtLeast(1), true)
    }
}
