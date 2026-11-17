package app.nova.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var api: Api
    private lateinit var actions: Actions
    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var mic: TextView
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var busy = false
    private var pending: Pair<Api.Action, Int>? = null // action waiting for a permission, and its chain depth

    private val cyan = Color.parseColor("#38E8FF")
    private val violet = Color.parseColor("#8B5CF6")
    private val ink = Color.parseColor("#EAF2FF")
    private val muted = Color.parseColor("#8A97AD")
    private val panel = Color.parseColor("#0E1420")

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun round(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        api = Api(prefs)
        actions = Actions(this)
        setContentView(buildUi())
        tts = TextToSpeech(this) { ok -> ttsReady = ok == TextToSpeech.SUCCESS }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { orb.state = OrbView.State.SPEAKING } }
            override fun onDone(id: String?) { main.post { if (!listening && !busy) orb.state = OrbView.State.IDLE } }
            @Deprecated("old API") override fun onError(id: String?) { main.post { orb.state = OrbView.State.IDLE } }
        })
        if (!prefs.paired) showSettings(firstRun = true) else loadHistory()
        if (intent?.action == Intent.ACTION_ASSIST) main.postDelayed({ startListening() }, 400)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_ASSIST && prefs.paired) startListening()
    }

    override fun onDestroy() {
        recognizer?.destroy(); tts?.shutdown(); io.shutdownNow()
        super.onDestroy()
    }

    /* ---------------- layout ---------------- */

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setPadding(dp(16), dp(8), dp(16), dp(12)) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            setText(R.string.app_name); setTextColor(ink); textSize = 22f; letterSpacing = 0.35f; typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = "⚙"; textSize = 22f; setTextColor(muted); setPadding(dp(12), dp(6), dp(4), dp(6))
            contentDescription = "Settings"; setOnClickListener { showSettings(false) }
        })
        root.addView(top)

        orb = OrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        status = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(muted); textSize = 13f; text = "Tap the mic and speak" }
        root.addView(status)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        scroll = ScrollView(this).apply { addView(list); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Type or speak…"; setHintTextColor(muted); setTextColor(ink); textSize = 16f
            background = round(panel, 24, Color.parseColor("#22304A")); setPadding(dp(16), dp(12), dp(16), dp(12))
            imeOptions = EditorInfo.IME_ACTION_SEND; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(TextView(this).apply {
            text = "➤"; textSize = 20f; setTextColor(cyan); gravity = Gravity.CENTER; contentDescription = "Send"
            setPadding(dp(14), dp(8), dp(10), dp(8)); setOnClickListener { sendTyped() }
        })
        mic = TextView(this).apply {
            text = "🎤"; textSize = 22f; gravity = Gravity.CENTER; contentDescription = "Speak"
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(cyan, violet)).apply { shape = GradientDrawable.OVAL }
            setOnClickListener { if (listening) stopListening() else startListening() }
        }
        bar.addView(mic, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(bar)
        return root
    }

    private fun bubble(role: String, msg: String) {
        val mine = role == "user"
        val tv = TextView(this).apply {
            text = msg; textSize = 15.5f; setTextColor(if (role == "note") muted else ink)
            setPadding(dp(14), dp(10), dp(14), dp(10)); setTextIsSelectable(true)
            background = when (role) {
                "user" -> round(Color.parseColor("#123A4A"), 18, Color.parseColor("#1E6E84"))
                "note" -> round(Color.TRANSPARENT, 12, Color.parseColor("#22304A"))
                else -> round(panel, 18, Color.parseColor("#22304A"))
            }
            if (role == "note") textSize = 13f
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (mine) Gravity.END else Gravity.START; topMargin = dp(6)
            if (mine) leftMargin = dp(48) else rightMargin = dp(48)
        }
        list.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    /* ---------------- conversation ---------------- */

    private fun loadHistory() {
        io.execute {
            try {
                val h = api.history()
                main.post { list.removeAllViews(); h.forEach { bubble(it.first, it.second) } }
            } catch (e: Exception) {
                main.post { bubble("note", e.message ?: "Could not load the conversation.") }
            }
        }
    }

    private fun sendTyped() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        send(t)
    }

    private fun send(t: String) {
        if (!prefs.paired) { showSettings(true); return }
        if (busy) return
        bubble("user", t)
        work("Thinking…") { api.say(t) }
    }

    /** Runs a server call off the main thread, then shows the reply and carries out any action. */
    private fun work(label: String, depth: Int = 0, call: () -> Api.Reply) {
        busy = true; orb.state = OrbView.State.THINKING; status.text = label
        io.execute {
            try {
                val r = call()
                main.post { busy = false; handle(r, depth) }
            } catch (e: Exception) {
                main.post { busy = false; orb.state = OrbView.State.IDLE; status.text = ""; bubble("note", e.message ?: "Something went wrong.") }
            }
        }
    }

    private fun handle(r: Api.Reply, depth: Int) {
        if (r.text.isNotBlank() && r.text != "…") { bubble("assistant", r.text); speak(r.text) }
        val a = r.actions.firstOrNull()
        if (a == null || depth >= 4) { status.text = ""; if (orb.state == OrbView.State.THINKING) orb.state = OrbView.State.IDLE; return }
        if (a.confirm) {
            AlertDialog.Builder(this, R.style.NovaDialog)
                .setTitle("Confirm")
                .setMessage(actions.describe(a.name, a.args))
                .setPositiveButton("Yes") { _, _ -> execute(a, depth) }
                .setNegativeButton("No") { _, _ -> report(a, "The user cancelled this.", depth) }
                .setCancelable(false)
                .show()
        } else execute(a, depth)
    }

    private fun execute(a: Api.Action, depth: Int) {
        try {
            val result = actions.run(a.name, a.args)
            bubble("note", "⚡ $result")
            report(a, result, depth)
        } catch (e: Actions.NeedsPermission) {
            pending = a to depth
            requestPermissions(arrayOf(e.permission), 2)
        }
    }

    private fun report(a: Api.Action, result: String, depth: Int) = work("…", depth + 1) { api.sendResult(a, result) }

    /* ---------------- voice ---------------- */

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { bubble("note", "Speech recognition is not available. Install or enable the Google app."); return }
        tts?.stop()
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { status.text = "Listening…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { orb.level = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "…" }
            override fun onError(err: Int) {
                stopListening()
                status.text = if (err == SpeechRecognizer.ERROR_NO_MATCH || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Didn't catch that. Tap the mic again." else "Mic error ($err)"
            }
            override fun onResults(b: Bundle?) {
                stopListening()
                val said = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (said.isNotEmpty()) send(said) else status.text = ""
            }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { input.setText(it) }
            }
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.voiceLang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        listening = true; orb.state = OrbView.State.LISTENING; mic.alpha = 0.6f
        rec.startListening(i)
    }

    private fun stopListening() {
        listening = false; mic.alpha = 1f; input.setText("")
        recognizer?.stopListening()
        if (!busy) orb.state = OrbView.State.IDLE
    }

    private fun speak(t: String) {
        if (!prefs.speakReplies || !ttsReady) return
        val bangla = t.any { it in 'ঀ'..'৿' }
        val loc = if (bangla) Locale("bn", "BD") else Locale.US
        if (tts?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED >= TextToSpeech.LANG_AVAILABLE) tts?.language = loc
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "nova-" + System.currentTimeMillis())
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        if (code == 1) { if (granted) startListening() else bubble("note", "The microphone permission is needed to talk to me.") }
        if (code == 2) {
            val p = pending ?: return
            pending = null
            if (granted) execute(p.first, p.second) else report(p.first, "Failed: the user did not allow access to contacts.", p.second)
        }
    }

    /* ---------------- settings ---------------- */

    private fun showSettings(firstRun: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun label(s: String) = TextView(this).apply { text = s; setTextColor(muted); textSize = 12f; setPadding(0, dp(12), 0, dp(2)) }
        val server = EditText(this).apply { setText(prefs.server); setSingleLine(); textSize = 14f }
        val code = EditText(this).apply { hint = "Pairing code"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS }
        val name = EditText(this).apply { setText(Build.MODEL); setSingleLine() }
        val langs = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val bn = RadioButton(this).apply { text = "বাংলা"; id = View.generateViewId() }
        val en = RadioButton(this).apply { text = "English"; id = View.generateViewId() }
        langs.addView(bn); langs.addView(en); langs.check(if (prefs.voiceLang == "en-US") en.id else bn.id)
        val speakBox = CheckBox(this).apply { text = "Speak replies aloud"; isChecked = prefs.speakReplies }
        box.addView(label("Server")); box.addView(server)
        if (!prefs.paired || firstRun) { box.addView(label("Pairing code (from the setup message)")); box.addView(code); box.addView(label("This phone's name")); box.addView(name) }
        else box.addView(label("Paired ✓"))
        box.addView(label("Voice language")); box.addView(langs); box.addView(speakBox)

        val d = AlertDialog.Builder(this, R.style.NovaDialog)
            .setTitle(if (firstRun) "Connect ${getString(R.string.app_name)}" else "Settings")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(if (!prefs.paired || firstRun) "Pair" else "Save", null)
            .setCancelable(!firstRun)
        if (prefs.paired && !firstRun) {
            d.setNeutralButton("Memory & chat") { _, _ -> showMemory() }
            d.setNegativeButton("Close", null)
        }
        val dlg = d.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.server = server.text.toString().trim().ifEmpty { BuildConfig.DEFAULT_SERVER }
                prefs.voiceLang = if (langs.checkedRadioButtonId == en.id) "en-US" else "bn-BD"
                prefs.speakReplies = speakBox.isChecked
                if (prefs.paired && !firstRun) { dlg.dismiss(); return@setOnClickListener }
                val c = code.text.toString().trim()
                if (c.isEmpty()) { code.error = "Enter the pairing code"; return@setOnClickListener }
                io.execute {
                    try {
                        val tok = api.pair(c, name.text.toString().trim().ifEmpty { "Phone" })
                        prefs.token = tok
                        main.post { dlg.dismiss(); bubble("note", "Connected. Try: \"torch jalao\", \"open YouTube\", \"set an alarm for 7\"."); loadHistory() }
                    } catch (e: Exception) {
                        main.post { code.error = e.message }
                    }
                }
            }
        }
        dlg.show()
    }

    private fun showMemory() {
        io.execute {
            val mem = try { api.memories() } catch (e: Exception) { emptyList() }
            main.post {
                val items = mem.map { it.second }.toTypedArray()
                AlertDialog.Builder(this, R.style.NovaDialog)
                    .setTitle(if (items.isEmpty()) "Nothing remembered yet" else "What I remember (tap to delete)")
                    .setItems(items) { _, i ->
                        val (id, t) = mem[i]
                        AlertDialog.Builder(this, R.style.NovaDialog).setMessage("Forget \"$t\"?")
                            .setPositiveButton("Forget") { _, _ -> io.execute { runCatching { api.deleteMemory(id) }; main.post { showMemory() } } }
                            .setNegativeButton("Keep", null).show()
                    }
                    .setPositiveButton("Close", null)
                    .setNegativeButton("Clear chat") { _, _ ->
                        io.execute { runCatching { api.clearHistory() }; main.post { list.removeAllViews(); bubble("note", "Chat cleared. Memories are kept.") } }
                    }
                    .show()
            }
        }
    }
}
