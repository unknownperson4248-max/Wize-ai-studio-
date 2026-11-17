package app.nova.assistant

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opened by tapping the floating bubble: a small see-through listening card over whatever app is open.
 * It hands what was said to the bubble's conversation and closes, so the app below stays in front.
 */
class ListenActivity : Activity(), Ears.Listener {

    private lateinit var prefs: Prefs
    private lateinit var ears: Ears
    private lateinit var face: AvatarView
    private lateinit var label: TextView
    private var delivered = false
    private val main = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        ears = Ears(this, this)
        setContentView(buildUi())
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        else ears.start(prefs.voiceLang)
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setOnClickListener { finish() } }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isClickable = true
            setPadding(dp(12), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F20E1420")); cornerRadius = dp(30).toFloat(); setStroke(dp(1), Color.parseColor("#6638E8FF"))
            }
        }
        face = AvatarView(this).apply { disc = true; state = Mood.LISTENING }
        card.addView(face, LinearLayout.LayoutParams(dp(56), dp(56)))
        label = TextView(this).apply {
            text = "Listening…"; setTextColor(Color.parseColor("#EAF2FF")); textSize = 16f; maxLines = 3
            setPadding(dp(12), 0, 0, 0)
        }
        card.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(14), 0, dp(14), dp(40))
        })
        return root
    }

    override fun onReady() { label.text = "Listening… bolo" }
    override fun onLevel(level: Float) { face.level = level }
    override fun onPartial(text: String) { label.text = text }
    override fun onHeard(text: String) = deliver(text)

    override fun onNothing() {
        face.state = Mood.IDLE
        label.text = "Didn't catch that."
        main.postDelayed({ finish() }, 1000)
    }

    override fun onBroken(code: Int) {
        try {
            startActivityForResult(Ears.dialogIntent(prefs.voiceLang, "Bolo…"), 7)
        } catch (e: ActivityNotFoundException) {
            face.state = Mood.IDLE
            label.text = "Speech recognition is missing. Install or update the Google app."
            main.postDelayed({ finish() }, 3500)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7) return
        val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (resultCode == RESULT_OK && said.isNotEmpty()) deliver(said) else finish()
    }

    private fun deliver(text: String) {
        if (delivered) return
        delivered = true
        val service = NovaAccessibility.instance
        if (service != null) service.ask(text)
        else startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ASK, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) ears.start(prefs.voiceLang)
        else { label.text = "The microphone permission is needed to talk to me."; main.postDelayed({ finish() }, 2500) }
    }

    override fun onDestroy() {
        ears.stop()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
