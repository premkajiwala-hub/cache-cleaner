package com.cachecleaner.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cachecleaner.app.CleanResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/* Timings match the approved preview (claude.ai artifact "Clear-Cache Animation"). */
const val COUNT_DOWN_MS = 900
const val SWEEP_MS = 750

private val Mint = Color(0xFFA6F0D7)
private val Warm = Color(0xFFFFD79A)
private val ScrimColor = Color(0xFF040A0E)

/** Overshoots ~10% then settles, like the preview's back-out curve. */
val OvershootEasing = Easing { p ->
    val c1 = 1.9f
    val c3 = c1 + 1f
    val q = p - 1f
    1f + c3 * q * q * q + c1 * q * q
}

/** True when the phone's "Remove animations" (animator scale 0) is on. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

// ---------------------------------------------------------------------------
// Phase 2: specks drift in from the card edges and spiral into the number.
// Each speck is derived from its index, so the whole system is a pure function of time.
// ---------------------------------------------------------------------------

private const val SPAWN_EVERY_MS = 55f
private const val MAX_LIFE_MS = 1000f

private class Speck(
    val edge: Int,
    val along: Float,
    val lifeMs: Float,
    val sizeDp: Float,
    val colorIndex: Int,
    val swirlDp: Float,
    val rotation: Float,
)

private fun speck(index: Int): Speck {
    val r = Random(index * 7919 + 17)
    return Speck(
        edge = r.nextInt(4),
        along = r.nextFloat(),
        lifeMs = 650f + r.nextFloat() * 350f,
        sizeDp = 3f + r.nextFloat() * 4f,
        colorIndex = r.nextInt(3),
        swirlDp = (if (r.nextBoolean()) -1f else 1f) * (30f + r.nextFloat() * 50f),
        rotation = r.nextFloat() * 6f,
    )
}

/**
 * Draws the suction specks while [active]. When [active] turns false the specks
 * already in flight finish their trip instead of vanishing.
 * [target] is the number's centre in this canvas's coordinates.
 */
@Composable
fun SuctionSpecks(active: Boolean, target: Offset, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    var stopAt by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (active) {
            time = 0f
            stopAt = Float.MAX_VALUE
            running = true
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { time = (it - start).toFloat() }
            }
        } else if (running) {
            stopAt = time
            val base = time
            val start = withFrameMillis { it }
            while (time < stopAt + MAX_LIFE_MS) {
                withFrameMillis { time = base + (it - start) }
            }
            running = false
        }
    }

    if (!running) return
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer, Mint)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 6.dp.toPx()
        val last = (min(time, stopAt) / SPAWN_EVERY_MS).toInt()
        val first = max(0, ((time - MAX_LIFE_MS) / SPAWN_EVERY_MS).toInt())
        for (i in first..last) {
            val s = speck(i)
            val k = (time - i * SPAWN_EVERY_MS) / s.lifeMs
            if (k < 0f || k >= 1f) continue
            val x0 = when (s.edge) { 1 -> w + pad; 3 -> -pad; else -> s.along * w }
            val y0 = when (s.edge) { 0 -> -pad; 2 -> h + pad; else -> s.along * h }
            val e = k * k
            val dx = target.x - x0
            val dy = target.y - y0
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val bend = sin(k * PI.toFloat()) * s.swirlDp.dp.toPx()
            val x = x0 + dx * e + (-dy / len) * bend
            val y = y0 + dy * e + (dx / len) * bend
            val side = s.sizeDp.dp.toPx() * (1f - e * 0.85f)
            val alpha = (min(1f, k * 4f) * (1f - e * 0.8f)).coerceIn(0f, 1f)
            rotate(degrees = (s.rotation + k * 4f) * 57.3f, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = colors[s.colorIndex],
                    topLeft = Offset(x - side / 2, y - side / 2),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.3f),
                    alpha = alpha,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Phase 3: a soft light band wipes across the card.
// ---------------------------------------------------------------------------

fun DrawScope.drawSweep(progress: Float, tint: Color) {
    if (progress <= 0f || progress >= 1f) return
    val w = size.width
    val bandW = w * 0.45f
    val eased = if (progress < 0.5f) 4 * progress * progress * progress
    else 1 - (-2 * progress + 2).let { it * it * it } / 2
    val left = w * (-0.5f + 1.6f * eased)
    val alpha = sin(progress * PI.toFloat())
    withTransform({
        rotate(degrees = 12f, pivot = Offset(left + bandW / 2, size.height / 2))
    }) {
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to tint.copy(alpha = 0.22f * alpha),
                1f to Color.Transparent,
                startX = left,
                endX = left + bandW,
            ),
            topLeft = Offset(left, -size.height * 0.2f),
            size = Size(bandW, size.height * 1.4f),
        )
    }
}

