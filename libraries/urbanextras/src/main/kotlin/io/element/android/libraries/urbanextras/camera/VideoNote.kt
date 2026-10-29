/*
 * UrbanChat extras.
 */

package io.element.android.libraries.urbanextras.camera

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Round video notes. The sender records a short front-camera clip and sends it as a normal Matrix video whose file name carries
 * [PREFIX]. UrbanChat on the other side shows such a video as a small circle that loops silently and plays with sound when tapped;
 * other Matrix apps simply show a video.
 */
object VideoNote {
    const val PREFIX = "UCVN-"
    const val MAX_DURATION_MS = 60_000L
    const val MIN_DURATION_MS = 1_000L

    fun isVideoNote(filename: String?): Boolean = filename?.startsWith(PREFIX) == true

    fun mark(filename: String): String = if (filename.startsWith(PREFIX)) filename else PREFIX + filename
}

/** Shared between the camera button in the composer (which feeds it the gesture) and the recorder overlay (which reads it). */
object VideoNoteController {
    enum class Phase { Idle, Holding, Locked }

    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _drag = MutableStateFlow(0f to 0f)

    /** How far the finger has moved (px) since the press started: negative x towards cancel, negative y towards lock. */
    val drag: StateFlow<Pair<Float, Float>> = _drag.asStateFlow()

    private val _finish = MutableStateFlow<Boolean?>(null)

    /** Centre of the camera button in window pixels, so the overlay can grow right where the finger is. */
    @Volatile
    var buttonCenter: Pair<Float, Float> = 0f to 0f

    /** Set once the recording should end: true = send it, false = throw it away. */
    val finish: StateFlow<Boolean?> = _finish.asStateFlow()

    fun start() {
        _drag.value = 0f to 0f
        _finish.value = null
        _phase.value = Phase.Holding
    }

    fun drag(dx: Float, dy: Float) {
        if (_phase.value == Phase.Holding) _drag.value = dx to dy
    }

    fun lock() {
        if (_phase.value == Phase.Holding) _phase.value = Phase.Locked
    }

    fun finish(send: Boolean) {
        if (_phase.value != Phase.Idle && _finish.value == null) _finish.value = send
    }

    internal fun reset() {
        _phase.value = Phase.Idle
        _finish.value = null
        _drag.value = 0f to 0f
    }
}

internal tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

internal suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        {
            try {
                continuation.resume(future.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        },
        ContextCompat.getMainExecutor(this),
    )
}
