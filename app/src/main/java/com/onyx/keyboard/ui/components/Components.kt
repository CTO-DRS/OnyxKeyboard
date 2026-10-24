package com.onyx.keyboard.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Particle field — drifting glowing motes, drawn entirely in the draw phase.
// ---------------------------------------------------------------------------

private data class Particle(
    val x: Float,        // 0..1 start position
    val y: Float,
    val vx: Float,       // fraction of width per cycle
    val vy: Float,
    val radiusDp: Float,
    val twinkle: Float,  // cycles per loop
    val phase: Float,
) {
    companion object {
        fun random(rng: Random = Random) = Particle(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            vx = (rng.nextFloat() - 0.5f) * 0.35f,
            vy = -0.15f - rng.nextFloat() * 0.5f,
            radiusDp = 1f + rng.nextFloat() * 2.2f,
            twinkle = 0.5f + rng.nextFloat() * 1.5f,
            phase = rng.nextFloat(),
        )
    }
}

@Composable
fun ParticleField(
    modifier: Modifier = Modifier,
    color: Color,
    count: Int = 32,
    maxAlpha: Float = 0.55f,
) {
    val particles = remember(count) {
        List(count) { Particle.random() }
    }
    val transition = rememberInfiniteTransition(label = "particles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "particleT"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            // Wrap positions into [0,1].
            val px = ((p.x + p.vx * t) % 1f + 1f) % 1f
            val py = ((p.y + p.vy * t) % 1f + 1f) % 1f
            val alpha = maxAlpha * (0.35f + 0.65f * (0.5f + 0.5f * sin(2.0 * Math.PI * (p.twinkle * t + p.phase)).toFloat()))
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = p.radiusDp.dp.toPx(),
                center = Offset(px * w, py * h)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Animated Onyx logo — gradient ring + orbiting spark + core.
// ---------------------------------------------------------------------------

@Composable
fun OnyxLogo(size: Dp = 64.dp, accent: Color = Color(0xFFB37BFF), glow: Color = Color(0xFF3EE6FF)) {
    val transition = rememberInfiniteTransition(label = "logo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "logoRotation"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "logoBreathe"
    )

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.075f
            val radius = this.size.minDimension / 2f - stroke * 1.6f
            // Static gradient ring.
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to accent.copy(alpha = 0.35f),
                    0.5f to accent,
                    0.75f to glow,
                    1f to accent.copy(alpha = 0.35f)
                ),
                radius = radius,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // Orbiting spark.
            rotate(degrees = rotation) {
                drawCircle(
                    color = glow,
                    radius = stroke * 0.9f,
                    center = Offset(center.x + radius, center.y)
                )
            }
            // Breathing core.
            drawCircle(
                color = accent.copy(alpha = 0.9f),
                radius = radius * 0.32f * breathe
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Glass card — translucent panel with gradient hairline border.
// ---------------------------------------------------------------------------

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.025f))
                )
            )
            .background(Color(0xFF12101C).copy(alpha = 0.55f))
            .padding(16.dp),
        content = content
    )
}

/** Small section heading with an accent bar. */
@Composable
fun SectionTitle(text: String, accent: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