// ---------------------------------------------------------------------------
// Phase 4 + 5: dimmed screen, tick badge, sparkle burst, "Freed X" counter.
// ---------------------------------------------------------------------------

private class Spark(val angle: Float, val speedDp: Float, val radiusDp: Float, val colorIndex: Int, val star: Boolean)

@Composable
fun CelebrationOverlay(
    result: CleanResult,
    reduceMotion: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val sparkColors = listOf(primary, Mint, Warm, Color.White)

    val scrim = remember { Animatable(0f) }
    val disc = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val amount = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var started by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinished)

    val sparks = remember(result) {
        val r = Random(result.freedBytes)
        List(26) { i ->
            Spark(
                angle = i / 26f * 2f * PI.toFloat() + r.nextFloat() * 0.25f,
                speedDp = 170f + r.nextFloat() * 150f,
                radiusDp = 2f + r.nextFloat() * 3.5f,
                colorIndex = i % 4,
                star = i % 3 == 0,
            )
        }
    }

    LaunchedEffect(result) {
        coroutineScope {
            if (reduceMotion) {
                started = true
                disc.snapTo(1f); check.snapTo(1f); amount.snapTo(1f); burst.snapTo(1f)
                launch { scrim.animateTo(1f, tween(250)) }
                text.animateTo(1f, tween(250))
            } else {
                delay(COUNT_DOWN_MS.toLong()) // let the card count down first
                started = true
                launch { scrim.animateTo(1f, tween(220)) }
                disc.animateTo(1f, tween(480, easing = OvershootEasing))
                launch { burst.animateTo(1f, tween(1100, easing = LinearEasing)) }
                launch { check.animateTo(1f, tween(340, easing = EaseOutCubic)) }
                launch { text.animateTo(1f, tween(300, easing = EaseOutCubic)) }
                amount.animateTo(1f, tween(850, easing = EaseOutCubic))
            }
        }
        var held = 0
        while (held < 1200 && !skip) {
            delay(50)
            held += 50
        }
        exit.animateTo(1f, tween(260))
        finish()
    }

    if (!started) return

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(ScrimColor.copy(alpha = 0.62f * scrim.value))
            .pointerInput(Unit) { detectTapGestures { skip = true } },
        contentAlignment = Alignment.Center,
    ) {
        if (!reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                val k = burst.value
                if (k <= 0f || k >= 1f) return@Canvas
                val e = 1f - (1f - k).let { it * it * it }
                val c = Offset(size.width / 2, size.height / 2 - 34.dp.toPx())
                for (s in sparks) {
                    val d = 60.dp.toPx() + s.speedDp.dp.toPx() * e
                    val p = Offset(c.x + cos(s.angle) * d, c.y + sin(s.angle) * d + 40.dp.toPx() * k * k)
                    val color = sparkColors[s.colorIndex].copy(alpha = 1f - k)
                    if (s.star) drawStar(p, s.radiusDp.dp.toPx() * 2.2f, k * 3f, color)
                    else drawCircle(color, s.radiusDp.dp.toPx() * (1f - k * 0.5f), p)
                }
                drawCircle(
                    color = primary.copy(alpha = (1f - k) * 0.5f),
                    radius = 56.dp.toPx() + 90.dp.toPx() * e,
                    center = c,
                    style = Stroke(width = (3f * (1f - k) + 0.5f).dp.toPx()),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        val s = disc.value * (1f - 0.15f * exit.value)
                        scaleX = s
                        scaleY = s
                    }
                    .background(primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(62.dp)) { drawCheck(check.value, onPrimary) }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = 10.dp.toPx() * (1f - text.value)
                },
            ) {
                Text(
                    context.formatSize((result.freedBytes * amount.value).toLong()),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "freed from ${result.appsCleaned} apps",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Draws the tick from the preview's 64×64 path (17,33 → 28,44 → 48,22), [progress] of its length. */
private fun DrawScope.drawCheck(progress: Float, color: Color) {
    if (progress <= 0f) return
    val u = size.width / 64f
    val full = Path().apply {
        moveTo(17f * u, 33f * u)
        lineTo(28f * u, 44f * u)
        lineTo(48f * u, 22f * u)
    }
    val measure = PathMeasure().apply { setPath(full, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), part, true)
    drawPath(part, color, style = Stroke(width = 7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawStar(center: Offset, radius: Float, rotation: Float, color: Color) {
    val path = Path()
    for (i in 0 until 8) {
        val r = if (i % 2 == 1) radius * 0.38f else radius
        val a = i * PI.toFloat() / 4f + rotation
        val x = center.x + cos(a) * r
        val y = center.y + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
