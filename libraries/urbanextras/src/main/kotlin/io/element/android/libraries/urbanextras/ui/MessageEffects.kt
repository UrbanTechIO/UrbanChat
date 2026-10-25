/*
 * UrbanChat extras: little full-screen animations for words like "congratulations" or "happy birthday".
 */

package io.element.android.libraries.urbanextras.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class EffectKind { CONFETTI, HEARTS, BALLOONS, SNOW, FIREWORKS, STARS, EMOJI_RAIN }

data class MessageEffect(val kind: EffectKind, val emoji: String? = null)

object MessageEffects {
    private val _current = MutableStateFlow<MessageEffect?>(null)
    val current: StateFlow<MessageEffect?> = _current
    private val seen = HashSet<String>()

    fun play(effect: MessageEffect) {
        _current.value = effect
    }

    fun finished() {
        _current.value = null
    }

    private val rules: List<Pair<(String, String) -> Boolean, MessageEffect>> = listOf(
        { lower: String, text: String -> "happy birthday" in lower || "عيد ميلاد" in lower || "🎂" in text || "🎈" in text } to MessageEffect(EffectKind.BALLOONS),
        { lower: String, text: String -> "happy new year" in lower || "كل عام وأنتم" in lower || "🎆" in text || "🎇" in text } to MessageEffect(EffectKind.FIREWORKS),
        { lower: String, text: String -> "eid mubarak" in lower || "عيد مبارك" in lower || "ramadan" in lower || "رمضان" in lower } to MessageEffect(EffectKind.EMOJI_RAIN, "🌙"),
        { lower: String, text: String -> "snow" in lower || "ثلج" in lower || "❄" in text || "⛄" in text } to MessageEffect(EffectKind.SNOW),
        { lower: String, text: String -> "good night" in lower || "تصبح على خير" in lower || "تصبحين على خير" in lower || "🌙" in text || "💤" in text } to MessageEffect(EffectKind.STARS),
        { lower: String, text: String -> "amazing" in lower || "wow" in lower || "🤩" in text || "⭐" in text || "✨" in text } to MessageEffect(EffectKind.STARS),
        { lower: String, text: String -> "congrat" in lower || "مبروك" in lower || "🎉" in text || "🥳" in text || "🎊" in text } to MessageEffect(EffectKind.CONFETTI),
        { lower: String, text: String -> "miss you" in lower || "missing you" in lower || "اشتقت" in lower || "وحشتني" in lower || "💔" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "💞"),
        { lower: String, text: String -> "thank you" in lower || "thanks" in lower || "شكرا" in lower || "شكراً" in lower || "🙏" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "🙏"),
        { lower: String, text: String -> "good morning" in lower || "صباح الخير" in lower || "☀" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "☀️"),
        { lower: String, text: String -> "get well" in lower || "سلامتك" in lower || "الله يشفيك" in lower } to MessageEffect(EffectKind.EMOJI_RAIN, "🌸"),
        { lower: String, text: String -> "cheers" in lower || "🍻" in text || "🥂" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "🥂"),
        { lower: String, text: String -> "welcome" in lower || "أهلا" in lower || "اهلا" in lower || "مرحبا" in lower } to MessageEffect(EffectKind.CONFETTI),
        { lower: String, text: String -> "😭" in text || "😢" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "😢"),
        { lower: String, text: String -> "kiss" in lower || "بوسة" in lower || "😘" in text || "😗" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "😘"),
        { lower: String, text: String -> "i love" in lower || "my love" in lower || "حبيبي" in lower || "حبيبتي" in lower || "عشقي" in lower } to MessageEffect(EffectKind.HEARTS),
        { lower: String, text: String -> "love you" in lower || "أحبك" in lower || "بحبك" in lower || "😍" in text || "❤" in text || "😘" in text } to MessageEffect(EffectKind.HEARTS),
        { lower: String, text: String -> "haha" in lower || "lol" in lower || "هههه" in lower || "😂" in text || "🤣" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "😂"),
        { lower: String, text: String -> "🔥" in text || "on fire" in lower } to MessageEffect(EffectKind.EMOJI_RAIN, "🔥"),
        { lower: String, text: String -> "bravo" in lower || "well done" in lower || "👏" in text || "برافو" in lower } to MessageEffect(EffectKind.EMOJI_RAIN, "👏"),
        { lower: String, text: String -> "pizza" in lower || "🍕" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "🍕"),
        { lower: String, text: String -> "coffee" in lower || "قهوة" in lower || "☕" in text } to MessageEffect(EffectKind.EMOJI_RAIN, "☕"),
        { lower: String, text: String -> "🥺" in text || "sorry" in lower || "آسف" in lower || "اسف" in lower } to MessageEffect(EffectKind.EMOJI_RAIN, "🥺"),
    )

    /** Looks at a message's text. Returns the effect it should trigger, if any, but only the first time [key] is seen. */
    fun effectFor(key: String, text: String): MessageEffect? {
        val lower = text.lowercase()
        val effect = rules.firstOrNull { (matches, _) -> matches(lower, text) }?.second ?: return null
        synchronized(seen) { if (!seen.add(key)) return null }
        return effect
    }
}

private class Particle(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
)

private val PALETTE = listOf(
    Color(0xFFFF5252), Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFE040FB), Color(0xFFFF9800), Color(0xFF00E5FF),
)

