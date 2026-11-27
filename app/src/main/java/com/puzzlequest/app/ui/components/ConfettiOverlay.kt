package com.puzzlequest.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.puzzlequest.app.ui.theme.QuestColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class ConfettiIntensity {
    PUZZLE,
    SESSION_DONE,
}

private enum class ParticleShape {
    RIBBON,
    CIRCLE,
    STAR,
    DIAMOND,
}

private data class ConfettiParticle(
    val originX: Float,       // normalized [0..1]
    val originY: Float,       // normalized [0..1]
    val vx: Float,            // normalized widths / sec
    val vy: Float,            // normalized heights / sec (negative = upward)
    val gravity: Float,       // normalized heights / sec^2
    val drag: Float,          // exponential damping factor
    val swayAmplitude: Float, // normalized width sway
    val swayFreq: Float,      // radians / sec
    val swayPhase: Float,
    val initialRotation: Float,
    val spinSpeed: Float,     // degrees / sec
    val flipPhase: Float,
    val flipSpeed: Float,     // radians / sec
    val delaySec: Float,
    val lifeSec: Float,
    val sizeDp: Float,
    val aspectRatio: Float,   // width / height for ribbons
    val color: Color,
    val backColor: Color,
    val shape: ParticleShape,
)

/**
 * Full-bleed, non-blocking particle-based confetti overlay powered by Jetpack Compose Canvas.
 *
 * Uses normalized container coordinates with DrawScope.size so physics scale cleanly across
 * phones, foldables, tablets, and orientation changes without intercepting any touch input.
 */
@Composable
fun ConfettiOverlay(
    burstId: Int,
    intensity: ConfettiIntensity,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible || burstId <= 0) return

    val particles = remember(burstId, intensity) {
        generateParticles(seed = burstId * 7919 + intensity.ordinal * 31, intensity = intensity)
    }
    val totalDurationSec = remember(particles) {
        particles.maxOfOrNull { it.delaySec + it.lifeSec } ?: 0f
    }

    var elapsedSec by remember(burstId) { mutableFloatStateOf(0f) }

    LaunchedEffect(burstId, totalDurationSec) {
        elapsedSec = 0f
        if (totalDurationSec <= 0f) return@LaunchedEffect
        var startNanos = 0L
        while (elapsedSec < totalDurationSec) {
            withFrameNanos { frameNanos ->
                if (startNanos == 0L) startNanos = frameNanos
                elapsedSec = ((frameNanos - startNanos) / 1_000_000_000f).coerceAtLeast(0f)
            }
        }
    }

    if (elapsedSec >= totalDurationSec) return

    val reusablePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("confetti_overlay")
            .semantics {},
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val tNow = elapsedSec
        for (i in particles.indices) {
            val p = particles[i]
            val localT = tNow - p.delaySec
            if (localT <= 0f || localT >= p.lifeSec) continue

            val progress = localT / p.lifeSec

            // Exponential air drag gives a punchy launch followed by gentle floating drift.
            val dragIntegral = if (p.drag > 0.001f) {
                (1f - exp(-p.drag * localT)) / p.drag
            } else {
                localT
            }

            val sway = p.swayAmplitude * sin(p.swayFreq * localT + p.swayPhase) *
                (progress.coerceAtMost(0.35f) / 0.35f)

            val nx = p.originX + p.vx * dragIntegral + sway
            val ny = p.originY + p.vy * dragIntegral + 0.5f * p.gravity * localT * localT

            if (ny > 1.15f || nx < -0.15f || nx > 1.15f) continue

            val fadeIn = (progress / 0.05f).coerceIn(0f, 1f)
            val fadeOut = ((1f - progress) / 0.28f).coerceIn(0f, 1f)
            val alpha = fadeIn * fadeOut
            if (alpha <= 0.01f) continue

            val flipCos = cos(p.flipPhase + p.flipSpeed * localT)
            val flipScaleY = abs(flipCos).coerceAtLeast(0.22f)
            val fillColor = (if (flipCos >= 0f) p.color else p.backColor).copy(alpha = alpha)

            val cx = nx * w
            val cy = ny * h
            val basePx = p.sizeDp.dp.toPx()
            val rotationDeg = p.initialRotation + p.spinSpeed * localT

            withTransform({
                translate(left = cx, top = cy)
                rotate(degrees = rotationDeg, pivot = Offset.Zero)
                if (p.shape != ParticleShape.CIRCLE) {
                    scale(scaleX = 1f, scaleY = flipScaleY, pivot = Offset.Zero)
                }
            }) {
                drawParticle(
                    shape = p.shape,
                    baseSize = basePx,
                    aspectRatio = p.aspectRatio,
                    color = fillColor,
                    path = reusablePath,
                )
            }
        }
    }
}

