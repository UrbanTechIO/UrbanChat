/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.os.Bundle
import android.util.Rational
import android.view.Surface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Observer
import io.element.android.compound.tokens.generated.CompoundIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File

data class UrbanCameraResult(val uri: Uri, val isVideo: Boolean)

/** Opens the UrbanChat camera and returns the photo or video that was taken, or null if it was closed. */
class UrbanCameraContract : ActivityResultContract<Unit, UrbanCameraResult?>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent(context, UrbanCameraActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): UrbanCameraResult? {
        val uri = intent?.data
        return if (resultCode == android.app.Activity.RESULT_OK && uri != null) {
            UrbanCameraResult(uri, intent.getBooleanExtra(EXTRA_IS_VIDEO, false))
        } else {
            null
        }
    }

    internal companion object {
        const val EXTRA_IS_VIDEO = "is_video"
    }
}

/** One camera screen with Photo and Video as separate modes, a camera switch and a flash/torch button. */
class UrbanCameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { CameraScreen(onClose = { finish() }, onResult = ::deliver) }
    }

    private fun deliver(file: File, isVideo: Boolean) {
        // A photo taken here only exists in the app's private cache, so it is saved into the Gallery's Sent folder right
        // away, like the plus menu's Take photo does. Videos are saved there once they are sent.
        if (!isVideo) savePhotoToGallery(file)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        setResult(RESULT_OK, Intent().setData(uri).putExtra(UrbanCameraContract.EXTRA_IS_VIDEO, isVideo))
        finish()
    }

    private fun savePhotoToGallery(file: File) {
        runCatching {
            val filename = "UrbanChat_${System.currentTimeMillis()}.${file.extension}"
            val subPath = "UrbanChat/UrbanImages/UrbanImages Sent"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DCIM}/$subPath")
                }
                val target = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
                file.inputStream().use { input -> contentResolver.openOutputStream(target)?.use { input.copyTo(it) } }
            } else {
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), subPath).apply { mkdirs() }
                file.copyTo(File(dir, filename), overwrite = true)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun CameraScreen(onClose: () -> Unit, onResult: (File, Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = remember { context.findLifecycleOwner() }
    fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var cameraAllowed by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
    var audioAllowed by remember { mutableStateOf(has(Manifest.permission.RECORD_AUDIO)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraAllowed = result[Manifest.permission.CAMERA] == true || has(Manifest.permission.CAMERA)
        audioAllowed = result[Manifest.permission.RECORD_AUDIO] == true || has(Manifest.permission.RECORD_AUDIO)
        if (!cameraAllowed) onClose()
    }
    LaunchedEffect(Unit) {
        if (!cameraAllowed || !audioAllowed) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    var videoMode by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var showLooks by remember { mutableStateOf(false) }
    var lookIndex by remember { mutableIntStateOf(0) }
    var focusTap by remember { mutableStateOf<Pair<Offset, Long>?>(null) }
    LaunchedEffect(focusTap) {
        if (focusTap != null) {
            delay(1200)
            focusTap = null
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Follow the camera's zoom, so the zoom buttons and pinch always start from the real value.
    DisposableEffect(camera, owner) {
        val liveData = camera?.cameraInfo?.zoomState
        val observer = Observer<ZoomState> { state ->
            zoomRatio = state.zoomRatio
            minZoom = state.minZoomRatio
            maxZoom = state.maxZoomRatio
        }
        if (liveData != null && owner != null) liveData.observe(owner, observer)
        onDispose { liveData?.removeObserver(observer) }
    }

    // The look is shown live on the preview for photos. Video is recorded as it is.
    LaunchedEffect(lookIndex, videoMode) {
        val matrix = if (videoMode) null else lookMatrix(lookIndex)
        if (matrix == null) {
            previewView.setLayerType(View.LAYER_TYPE_NONE, null)
        } else {
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        }
    }

    BackHandler(enabled = recording != null) { recording?.stop() }

    LaunchedEffect(cameraAllowed, videoMode, front) {
        if (!cameraAllowed || owner == null || recording != null) return@LaunchedEffect
        try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            // The photo and the video are cut to exactly the area the preview shows (it fills the screen and crops the sides), so
            // what is captured is what was seen, at every zoom.
            val viewPort = ViewPort.Builder(
                Rational(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels),
                previewView.display?.rotation ?: Surface.ROTATION_0,
            ).setScaleType(ViewPort.FILL_CENTER).build()
            fun sharedView(vararg useCases: androidx.camera.core.UseCase) = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .apply { useCases.forEach { addUseCase(it) } }
                .build()
            provider.unbindAll()
            if (videoMode) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                camera = provider.bindToLifecycle(owner, selector, sharedView(preview, capture))
                videoCapture = capture
                imageCapture = null
            } else {
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                camera = provider.bindToLifecycle(owner, selector, sharedView(preview, capture))
                imageCapture = capture
                videoCapture = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not open the camera")
            onClose()
        }
    }

    LaunchedEffect(flashOn, camera, videoMode) {
        val current = camera ?: return@LaunchedEffect
        if (videoMode) {
            current.cameraControl.enableTorch(flashOn)
        } else {
            current.cameraControl.enableTorch(false)
            imageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
    }

    LaunchedEffect(recording) {
        if (recording == null) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAt
            delay(200)
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (busy) return
        busy = true
        val file = File(context.cacheDir, "urban-photo-${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val matrix = lookMatrix(lookIndex)
                    if (matrix == null) {
                        onResult(file, false)
                    } else {
                        // Bake the chosen look into the photo off the main thread, then hand it over.
                        Thread {
                            runCatching { applyLook(file, matrix) }.onFailure { Timber.e(it, "Could not apply the look") }
                            ContextCompat.getMainExecutor(context).execute { onResult(file, false) }
                        }.start()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "Could not take the photo")
                    busy = false
                }
            },
        )
    }

    fun toggleRecording() {
        val current = recording
        if (current != null) {
            current.stop()
            return
        }
        val capture = videoCapture ?: return
        val file = File(context.cacheDir, "urban-video-${System.currentTimeMillis()}.mp4")
        recordingFile = file
        val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        recording = (if (audioAllowed) pending.withAudioEnabled() else pending)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    if (!event.hasError() && file.length() > 0) {
                        onResult(file, true)
                    } else {
                        Timber.e("Video recording failed: ${event.error}")
                        file.delete()
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Rule-of-thirds grid, as in the phone camera.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val line = Color.White.copy(alpha = 0.55f)
            val stroke = 1.dp.toPx()
            for (i in 1..2) {
                drawLine(line, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), stroke)
                drawLine(line, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), stroke)
            }
        }

        // Pinch to zoom, tap to focus.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        camera?.cameraControl?.setZoomRatio((zoomRatio * zoom).coerceIn(minZoom, maxZoom))
                    }
                }
                .pointerInput(camera) {
                    detectTapGestures { tap ->
                        val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        focusTap = tap to System.currentTimeMillis()
                    }
                },
        )
        focusTap?.let { (point, _) ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((point.x - 38.dp.toPx()).roundToInt(), (point.y - 38.dp.toPx()).roundToInt()) }
                    .size(76.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            )
        }

        // Top: close on the left, recording time in the middle, flash and filters on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(onClick = onClose) { Icon(CompoundIcons.Close(), contentDescription = null, tint = Color.White) }
            if (recording != null) {
                val seconds = (elapsedMs / 1000).toInt()
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                    Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 16.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(onClick = { flashOn = !flashOn }) { FlashBolt(active = flashOn) }
                if (!videoMode && recording == null) {
                    RoundButton(onClick = { showLooks = !showLooks }) { LooksIcon(active = showLooks || lookIndex != 0) }
                }
            }
        }

        // Bottom, from the top down: filters, Photo/Video, zoom, then the shutter with the camera flip beside it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (showLooks && !videoMode && recording == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 218.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .horizontalScroll(rememberScrollState())
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LOOK_NAMES.forEachIndexed { index, name -> ModePill(name, selected = lookIndex == index) { lookIndex = index } }
                }
            }
            if (recording == null) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 166.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    ModeTab("PHOTO", selected = !videoMode) { videoMode = false }
                    ModeTab("VIDEO", selected = videoMode) { videoMode = true }
                }
            }
            // Zoom for the lenses and zoom range this phone has: one dark pill, the active level in a circle.
            val options = buildList {
                if (minZoom < 0.95f) add(minZoom)
                add(1f)
                listOf(2f, 3f, 5f).forEach { if (it <= maxZoom + 0.01f) add(it) }
            }
            if (options.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 108.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(4.dp),
                ) {
                    options.forEach { ratio ->
                        val active = kotlin.math.abs(zoomRatio - ratio) < 0.05f
                        val number = if (ratio < 1f) "%.1f".format(ratio).trimStart('0') else "%d".format(ratio.toInt())
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 40.dp)
                                .clip(CircleShape)
                                .background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { camera?.cameraControl?.setZoomRatio(ratio) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (active) "$number×" else number,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            // The shutter: a white circle; in video it carries an orange dot, which becomes a square while recording.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFAF8F5))
                    .clickable { if (videoMode) toggleRecording() else takePhoto() },
                contentAlignment = Alignment.Center,
            ) {
                if (videoMode) {
                    Box(
                        modifier = Modifier
                            .size(if (recording != null) 30.dp else 40.dp)
                            .clip(if (recording != null) RoundedCornerShape(8.dp) else CircleShape)
                            .background(Color(0xFFF05A3C)),
                    )
                }
            }
            if (recording == null) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 44.dp, bottom = 10.dp)) {
                    RoundButton(onClick = { front = !front }, size = 64.dp) {
                        Icon(CompoundIcons.SwitchCameraSolid(), contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Two overlapping circles, like the phone camera's filter icon: yellow while a look is in use. */
@Composable
private fun LooksIcon(active: Boolean) {
    val color = if (active) Color(0xFFFFC107) else Color.White
    Canvas(modifier = Modifier.size(24.dp)) {
        val r = size.width * 0.3f
        drawCircle(color, r, Offset(size.width * 0.38f, size.height * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawCircle(color.copy(alpha = 0.75f), r, Offset(size.width * 0.62f, size.height * 0.62f))
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

/** A lightning bolt: light grey while the flash is off, yellow while it is on. */
@Composable
private fun FlashBolt(active: Boolean) {
    val color = if (active) Color(0xFFFFC107) else Color(0xFFC8C8C8)
    val path = remember { PathParser().parsePathString("M7 2v11h3v9l7-12h-4l4-8z").toPath() }
    Canvas(modifier = Modifier.size(24.dp)) {
        val scale = size.width / 24f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) { drawPath(path, color) }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun ZoomPill(label: String, selected: Boolean, onClick: () -> Unit) = ModePill(label, selected, onClick)

private val LOOK_NAMES = listOf("Normal", "Mono", "Sepia", "Vivid", "Cool", "Warm", "Fade")

/** The colour change of the look at [index], or null for no change. */
private fun lookMatrix(index: Int): ColorMatrix? = when (index) {
    1 -> ColorMatrix().apply { setSaturation(0f) }
    2 -> ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    3 -> ColorMatrix().apply { setSaturation(1.6f) }
    4 -> ColorMatrix(floatArrayOf(0.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.12f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    5 -> ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    6 -> ColorMatrix(floatArrayOf(0.85f, 0f, 0f, 0f, 25f, 0f, 0.85f, 0f, 0f, 25f, 0f, 0f, 0.85f, 0f, 25f, 0f, 0f, 0f, 1f, 0f))
    else -> null
}

/** Rewrites the JPEG at [file] with [matrix] applied, upright (the rotation the camera stored is baked in). */
private fun applyLook(file: File, matrix: ColorMatrix) {
    val source = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val degrees = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (degrees == 0f) source else Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees) }, true)
    val result = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(result).drawBitmap(upright, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
    file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 92, it) }
}
