package app.nova.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin

/**
 * The animated start screen: a drifting star field over a moving neon grid, NOVA's face building up
 * like a hologram, then the name and a Start button. Tap anywhere to skip once the app is set up.
 */
class IntroView(
    context: Context,
    private val autoClose: Boolean,
    private val removeSelf: Boolean = true,
    private val onDone: () -> Unit,
) : FrameLayout(context) {

    private val face = AvatarView(context).apply { reveal = 0f }
    private val title = TextView(context)
    private val sub = TextView(context)
    private val go = TextView(context)
    private var closing = false

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    init {
        setBackgroundColor(Color.parseColor("#05070C"))
        isClickable = true // the screen below does not get touches while the intro shows
        addView(StarsView(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(face, LinearLayout.LayoutParams(dp(250), dp(250)))
        title.apply {
            text = context.getString(R.string.app_name); textSize = 44f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); letterSpacing = 1.1f
            setTextColor(Color.parseColor("#EAF2FF")); setShadowLayer(dp(16).toFloat(), 0f, 0f, Color.parseColor("#38E8FF")); alpha = 0f
        }
        col.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        sub.apply {
            text = "Your personal AI\nবলো, আমি করে দিই"; textSize = 15f; gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f)
            setTextColor(Color.parseColor("#8A97AD")); alpha = 0f
        }
        col.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        go.apply {
            text = "Start  →"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#05070C")); setPadding(dp(40), dp(15), dp(40), dp(15)); alpha = 0f
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#38E8FF"), Color.parseColor("#8B5CF6"))).apply { cornerRadius = dp(30).toFloat() }
            visibility = if (autoClose) View.GONE else View.VISIBLE
            setOnClickListener { close() }
        }
        col.addView(go, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(40) })
        addView(col, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setOnClickListener { if (autoClose) close() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500; startDelay = 250; interpolator = DecelerateInterpolator()
            addUpdateListener { face.reveal = it.animatedValue as Float }
            start()
        }
        title.animate().alpha(1f).setStartDelay(1100).setDuration(700).start()
        ValueAnimator.ofFloat(1.1f, 0.45f).apply {
            duration = 1300; startDelay = 1100; interpolator = DecelerateInterpolator()
            addUpdateListener { title.letterSpacing = it.animatedValue as Float }
            start()
        }
        sub.translationY = dp(14).toFloat()
        sub.animate().alpha(1f).translationY(0f).setStartDelay(1600).setDuration(600).start()
        if (autoClose) postDelayed({ close() }, 3000)
        else go.animate().alpha(1f).setStartDelay(2100).setDuration(500).start()
    }

    fun close() {
        if (closing) return
        closing = true
        animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(450).withEndAction {
            if (removeSelf) (parent as? ViewGroup)?.removeView(this)
            onDone()
        }.start()
    }

    /** Stars drifting down, a soft nebula glow, and a neon grid floor rolling toward the viewer. */
    private class StarsView(context: Context) : View(context) {
        private val n = 110
        private val xs = FloatArray(n) { Math.random().toFloat() }
        private val ys = FloatArray(n) { Math.random().toFloat() }
        private val zs = FloatArray(n) { 0.2f + Math.random().toFloat() * 0.8f }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val born = SystemClock.uptimeMillis()

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            glow.shader = RadialGradient(w * 0.5f, h * 0.36f, w * 0.85f,
                intArrayOf(Color.argb(80, 70, 56, 200), Color.argb(26, 56, 232, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }

        override fun onDraw(c: Canvas) {
            val t = (SystemClock.uptimeMillis() - born) / 1000f
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawRect(0f, 0f, w, h, glow)
            for (i in 0 until n) {
                val y = (ys[i] + t * 0.012f * zs[i]) % 1f
                p.color = if (i % 3 == 0) Color.parseColor("#8B5CF6") else Color.WHITE
                p.alpha = ((0.35f + 0.65f * zs[i]) * (0.6f + 0.4f * sin(t * 2f + i)) * 255).toInt().coerceIn(20, 255)
                c.drawCircle(xs[i] * w, y * h * 0.72f, zs[i] * 2.2f, p)
            }
            val horizon = h * 0.76f
            p.strokeWidth = 2f
            p.color = Color.argb(150, 56, 232, 255)
            c.drawLine(0f, horizon, w, horizon, p)
            p.strokeWidth = 1.5f
            p.color = Color.argb(55, 56, 232, 255)
            for (k in -10..10) c.drawLine(w / 2 + k * w * 0.02f, horizon, w / 2 + k * w * 0.32f, h, p)
            for (j in 0 until 9) {
                val f = (j + (t * 0.6f) % 1f) / 9f
                val yy = horizon + (h - horizon) * f * f
                p.color = Color.argb((20 + 100 * f).toInt().coerceIn(0, 255), 56, 232, 255)
                c.drawLine(0f, yy, w, yy, p)
            }
            postInvalidateDelayed(16)
        }
    }
}
