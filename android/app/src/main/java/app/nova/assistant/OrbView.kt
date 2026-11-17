package app.nova.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The animated core in the middle of the screen. Its motion shows what the assistant is doing. */
class OrbView(context: Context) : View(context) {


    var state = Mood.IDLE
        set(v) { field = v; invalidate() }
    /** 0..1 microphone loudness while listening. */
    var level = 0f

    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t += 0.016f; invalidate() }
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.parseColor("#38E8FF")
    private val violet = Color.parseColor("#8B5CF6")

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.30f
        val speed = when (state) { Mood.IDLE -> 0.6f; Mood.LISTENING -> 1.4f; Mood.THINKING -> 2.6f; Mood.SPEAKING -> 1.8f }
        val pulse = when (state) {
            Mood.IDLE -> 0.03f * sin(t * 1.6f)
            Mood.LISTENING -> 0.05f + level * 0.22f
            Mood.THINKING -> 0.04f * sin(t * 6f)
            Mood.SPEAKING -> 0.08f * (0.5f + 0.5f * sin(t * 9f))
        }
        val r = base * (1f + pulse)

        // outer glow
        glow.shader = RadialGradient(cx, cy, r * 2.1f, intArrayOf(Color.argb(110, 56, 232, 255), Color.argb(40, 139, 92, 246), Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 2.1f, glow)

        // core sphere
        core.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.3f, intArrayOf(Color.WHITE, cyan, violet, Color.parseColor("#120A2A")), floatArrayOf(0f, 0.25f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, core)

        // rotating rings
        for (k in 0 until 3) {
            val rr = r * (1.25f + k * 0.18f)
            ring.strokeWidth = r * (0.035f - k * 0.008f)
            ring.shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, cyan, violet, Color.TRANSPARENT), null)
            c.save()
            c.rotate((t * 40f * speed * (if (k % 2 == 0) 1 else -1) + k * 60f) % 360f, cx, cy)
            c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, 0f, 200f - k * 40f, false, ring)
            c.restore()
        }

        // orbiting particles
        for (i in 0 until 14) {
            val a = t * speed * (0.5f + (i % 5) * 0.12f) + i * 0.45f
            val d = r * (1.55f + 0.35f * sin(t * 0.7f + i))
            dot.color = if (i % 2 == 0) cyan else violet
            dot.alpha = (120 + 100 * sin(t * 2f + i)).toInt().coerceIn(40, 230)
            c.drawCircle(cx + cos(a) * d, cy + sin(a) * d, r * 0.028f, dot)
        }
    }
}
