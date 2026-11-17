package app.nova.assistant.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import app.nova.assistant.Mood
import app.nova.assistant.ui.theme.J
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The JARVIS core: an electric-blue sphere inside silver rings, with a ring of bars that moves with the
 * microphone level while listening, with the voice while speaking, and as a running wave while thinking.
 */
@Composable
fun Visualizer(mood: Mood, level: Float, modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    val lv by animateFloatAsState(level, label = "level")

    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.24f
        val speed = when (mood) { Mood.IDLE -> 0.5f; Mood.LISTENING -> 1.2f; Mood.THINKING -> 2.4f; Mood.SPEAKING -> 1.6f }
        val pulse = when (mood) {
            Mood.IDLE -> 0.025f * sin(t * 1.6f)
            Mood.LISTENING -> 0.04f + lv * 0.16f
            Mood.THINKING -> 0.03f * sin(t * 6f)
            Mood.SPEAKING -> 0.06f * (0.5f + 0.5f * sin(t * 9f))
        }
        val r = base * (1f + pulse)

        // glow
        drawCircle(Brush.radialGradient(listOf(J.Blue.copy(alpha = 0.32f), J.BlueDeep.copy(alpha = 0.14f), Color.Transparent), c, base * 2.3f), base * 2.3f, c)
        // sphere
        drawCircle(
            Brush.radialGradient(listOf(Color.White, J.Cyan, J.Blue, Color(0xFF031223)), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.45f),
            r, c,
        )
        // silver inner ring
        drawCircle(J.Silver.copy(alpha = 0.28f), r * 1.13f, c, style = Stroke(1.2f * density))

        // rotating arcs
        for (k in 0 until 3) {
            val rr = r * (1.24f + k * 0.11f)
            val w = (3.2f - k * 0.8f) * density
            rotate((t * 38f * speed * (if (k % 2 == 0) 1 else -1) + k * 70f) % 360f, c) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, J.Blue, J.Silver, Color.Transparent), c),
                    startAngle = 0f, sweepAngle = 210f - k * 45f, useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }

        // voice bars
        val bars = 72
        for (i in 0 until bars) {
            val a = (i * 2 * PI / bars).toFloat()
            val noise = 0.5f + 0.25f * (sin(t * 6f + i * 0.7f) + sin(t * 3.3f + i * 1.9f))
            val amp = when (mood) {
                Mood.LISTENING -> (0.12f + lv) * noise
                Mood.SPEAKING -> (0.3f + 0.3f * sin(t * 9f)) * noise
                Mood.THINKING -> 0.28f * max(0f, sin(t * 4f - i * 0.35f))
                Mood.IDLE -> 0.05f * noise
            }
            val len = base * 0.04f + base * 0.5f * amp
            val d = Offset(cos(a), sin(a))
            val from = r * 1.62f
            drawLine(
                color = lerp(J.Blue, J.Silver, if (i % 3 == 0) 0.6f else 0.1f).copy(alpha = 0.55f + 0.45f * amp.coerceIn(0f, 1f)),
                start = c + d * from, end = c + d * (from + len),
                strokeWidth = 2.2f * density, cap = StrokeCap.Round,
            )
        }
    }
}

/** A small microphone drawing (the core icon set has no mic). */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        drawRoundRect(color, topLeft = Offset(w * 0.36f, h * 0.12f), size = Size(w * 0.28f, h * 0.46f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, topLeft = Offset(w * 0.24f, h * 0.22f), size = Size(w * 0.52f, h * 0.5f), style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.86f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.88f), Offset(w * 0.64f, h * 0.88f), sw, StrokeCap.Round)
    }
}
