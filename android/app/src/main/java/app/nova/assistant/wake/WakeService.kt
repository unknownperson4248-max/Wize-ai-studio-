package app.nova.assistant.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.nova.assistant.Brain
import app.nova.assistant.Ears
import app.nova.assistant.ListenActivity
import app.nova.assistant.MainActivity
import app.nova.assistant.MicLock
import app.nova.assistant.Mood
import app.nova.assistant.NovaAccessibility
import app.nova.assistant.Prefs
import app.nova.assistant.R
import app.nova.assistant.Safety
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Hands-free "Hey Jarvis". A foreground service (with its notification, so the user always knows) that listens
 * on the phone, offline, for the words "Hey Jarvis" only. When heard, it hands over to normal speech recognition
 * for the command, then goes back to listening. It lets go of the mic whenever JARVIS listens or speaks.
 */
class WakeService : Service(), Ears.Listener, Brain.Ui {

    companion object {
        private const val ACTION_STOP = "app.nova.assistant.wake.STOP"
        private const val CHANNEL = "wake"
        private const val NOTE_ID = 7
        private const val GRAMMAR = "[\"hey jarvis\", \"jarvis\", \"okay jarvis\", \"[unk]\"]"

        @Volatile
        var running = false
            private set

        fun start(context: Context) { context.startForegroundService(Intent(context, WakeService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, WakeService::class.java)) }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var ears: Ears
    private var brain: Brain? = null          // only used when phone control is off
    private var model: Model? = null
    private var vosk: SpeechService? = null
    private var rec: Recognizer? = null
    private var commanding = false             // listening for a command in this service
    private val onMic: (Boolean) -> Unit = { busy -> if (busy) pauseWake() else main.postDelayed({ resumeWake() }, 700) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        ears = Ears(this, this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hey Jarvis", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while JARVIS listens for \"Hey Jarvis\""
        })
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTE_ID, buildNotice("Listening for \"Hey Jarvis\""), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTE_ID, buildNotice("Listening for \"Hey Jarvis\""))
        } catch (e: Exception) {
            // Android refuses a microphone service started from the background: the user turns it on again in the app
            stopSelf(); return
        }
        running = true
        MicLock.listen(onMic)
        Thread {
            val m = try { Model(WakeModel.dir(this).absolutePath) } catch (e: Throwable) { null }
            main.post {
                if (m == null) { update("The \"Hey Jarvis\" model is missing. Turn hands-free on again in JARVIS."); stopSelf() }
                else { model = m; resumeWake() }
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { prefs.wakeWord = false; stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        MicLock.unlisten(onMic)
        pauseWake()
        ears.stop()
        brain?.close()
        model?.close()
        super.onDestroy()
    }

    /* ---------------- wake word ---------------- */

    private val wakeListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) { if (Safety.isWakeWord(hypothesis.orEmpty())) heard() }
        override fun onResult(hypothesis: String?) { if (Safety.isWakeWord(hypothesis.orEmpty())) heard() }
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) { pauseWake(); main.postDelayed({ resumeWake() }, 3000) }
        override fun onTimeout() {}
    }

    private fun resumeWake() {
        val m = model ?: return
        if (vosk != null || commanding || MicLock.busy || !running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { update("Microphone permission is off."); return }
        try {
            val r = Recognizer(m, 16000f, GRAMMAR)
            rec = r
            vosk = SpeechService(r, 16000f).also { it.startListening(wakeListener) }
            update("Listening for \"Hey Jarvis\"")
        } catch (e: Exception) {
            vosk = null
            main.postDelayed({ resumeWake() }, 5000)
        }
    }

    private fun pauseWake() {
        val v = vosk
        vosk = null
        if (v != null) try { v.stop(); v.shutdown() } catch (_: Exception) {}
        rec?.let { r -> rec = null; try { r.close() } catch (_: Exception) {} }
    }

    /** "Hey Jarvis" was heard: chime, then take the command. */
    private fun heard() {
        if (vosk == null) return
        pauseWake()
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 70).startTone(ToneGenerator.TONE_PROP_ACK, 150) } catch (_: Exception) {}
        if (NovaAccessibility.instance != null) {
            // phone control is on: the listening card and the bubble handle the command and the reply
            main.postDelayed({
                startActivity(Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }, 200)
            main.postDelayed({ if (!MicLock.busy) resumeWake() }, 5000) // in case the card did not open
        } else {
            commanding = true
            update("Listening…")
            main.postDelayed({ ears.start(prefs.voiceLang) }, 250)
        }
    }

    /* ---------------- command without phone control ---------------- */

    private fun brain(): Brain = brain ?: Brain(this, this, "wake").also { brain = it }

    override fun onReady() { update("Listening… speak now") }
    override fun onLevel(level: Float) {}
    override fun onPartial(text: String) { update(text) }
    override fun onHeard(text: String) { commanding = false; update("You: $text"); brain().ask(text, true) }
    override fun onNothing() { commanding = false; update("Listening for \"Hey Jarvis\"") }
    override fun onBroken(code: Int) { commanding = false; update("Speech recognition failed ($code). Open JARVIS to talk.") }

    override fun mood(m: Mood) {}
    override fun status(text: String) { if (text.isNotBlank() && text != "…") update(text) }
    override fun reply(text: String) { update(text) }
    override fun note(text: String) { if (!text.startsWith("⚡")) update(text) }
    override fun confirm(question: String, answer: (Boolean) -> Unit) {
        update("Needs your approval: open JARVIS or turn on phone control. ($question)")
        answer(false)
    }
    override fun permission(permission: String, answer: (Boolean) -> Unit) { answer(false); update("Open JARVIS to allow a permission, then ask again.") }
    override fun listenAgain() {
        if (!prefs.conversation || commanding) return
        pauseWake(); commanding = true
        main.postDelayed({ ears.start(prefs.voiceLang) }, 250)
    }

    /* ---------------- notification ---------------- */

    private fun buildNotice(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("JARVIS hands-free")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null as Icon?, "Turn off", stop).build())
            .build()
    }

    private fun update(text: String) {
        if (!running) return
        getSystemService(NotificationManager::class.java).notify(NOTE_ID, buildNotice(text.take(300)))
    }
}
