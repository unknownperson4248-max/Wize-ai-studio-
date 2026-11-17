package app.nova.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.nova.assistant.data.JarvisDb
import app.nova.assistant.ui.Approval
import app.nova.assistant.ui.ChatMsg
import app.nova.assistant.ui.Controller
import app.nova.assistant.ui.CoreState
import app.nova.assistant.ui.JarvisApp
import app.nova.assistant.ui.theme.JarvisTheme
import app.nova.assistant.wake.WakeModel
import app.nova.assistant.wake.WakeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The JARVIS app: hosts the screens, the voice, and the conversation loop for the app (the bubble has its own). */
class MainActivity : ComponentActivity(), Controller, Brain.Ui, Ears.Listener {

    companion object {
        const val EXTRA_ASK = "ask"
        const val EXTRA_PERMISSION = "permission"
    }

    override val state = CoreState()
    private val brain by lazy { Brain(this, this, "app") }
    private val ears by lazy { Ears(this, this) }
    override val prefs: Prefs by lazy { brain.prefs }
    override val db: JarvisDb by lazy { JarvisDb.get(this) }
    private val main = Handler(Looper.getMainLooper())
    private var listenAfterGrant = false
    private var permissionAnswer: ((Boolean) -> Unit)? = null
    private val ticker = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 3000) }
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh()
        if (ok && listenAfterGrant) startListening()
        if (!ok) note("The microphone permission is needed to talk to me.")
        listenAfterGrant = false
    }
    private val otherPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh()
        val a = permissionAnswer
        permissionAnswer = null
        a?.invoke(ok)
    }
    private val voiceDialog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        MicLock.release("voice-dialog")
        if (r.resultCode == RESULT_OK && said.isNotEmpty()) send(said, spoken = true)
    }

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startWake() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // draw behind the status bar and the gesture bar; the screens add the right padding themselves
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val assist = intent?.action == Intent.ACTION_ASSIST
        state.showIntro = savedInstanceState == null && !assist
        refresh()
        if (prefs.paired) loadHistory()
        checkAi()
        setContent { JarvisTheme { JarvisApp(this) } }
        if (assist && prefs.paired) { state.goTo = "core"; main.postDelayed({ startListening() }, 300) }
        handleExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST && prefs.paired) { state.goTo = "core"; startListening() } else handleExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        main.post(ticker)
        // Android may have stopped "Hey Jarvis"; a microphone service can only be started while JARVIS is on screen
        if (prefs.wakeWord && !WakeService.running && WakeModel.ready(this) && granted(Manifest.permission.RECORD_AUDIO)) startWake()
    }
    override fun onPause() { main.removeCallbacks(ticker); super.onPause() }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        ears.stop(); brain.close()
        super.onDestroy()
    }

    private fun handleExtras(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_ASK)?.let { i.removeExtra(EXTRA_ASK); state.goTo = "core"; send(it) }
        i.getStringExtra(EXTRA_PERMISSION)?.let { i.removeExtra(EXTRA_PERMISSION); otherPermission.launch(it) }
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Reads the real state of permissions and services for the screens. */
    private fun refresh() {
        val control = NovaAccessibility.instance
        state.paired = prefs.paired
        state.micGranted = granted(Manifest.permission.RECORD_AUDIO)
        state.contactsGranted = granted(Manifest.permission.READ_CONTACTS)
        state.controlOn = control != null
        state.foregroundApp = control?.currentApp().orEmpty()
        state.batteryFree = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        state.paused = prefs.automationPaused
        state.bubble = prefs.bubble
        state.busy = brain.busy
        state.wakeOn = WakeService.running
        state.conversation = prefs.conversation
        state.voiceInfo = brain.voiceInfo
        state.banglaMissing = brain.banglaVoiceMissing
    }

    private fun loadHistory() {
        brain.background {
            try {
                val h = brain.api.history()
                main.post { state.messages.clear(); h.forEach { state.messages.add(ChatMsg(it.first, it.second)) } }
            } catch (e: Exception) {
                main.post { note(e.message ?: "Could not load the conversation.") }
            }
        }
    }

    /* ---------------- Brain.Ui ---------------- */

    override fun mood(m: Mood) { if (!state.listening) state.mood = m; state.busy = brain.busy }
    override fun status(text: String) { state.status = text; state.busy = brain.busy }
    override fun reply(text: String) { state.messages.add(ChatMsg("assistant", text)) }
    override fun note(text: String) { state.messages.add(ChatMsg("note", text)) }
    override fun confirm(question: String, answer: (Boolean) -> Unit) { state.approval = Approval(question, answer) }
    override fun permission(permission: String, answer: (Boolean) -> Unit) {
        permissionAnswer = answer
        otherPermission.launch(permission)
    }

    /** Conversation mode: JARVIS finished its spoken answer, so listen for the follow-up (only while this screen is open). */
    override fun listenAgain() {
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && !state.listening) startListening()
    }

    /* ---------------- voice ---------------- */

    private fun startListening() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) { listenAfterGrant = true; micPermission.launch(Manifest.permission.RECORD_AUDIO); return }
        brain.hush()
        state.listening = true; state.mood = Mood.LISTENING; state.partial = ""; state.status = "Starting the mic…"
        ears.start(prefs.voiceLang)
    }

    private fun micOff() {
        state.listening = false; state.level = 0f; state.partial = ""
        state.mood = if (brain.busy) Mood.THINKING else Mood.IDLE
    }

    override fun onReady() { state.status = "Listening…" }
    override fun onLevel(level: Float) { state.level = level }
    override fun onPartial(text: String) { state.partial = text }
    override fun onHeard(text: String) { micOff(); state.status = ""; send(text, spoken = true) }
    override fun onNothing() { micOff(); state.status = "Didn't catch that. Tap the mic again." }

    /** The phone's speech service refused: use the system voice dialog instead. */
    override fun onBroken(code: Int) {
        micOff(); state.status = ""
        try {
            MicLock.acquire("voice-dialog")
            voiceDialog.launch(Ears.dialogIntent(prefs.voiceLang, "Bolo…"))
        } catch (e: ActivityNotFoundException) {
            MicLock.release("voice-dialog")
            note("Speech recognition is not available on this phone (error $code). Install or update the Google app, then try again.")
        }
    }

    /* ---------------- Controller ---------------- */

    override fun send(text: String) = send(text, spoken = false)

    private fun send(text: String, spoken: Boolean) {
        if (!prefs.paired) { note("Pair this phone first (Settings)."); return }
        if (brain.busy) { note("One moment, I'm still working on the last thing. Tap Stop to cancel it."); return }
        state.messages.add(ChatMsg("user", text))
        brain.ask(text, spoken)
        state.busy = brain.busy
    }

    override fun toggleMic() {
        if (state.listening) { ears.stop(); micOff(); state.status = "" } else startListening()
    }

    override fun hush() { brain.hush() }

    override fun emergencyStop() {
        ears.stop(); micOff()
        state.approval = null
        brain.stop()
        NovaAccessibility.instance?.stopTask()
        state.busy = false
        state.status = "Stopped"
    }

    override fun setPaused(paused: Boolean) { prefs.automationPaused = paused; state.paused = paused }

    override fun setBubble(on: Boolean) {
        prefs.bubble = on; state.bubble = on
        NovaAccessibility.instance?.refreshBubble()
        if (on && NovaAccessibility.instance == null) note("The bubble appears once phone control is on (Control tab).")
    }

    override fun requestMic() { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    override fun requestContacts() { otherPermission.launch(Manifest.permission.READ_CONTACTS) }

    private fun openSettings(i: Intent) {
        try { startActivity(i) } catch (e: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    override fun openAccessibilitySettings() = openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    override fun openAppInfo() = openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    override fun turnOffControl() {
        NovaAccessibility.instance?.turnOff()
        main.postDelayed({ refresh() }, 600)
    }

    /** Some phones (Honor, Xiaomi, Oppo) close background services to save battery; this asks to keep JARVIS running. */
    override fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun checkAi() {
        state.aiOnline = null
        brain.background {
            val r = runCatching { brain.api.health() }
            main.post {
                state.aiOnline = r.isSuccess
                state.aiInfo = r.getOrNull() ?: (r.exceptionOrNull()?.message ?: "")
            }
        }
    }

    override fun pair(code: String, name: String, done: (error: String?) -> Unit) {
        brain.background {
            try {
                val tok = brain.api.pair(code, name)
                prefs.token = tok
                main.post { state.paired = true; done(null); loadHistory(); checkAi() }
            } catch (e: Exception) {
                main.post { done(e.message ?: "Pairing failed.") }
            }
        }
    }

    override fun unpair(done: () -> Unit) {
        brain.background {
            runCatching { brain.api.unpair() }
            prefs.token = ""
            main.post { state.paired = false; state.messages.clear(); done() }
        }
    }

    override fun testVoice() {
        brain.say(if (prefs.voiceLang == "en-US") "Hello, I am JARVIS. How can I help?" else "আমি জার্ভিস। বলো, কী করতে হবে?")
    }

    /** Hands-free "Hey Jarvis": needs the microphone, the offline model (downloaded once) and, on Android 13+, the notification. */
    override fun setWakeWord(on: Boolean) {
        prefs.wakeWord = on
        if (!on) { WakeService.stop(this); state.wakeOn = false; state.wakeMessage = ""; return }
        if (!granted(Manifest.permission.RECORD_AUDIO)) { micPermission.launch(Manifest.permission.RECORD_AUDIO); state.wakeMessage = "Allow the microphone, then switch it on again."; prefs.wakeWord = false; return }
        if (!WakeModel.ready(this)) {
            state.wakeProgress = 0f; state.wakeMessage = "Downloading the offline \"Hey Jarvis\" model (about 40 MB)…"
            Thread {
                val r = runCatching { WakeModel.download(this) { p -> main.post { state.wakeProgress = p } } }
                main.post {
                    state.wakeProgress = null
                    if (r.isSuccess) setWakeWord(true)
                    else { prefs.wakeWord = false; state.wakeOn = false; state.wakeMessage = r.exceptionOrNull()?.message ?: "Download failed. Check the internet and try again." }
                }
            }.start()
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) { notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS); return }
        startWake()
    }

    private fun startWake() {
        if (!prefs.wakeWord) return
        try { WakeService.start(this) } catch (e: Exception) { state.wakeMessage = "Android did not allow it: ${e.message}"; return }
        state.wakeMessage = "Say \"Hey Jarvis\" any time. A notification shows while it listens."
        main.postDelayed({ refresh() }, 800)
    }

    override fun setConversation(on: Boolean) { prefs.conversation = on; state.conversation = on }

    /** Opens the voice install page: Google's voice data (for Bangla) if the engine is there, else the Play Store. */
    override fun installVoice() {
        val tries = listOf(
            Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(Voice.GOOGLE_TTS),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Voice.GOOGLE_TTS)),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + Voice.GOOGLE_TTS)),
        )
        val start = if (Voice.hasGoogleTts(this)) 0 else 1
        for (i in tries.drop(start)) { try { startActivity(i); return } catch (_: Exception) {} }
    }

    override fun loadMemories(done: (List<Pair<Int, String>>) -> Unit) {
        brain.background {
            val m = runCatching { brain.api.memories() }.getOrDefault(emptyList())
            main.post { done(m) }
        }
    }

    override fun deleteMemory(id: Int, done: () -> Unit) {
        brain.background { runCatching { brain.api.deleteMemory(id) }; main.post { done() } }
    }

    override fun clearChat() {
        brain.background {
            val r = runCatching { brain.api.clearHistory() }
            main.post { if (r.isSuccess) { state.messages.clear(); note("Chat cleared. Memories are kept.") } else note("Could not clear the chat: no connection.") }
        }
    }

    override fun deleteTask(id: String) {
        lifecycleScope.launch(Dispatchers.IO) { db.tasks().deleteSteps(id); db.tasks().deleteTask(id) }
    }

    override fun clearTasks() {
        lifecycleScope.launch(Dispatchers.IO) { db.tasks().clearSteps(); db.tasks().clearTasks() }
    }

    override fun clearActions() {
        lifecycleScope.launch(Dispatchers.IO) { db.actions().clear() }
    }
}