/** Place once on top of a chat. Draws nothing unless an effect was triggered, and never takes touches. */
@Composable
fun MessageEffectsOverlay(modifier: Modifier = Modifier) {
    val effect by MessageEffects.current.collectAsState()
    val current = effect ?: return
    val progress = remember(current) { Animatable(0f) }
    val particles = remember(current) {
        val random = Random(System.nanoTime())
        val count = when (current.kind) {
            EffectKind.CONFETTI -> 90
            EffectKind.SNOW -> 70
            EffectKind.STARS -> 34
            EffectKind.FIREWORKS -> 6
            EffectKind.EMOJI_RAIN -> 26
            else -> 28
        }
        List(count) {
            Particle(
                x = random.nextFloat(),
                phase = random.nextFloat() * 0.45f,
                speed = 0.55f + random.nextFloat() * 0.45f,
                size = 0.6f + random.nextFloat() * 0.8f,
                color = when (current.kind) {
                    EffectKind.HEARTS -> listOf(Color(0xFFFF4D6D), Color(0xFFFF758F), Color(0xFFFF99AC)).random(random)
                    EffectKind.SNOW -> Color.White
                    EffectKind.STARS -> listOf(Color(0xFFFFE082), Color.White, Color(0xFFB3E5FC)).random(random)
                    else -> PALETTE.random(random)
                },
                spin = random.nextFloat() * 360f,
            )
        }
    }
    val paint = remember(current) {
        android.graphics.Paint().apply {
            textSize = 64f
            isAntiAlias = true
        }
    }
    LaunchedEffect(current) {
        progress.animateTo(1f, tween(durationMillis = 3400, easing = LinearEasing))
        MessageEffects.finished()
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEach { p ->
            val local = ((t - p.phase) / (1f - p.phase)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            when (current.kind) {
                EffectKind.CONFETTI -> {
                    val x = p.x * size.width + sin(local * 8f * PI.toFloat() + p.spin) * 24f
                    val y = -40f + local * p.speed * (size.height + 80f)
                    rotate(p.spin + local * 540f, pivot = Offset(x, y)) {
                        drawRect(p.color, topLeft = Offset(x, y), size = Size(14f * p.size, 8f * p.size), alpha = 1f - local * 0.3f)
                    }
                }
                EffectKind.HEARTS -> {
                    val x = p.x * size.width + sin(local * 4f * PI.toFloat() + p.spin) * 30f
                    val y = size.height + 40f - local * p.speed * (size.height + 120f)
                    drawHeart(Offset(x, y), 26f * p.size, p.color, alpha = 1f - local * local)
                }
                EffectKind.BALLOONS -> {
                    val x = p.x * size.width + sin(local * 3f * PI.toFloat() + p.spin) * 20f
                    val y = size.height + 80f - local * p.speed * (size.height + 220f)
                    val w = 34f * p.size
                    drawOval(p.color, topLeft = Offset(x - w, y - w * 1.3f), size = Size(w * 2, w * 2.6f), alpha = 0.9f)
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(x, y + w * 1.3f), Offset(x + 6f, y + w * 2.8f), strokeWidth = 2f)
                }
                EffectKind.SNOW -> {
                    val x = p.x * size.width + sin(local * 5f * PI.toFloat() + p.spin) * 26f
                    val y = -20f + local * p.speed * (size.height + 40f)
                    drawCircle(p.color, radius = 5f * p.size, center = Offset(x, y), alpha = 0.9f - local * 0.3f)
                }
                EffectKind.STARS -> {
                    val x = p.x * size.width
                    val y = (0.08f + (p.spin / 360f) * 0.8f) * size.height
                    val twinkle = sin(local * PI.toFloat())
                    drawStar(Offset(x, y), 22f * p.size * (0.4f + twinkle * 0.6f), p.color, alpha = twinkle)
                }
                EffectKind.FIREWORKS -> {
                    val cx = (0.15f + p.x * 0.7f) * size.width
                    val cy = (0.15f + (p.spin / 360f) * 0.4f) * size.height
                    val radius = (60f + 190f * p.size) * local
                    repeat(18) { i ->
                        val angle = i * (2f * PI.toFloat() / 18f)
                        val start = Offset(cx + cos(angle) * radius * 0.7f, cy + sin(angle) * radius * 0.7f + local * local * 40f)
                        val end = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius + local * local * 40f)
                        drawLine(p.color, start, end, strokeWidth = 4f, alpha = 1f - local)
                    }
                }
                EffectKind.EMOJI_RAIN -> {
                    val x = p.x * size.width + sin(local * 4f * PI.toFloat() + p.spin) * 18f
                    val y = -60f + local * p.speed * (size.height + 120f)
                    paint.textSize = 52f + 40f * p.size
                    paint.alpha = ((1f - local * 0.4f) * 255).toInt()
                    drawContext.canvas.nativeCanvas.drawText(current.emoji.orEmpty(), x, y, paint)
                }
            }
        }
    }
}

private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius)
        cubicTo(center.x - radius * 2f, center.y - radius * 0.2f, center.x - radius * 0.9f, center.y - radius * 1.6f, center.x, center.y - radius * 0.5f)
        cubicTo(center.x + radius * 0.9f, center.y - radius * 1.6f, center.x + radius * 2f, center.y - radius * 0.2f, center.x, center.y + radius)
        close()
    }
    drawPath(path, color, alpha = alpha)
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color, alpha: Float) {
    val path = Path().apply {
        val inner = radius * 0.28f
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) radius else inner
            val angle = i * (PI.toFloat() / 4f) - PI.toFloat() / 2f
            val point = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
    drawPath(path, color, alpha = alpha.coerceIn(0f, 1f))
}