private fun DrawScope.drawParticle(
    shape: ParticleShape,
    baseSize: Float,
    aspectRatio: Float,
    color: Color,
    path: Path,
) {
    when (shape) {
        ParticleShape.RIBBON -> {
            val rw = baseSize * aspectRatio
            val rh = baseSize * 0.55f
            drawRoundRect(
                color = color,
                topLeft = Offset(-rw / 2f, -rh / 2f),
                size = Size(rw, rh),
                cornerRadius = CornerRadius(rh * 0.35f, rh * 0.35f),
            )
        }

        ParticleShape.CIRCLE -> {
            drawCircle(
                color = color,
                radius = baseSize * 0.42f,
                center = Offset.Zero,
            )
        }

        ParticleShape.STAR -> {
            val outerR = baseSize * 0.65f
            val innerR = outerR * 0.44f
            path.reset()
            for (point in 0 until 10) {
                val radius = if (point % 2 == 0) outerR else innerR
                val angle = -PI / 2.0 + point * PI / 5.0
                val px = (cos(angle) * radius).toFloat()
                val py = (sin(angle) * radius).toFloat()
                if (point == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path = path, color = color)
        }

        ParticleShape.DIAMOND -> {
            val rx = baseSize * 0.48f
            val ry = baseSize * 0.66f
            path.reset()
            path.moveTo(0f, -ry)
            path.lineTo(rx, 0f)
            path.lineTo(0f, ry)
            path.lineTo(-rx, 0f)
            path.close()
            drawPath(path = path, color = color)
        }
    }
}

private fun generateParticles(
    seed: Int,
    intensity: ConfettiIntensity,
): List<ConfettiParticle> {
    val rng = Random(seed)
    val palette = QuestColors.ConfettiPalette
    val count = when (intensity) {
        ConfettiIntensity.PUZZLE -> 72
        ConfettiIntensity.SESSION_DONE -> 114
    }

    return List(count) { idx ->
        val wave = if (intensity == ConfettiIntensity.SESSION_DONE && idx >= count / 2) 1 else 0
        val emitter = idx % 3

        val originX = when (emitter) {
            0 -> 0.16f + rng.nextFloat() * 0.12f
            1 -> 0.72f + rng.nextFloat() * 0.12f
            else -> 0.40f + rng.nextFloat() * 0.20f
        }
        val originY = when {
            wave == 1 -> 0.12f + rng.nextFloat() * 0.14f
            emitter == 2 -> 0.26f + rng.nextFloat() * 0.08f
            else -> 0.34f + rng.nextFloat() * 0.10f
        }

        val vx = when (emitter) {
            0 -> 0.14f + rng.nextFloat() * 0.46f      // shoot rightward across screen
            1 -> -(0.14f + rng.nextFloat() * 0.46f)   // shoot leftward across screen
            else -> (rng.nextFloat() - 0.5f) * 0.78f  // center radial fan
        }
        val vy = if (wave == 1) {
            -(0.32f + rng.nextFloat() * 0.42f)
        } else {
            -(0.52f + rng.nextFloat() * 0.54f)
        }

        val baseColor = palette[rng.nextInt(palette.size)]
        val backColor = darkenSlightly(baseColor)

        val shapeRoll = rng.nextFloat()
        val shape = when {
            shapeRoll < 0.52f -> ParticleShape.RIBBON
            shapeRoll < 0.72f -> ParticleShape.CIRCLE
            shapeRoll < 0.88f -> ParticleShape.STAR
            else -> ParticleShape.DIAMOND
        }

        val delaySec = if (wave == 1) {
            0.34f + rng.nextFloat() * 0.26f
        } else {
            rng.nextFloat() * 0.16f
        }
        val lifeSec = when (intensity) {
            ConfettiIntensity.PUZZLE -> 1.85f + rng.nextFloat() * 0.75f
            ConfettiIntensity.SESSION_DONE -> 2.15f + rng.nextFloat() * 0.95f
        }

        ConfettiParticle(
            originX = originX,
            originY = originY,
            vx = vx,
            vy = vy,
            gravity = 0.88f + rng.nextFloat() * 0.42f,
            drag = 1.15f + rng.nextFloat() * 0.75f,
            swayAmplitude = 0.015f + rng.nextFloat() * 0.032f,
            swayFreq = 4.2f + rng.nextFloat() * 4.5f,
            swayPhase = rng.nextFloat() * (2f * PI.toFloat()),
            initialRotation = rng.nextFloat() * 360f,
            spinSpeed = (if (rng.nextBoolean()) 1f else -1f) * (120f + rng.nextFloat() * 240f),
            flipPhase = rng.nextFloat() * (2f * PI.toFloat()),
            flipSpeed = 5.5f + rng.nextFloat() * 6.5f,
            delaySec = delaySec,
            lifeSec = lifeSec,
            sizeDp = 7.5f + rng.nextFloat() * 6.5f,
            aspectRatio = 1.25f + rng.nextFloat() * 0.85f,
            color = baseColor,
            backColor = backColor,
            shape = shape,
        )
    }
}

private fun darkenSlightly(color: Color): Color = Color(
    red = (color.red * 0.82f).coerceIn(0f, 1f),
    green = (color.green * 0.82f).coerceIn(0f, 1f),
    blue = (color.blue * 0.82f).coerceIn(0f, 1f),
    alpha = color.alpha,
)
