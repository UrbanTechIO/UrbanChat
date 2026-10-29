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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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
import androidx.compose.runtime.LaunchedEffect
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
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        setResult(RESULT_OK, Intent().setData(uri).putExtra(UrbanCameraContract.EXTRA_IS_VIDEO, isVideo))
        finish()
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

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    BackHandler(enabled = recording != null) { recording?.stop() }

    LaunchedEffect(cameraAllowed, videoMode, front) {
        if (!cameraAllowed || owner == null || recording != null) return@LaunchedEffect
        try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            if (videoMode) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                camera = provider.bindToLifecycle(owner, selector, preview, capture)
                videoCapture = capture
                imageCapture = null
            } else {
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                camera = provider.bindToLifecycle(owner, selector, preview, capture)
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
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onResult(file, false)

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

        // Top bar: close and flash
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
            RoundButton(onClick = { flashOn = !flashOn }) {
                FlashBolt(active = flashOn)
            }
        }

        // Bottom: mode switch, shutter, camera flip
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recording == null) {
                    ModePill("Photo", selected = !videoMode) { videoMode = false }
                    ModePill("Video", selected = videoMode) { videoMode = true }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(84.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(if (recording != null) RoundedCornerShape(12.dp) else CircleShape)
                    .background(if (videoMode) Color(0xFFFF3B30) else Color.White)
                    .clickable { if (videoMode) toggleRecording() else takePhoto() },
            )
            if (recording == null) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 14.dp)) {
                    RoundButton(onClick = { front = !front }) {
                        Icon(CompoundIcons.SwitchCameraSolid(), contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
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
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
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
