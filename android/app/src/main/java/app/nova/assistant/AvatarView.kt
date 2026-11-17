package app.nova.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * NOVA's face: a small animated cyber-girl, drawn in code (no image files).
 * Idle: floats and blinks. Listening: ear rings pulse and send out waves. Thinking: eyes wander, dots orbit.
 * Speaking: mouth moves. Drawn on a 100 x 100 grid and scaled to the view.
 */
class AvatarView(context: Context) : View(context) {

    var state = Mood.IDLE
        set(v) { field = v; invalidate() }
    /** 0..1 microphone loudness while listening. */
    var level = 0f
    /** Dark round badge with spinning rings behind the face (the floating bubble). */
    var disc = false
    /** 0..1: the face builds up from the top like a hologram (used by the intro). */
    var reveal = 1f
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val box = RectF()
    private val born = SystemClock.uptimeMillis()

    private val cyan = Color.parseColor("#38E8FF")
    private val violet = Color.parseColor("#8B5CF6")
    private val lash = Color.parseColor("#221840")
    private val hair = LinearGradient(0f, 10f, 0f, 90f,
        intArrayOf(Color.parseColor("#1D1644"), Color.parseColor("#4430AE"), Color.parseColor("#5AD6F4")),
        floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    private val skin = RadialGradient(46f, 44f, 34f,
        intArrayOf(Color.parseColor("#FFF4EF"), Color.parseColor("#FADBD2"), Color.parseColor("#EBBDBB")),
        floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    private val iris = LinearGradient(0f, 49f, 0f, 61f,
        intArrayOf(Color.parseColor("#2A2372"), Color.parseColor("#6A4CF0"), cyan),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val discBg = RadialGradient(50f, 42f, 56f,
        intArrayOf(Color.parseColor("#211A4A"), Color.parseColor("#070A14")), null, Shader.TileMode.CLAMP)
    private val halo = RadialGradient(50f, 50f, 50f,
        intArrayOf(Color.argb(90, 56, 232, 255), Color.argb(34, 139, 92, 246), Color.TRANSPARENT),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)

    override fun onAttachedToWindow() { super.onAttachedToWindow(); invalidate() }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }

    private fun solid(color: Int): Paint { fill.shader = null; fill.color = color; return fill }
    private fun shaded(s: Shader): Paint { fill.color = Color.BLACK; fill.shader = s; return fill }
    private fun pen(color: Int, w: Float): Paint { line.shader = null; line.color = color; line.strokeWidth = w; return line }
    private fun alpha(color: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    override fun onDraw(c: Canvas) {
        val t = (SystemClock.uptimeMillis() - born) / 1000f
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        c.save()
        c.translate((width - size) / 2f, (height - size) / 2f)
        c.scale(size / 100f, size / 100f)
        if (disc) backdrop(c, t) else c.drawCircle(50f, 50f, 50f, shaded(halo))
        c.save()
        if (disc) { path.reset(); path.addCircle(50f, 50f, 45.5f, Path.Direction.CW); c.clipPath(path) }
        if (reveal < 1f) c.clipRect(0f, 0f, 100f, 100f * reveal)
        val lift = if (disc) 3f else 0f
        c.translate(0f, sin(t * 1.7f) * 1.1f + lift)
        figure(c, t)
        c.restore()
        if (reveal < 1f) {
            val y = 100f * reveal
            c.drawRect(0f, y - 3f, 100f, y, solid(alpha(cyan, 50)))
            c.drawLine(4f, y, 96f, y, pen(cyan, 0.7f))
        }
        c.restore()
        postInvalidateDelayed(if (state == Mood.IDLE && reveal >= 1f) 40L else 16L)
    }

    private fun backdrop(c: Canvas, t: Float) {
        c.drawCircle(50f, 50f, 48f, shaded(discBg))
        val spin = when (state) { Mood.THINKING -> 160f; Mood.LISTENING -> 90f; else -> 35f }
        box.set(3f, 3f, 97f, 97f)
        c.save()
        c.rotate((t * spin) % 360f, 50f, 50f)
        c.drawArc(box, 0f, 110f, false, pen(alpha(cyan, 220), 1.6f + level * 2f))
        c.drawArc(box, 180f, 70f, false, pen(alpha(violet, 220), 1.6f))
        c.restore()
    }

    private fun figure(c: Canvas, t: Float) {
        // hair behind the head
        c.drawCircle(50f, 44f, 31f, shaded(hair))
        // shoulders, neck and the glowing collar
        path.reset()
        path.moveTo(15f, 101f); path.cubicTo(17f, 87f, 32f, 82f, 50f, 82f); path.cubicTo(68f, 82f, 83f, 87f, 85f, 101f); path.close()
        c.drawPath(path, solid(Color.parseColor("#141B33")))
        c.drawRect(44.5f, 66f, 55.5f, 84f, solid(Color.parseColor("#EDC6BF")))
        path.reset(); path.moveTo(37f, 84f); path.quadTo(50f, 91f, 63f, 84f)
        c.drawPath(path, pen(alpha(cyan, 200), 1.2f))
        c.drawCircle(50f, 89.5f, 1.3f + 0.4f * sin(t * 3f), solid(cyan))
        // long side locks
        box.set(19f, 38f, 33f, 87f); c.drawRoundRect(box, 7f, 7f, shaded(hair))
        box.set(67f, 38f, 81f, 87f); c.drawRoundRect(box, 7f, 7f, shaded(hair))
        // face
        box.set(28.5f, 23f, 71.5f, 73f); c.drawOval(box, shaded(skin))
        path.reset(); path.moveTo(31f, 57f); path.quadTo(38f, 74.5f, 50f, 75.5f); path.quadTo(62f, 74.5f, 69f, 57f); path.close()
        c.drawPath(path, shaded(skin))
        eyes(c, t)
        // blush, nose, and a small glowing circuit line on the cheek
        box.set(32.5f, 61f, 39.5f, 64.5f); c.drawOval(box, solid(Color.argb(80, 255, 128, 168)))
        box.set(60.5f, 61f, 67.5f, 64.5f); c.drawOval(box, solid(Color.argb(80, 255, 128, 168)))
        c.drawLine(50.2f, 61.4f, 49.5f, 62.6f, pen(Color.parseColor("#D49C98"), 0.9f))
        val glow = (150 + 80 * sin(t * 2.4f)).toInt()
        path.reset(); path.moveTo(62.5f, 67f); path.lineTo(65.5f, 67f); path.lineTo(67f, 65.3f)
        c.drawPath(path, pen(alpha(cyan, glow), 0.7f))
        c.drawCircle(67.6f, 64.7f, 0.65f, solid(alpha(cyan, glow)))
        mouth(c, t)
        // fringe
        path.reset()
        path.moveTo(28.5f, 52f); path.lineTo(28f, 40f); path.cubicTo(29f, 16f, 71f, 16f, 72f, 40f); path.lineTo(71.5f, 52f)
        path.quadTo(71f, 44f, 67f, 40f); path.quadTo(66f, 44f, 64f, 47.5f)
        path.quadTo(62f, 42f, 59f, 39f); path.quadTo(57f, 43f, 55f, 46f)
        path.quadTo(52.5f, 41.5f, 49.5f, 38.5f); path.quadTo(47.5f, 42.5f, 44.5f, 46.5f)
        path.quadTo(42.5f, 41.5f, 39.5f, 39.5f); path.quadTo(36f, 44f, 33.5f, 49f)
        path.quadTo(31f, 46f, 28.5f, 52f); path.close()
        c.drawPath(path, shaded(hair))
        path.reset()
        path.moveTo(29f, 40f); path.quadTo(26.5f, 56f, 31f, 67f); path.quadTo(31f, 54f, 34.5f, 44f); path.close()
        path.moveTo(71f, 40f); path.quadTo(73.5f, 56f, 69f, 67f); path.quadTo(69f, 54f, 65.5f, 44f); path.close()
        c.drawPath(path, shaded(hair))
        box.set(35f, 17f, 65f, 40f); c.drawArc(box, 205f, 45f, false, pen(Color.argb(70, 255, 255, 255), 1f))
        // headband and ear pieces: the robotic touch
        box.set(27.5f, 19f, 72.5f, 85f)
        c.drawArc(box, 180f, 180f, false, pen(Color.argb(170, 174, 185, 221), 1.5f))
        c.drawArc(box, 222f, 30f, false, pen(alpha(cyan, 150), 0.7f))
        val beat = when (state) {
            Mood.LISTENING -> 0.5f + 0.5f * sin(t * 8f) + level
            Mood.SPEAKING -> 0.5f + 0.5f * sin(t * 12f)
            else -> 0.3f + 0.2f * sin(t * 2f)
        }
        for (x in floatArrayOf(27.5f, 72.5f)) {
            c.drawCircle(x, 52f, 6.2f, solid(Color.parseColor("#1A2342")))
            c.drawCircle(x, 52f, 5f, pen(alpha(cyan, (140 + 110 * beat).toInt()), 1.4f))
            c.drawCircle(x, 52f, 1.5f + beat.coerceIn(0f, 1.2f), solid(cyan))
        }
        effects(c, t)
    }

    private fun eyes(c: Canvas, t: Float) {
        val phase = t % 4.3f
        var open = if (phase > 4.05f) (abs(phase - 4.175f) / 0.125f).coerceIn(0.05f, 1f) else 1f
        if (state == Mood.LISTENING) open *= 1.06f
        val lx = when (state) { Mood.THINKING -> 1.6f * sin(t * 1.3f); Mood.IDLE -> 0.7f * sin(t * 0.45f); else -> 0f }
        val ly = if (state == Mood.THINKING) -1.6f else 0f
        for (side in 0..1) {
            val ex = if (side == 0) 40f else 60f
            val ey = 55f
            val browLift = if (state == Mood.THINKING && side == 1) -1f else 0f
            box.set(ex - 4f, ey - 11.5f + browLift, ex + 4f, ey - 8f + browLift)
            c.drawArc(box, 200f, 140f, false, pen(Color.parseColor("#3A2C7A"), 1f))
            if (open < 0.3f) {
                box.set(ex - 5f, ey - 3f, ex + 5f, ey + 3f)
                c.drawArc(box, 20f, 140f, false, pen(lash, 1.4f))
                continue
            }
            val ew = 5.2f
            val eh = 6.4f * open
            box.set(ex - ew, ey - eh, ex + ew, ey + eh)
            c.drawOval(box, solid(Color.WHITE))
            c.save()
            path.reset(); path.addOval(box, Path.Direction.CW); c.clipPath(path)
            box.set(ex - 4.4f + lx, ey - 5.6f + ly, ex + 4.4f + lx, ey + 5.6f + ly); c.drawOval(box, shaded(iris))
            box.set(ex - 2f + lx, ey - 2.8f + ly, ex + 2f + lx, ey + 2.2f + ly); c.drawOval(box, solid(Color.parseColor("#0E0B2A")))
            c.drawCircle(ex - 1.6f + lx, ey - 2.6f + ly, 1.5f, solid(Color.WHITE))
            c.drawCircle(ex + 1.8f + lx, ey + 2.4f + ly, 0.7f, solid(Color.WHITE))
            c.restore()
            box.set(ex - ew - 0.4f, ey - eh - 0.2f, ex + ew + 0.4f, ey + eh)
            c.drawArc(box, 195f, 150f, false, pen(lash, 1.6f))
            val out = if (side == 0) -1f else 1f
            c.drawLine(ex + out * ew, ey - 1.5f * open, ex + out * (ew + 1.8f), ey - 3.4f * open, pen(lash, 1.2f))
        }
    }

    private fun mouth(c: Canvas, t: Float) {
        when (state) {
            Mood.SPEAKING -> {
                val o = 0.6f + 2.6f * abs(sin(t * 13f)) * (0.65f + 0.35f * sin(t * 3.1f))
                box.set(47.4f, 66.4f, 52.6f, 67f + o); c.drawOval(box, solid(Color.parseColor("#B83A5E")))
            }
            Mood.LISTENING -> {
                box.set(48.8f, 66.3f, 51.2f, 68.5f); c.drawOval(box, solid(Color.parseColor("#C04A6A")))
            }
            Mood.THINKING -> {
                path.reset(); path.moveTo(48f, 68f); path.quadTo(50f, 67.3f, 52.6f, 67.6f)
                c.drawPath(path, pen(Color.parseColor("#B5506A"), 1.1f))
            }
            Mood.IDLE -> {
                box.set(46.5f, 64.4f, 53.5f, 69f); c.drawArc(box, 25f, 130f, false, pen(Color.parseColor("#C2576E"), 1.2f))
            }
        }
    }

    private fun effects(c: Canvas, t: Float) {
        if (state == Mood.LISTENING) {
            for (k in 0..2) {
                val ph = (t * 1.6f + k / 3f) % 1f
                val rr = 7f + ph * 9f
                val p = pen(alpha(cyan, ((1f - ph) * 200).toInt()), 1f)
                box.set(27.5f - rr, 52f - rr, 27.5f + rr, 52f + rr); c.drawArc(box, 140f, 80f, false, p)
                box.set(72.5f - rr, 52f - rr, 72.5f + rr, 52f + rr); c.drawArc(box, -40f, 80f, false, p)
            }
        }
        if (state == Mood.THINKING) {
            for (i in 0..2) {
                val a = t * 3f + i * 2.09f
                c.drawCircle(50f + cos(a) * 10f, 9f + sin(a) * 2.5f, 1.4f, solid(if (i % 2 == 0) cyan else violet))
            }
        }
    }
}
