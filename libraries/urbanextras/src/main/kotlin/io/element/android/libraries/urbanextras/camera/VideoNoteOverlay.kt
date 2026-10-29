/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.element.android.compound.theme.ElementTheme
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.mutableStateOf
import io.element.android.compound.tokens.generated.CompoundIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File

/**
 * Shows the round camera view while a video note is being recorded. Hosted once over the whole chat screen; it draws nothing when
 * no note is being recorded. [onNote] gets the finished clip.
 */
@Composable
fun VideoNoteOverlay(onNote: (Uri) -> Unit) {
    val context = LocalContext.current
    val phase by VideoNoteController.phase.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val permissionsGranted = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    LaunchedEffect(phase, permissionsGranted) {
        if (phase != VideoNoteController.Phase.Idle && !permissionsGranted) {
            // The press cannot continue through the permission dialog; the next press records.
            VideoNoteController.reset()
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    if (phase != VideoNoteController.Phase.Idle && permissionsGranted) {
        NoteRecorder(onNote = onNote)
    }
}

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@SuppressLint("MissingPermission")
@Composable
private fun NoteRecorder(onNote: (Uri) -> Unit) {
    val context = LocalContext.current
    val owner = remember { context.findLifecycleOwner() }
    val phase by VideoNoteController.phase.collectAsState()
    val finish by VideoNoteController.finish.collectAsState()
    val drag by VideoNoteController.drag.collectAsState()

    val latestOnNote by rememberUpdatedState(onNote)
    val file = remember { File(context.cacheDir, "videonote-${System.currentTimeMillis()}.mp4") }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    // Whether the clip is wanted, remembered from the moment the recording is asked to stop.
    var sendIt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (owner == null) {
            VideoNoteController.reset()
            return@LaunchedEffect
        }
        try {
            val provider = context.awaitCameraProvider()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .setTargetVideoEncodingBitRate(1_500_000)
                .build()
            val capture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
            val startedAt = System.currentTimeMillis()
            recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        val durationMs = System.currentTimeMillis() - startedAt
                        provider.unbindAll()
                        val ok = !event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                        if (sendIt && ok && durationMs >= VideoNote.MIN_DURATION_MS && file.length() > 0) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            latestOnNote(uri)
                        } else {
                            file.delete()
                        }
                        VideoNoteController.reset()
                    }
                }
            while (true) {
                elapsedMs = System.currentTimeMillis() - startedAt
                if (elapsedMs >= VideoNote.MAX_DURATION_MS) VideoNoteController.finish(true)
                delay(100)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not record a video note")
            file.delete()
            VideoNoteController.reset()
        }
    }

    LaunchedEffect(finish, recording) {
        val wanted = finish ?: return@LaunchedEffect
        val current = recording ?: return@LaunchedEffect
        sendIt = wanted
        current.stop()
        recording = null
    }

    DisposableEffect(Unit) {
        onDispose {
            sendIt = false
            recording?.stop()
        }
    }

    val locked = phase == VideoNoteController.Phase.Locked
    val density = LocalDensity.current
    val cancelProgress = (-drag.first / with(density) { 120.dp.toPx() }).coerceIn(0f, 1f)
    val lockProgress = (-drag.second / with(density) { 64.dp.toPx() }).coerceIn(0f, 1f)
    val grow by animateFloatAsState(
        targetValue = if (locked) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "noteButtonGrow",
    )
    val (buttonX, buttonY) = VideoNoteController.buttonCenter
    val accent = ElementTheme.colors.bgAccentRest

    // A window of its own that covers the whole screen, so it can also grow over the message bar where the finger is.
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ) = IntOffset.Zero
        },
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
        ) {
            // Timer on top
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF15C6D))
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                val seconds = (elapsedMs / 1000).toInt()
                Text(text = "%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 20.sp)
            }

            // The round camera view with the progress ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-60).dp)
                    .size(300.dp),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape),
                )
                Canvas(modifier = Modifier.size(296.dp)) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.25f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * (elapsedMs / VideoNote.MAX_DURATION_MS.toFloat()).coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }

            if (locked) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundAction(
                        color = Color.White.copy(alpha = 0.2f),
                        onClick = { VideoNoteController.finish(false) },
                    ) { Icon(CompoundIcons.Delete(), contentDescription = null, tint = Color.White) }
                    RoundAction(
                        color = accent,
                        onClick = { VideoNoteController.finish(true) },
                    ) { Icon(CompoundIcons.SendSolid(), contentDescription = null, tint = Color.White) }
                }
            } else {
                // The lock slot above the finger: slide up into it to keep recording hands-free
                Column(
                    modifier = Modifier
                        .offset { IntOffset((buttonX - 28.dp.toPx()).roundToInt(), (buttonY - 190.dp.toPx() + 24.dp.toPx() * lockProgress).roundToInt()) }
                        .size(width = 56.dp, height = 132.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF11181F))
                        .padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(CompoundIcons.LockOff(), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Icon(
                        CompoundIcons.ChevronUp(),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 1f - lockProgress),
                        modifier = Modifier.size(22.dp),
                    )
                }

                // "Slide to cancel" along the bottom, where the message bar is
                Row(
                    modifier = Modifier
                        .offset { IntOffset(8.dp.toPx().roundToInt(), (buttonY - 28.dp.toPx()).roundToInt()) }
                        .size(width = (buttonX / density.density - 40).dp.coerceAtLeast(120.dp), height = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF11181F).copy(alpha = 0.85f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(CompoundIcons.ChevronLeft(), contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                    Text(
                        text = "Slide to cancel",
                        color = Color.White.copy(alpha = 0.75f * (1f - cancelProgress)),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                // The camera button, grown, following the finger
                val followX = drag.first.coerceIn(-with(density) { 120.dp.toPx() }, 0f)
                val followY = drag.second.coerceIn(-with(density) { 64.dp.toPx() }, 0f)
                val buttonSize = 48.dp + 24.dp * grow
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (buttonX + followX - buttonSize.toPx() / 2).roundToInt(),
                                (buttonY + followY - buttonSize.toPx() / 2).roundToInt(),
                            )
                        }
                        .size(buttonSize)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CompoundIcons.TakePhoto(), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundAction(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
