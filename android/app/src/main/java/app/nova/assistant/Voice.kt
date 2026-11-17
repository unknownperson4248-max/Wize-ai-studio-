package app.nova.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * JARVIS's spoken voice (Android text-to-speech). Uses Google's voice engine when installed, because many phones'
 * own engines (Honor, Huawei) have no Bangla voice and stay silent on Bangla text. Picks Bangla or English per sentence,
 * queues sentences, and holds the [MicLock] while speaking.
 */
class Voice(context: Context, private val prefs: Prefs, private val onSpeaking: (Boolean) -> Unit) {

    companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
        fun hasGoogleTts(context: Context) = try { context.packageManager.getPackageInfo(GOOGLE_TTS, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private val main = Handler(Looper.getMainLooper())
    private val lockTag = "tts-" + System.identityHashCode(this)
    private val engine = if (hasGoogleTts(context)) GOOGLE_TTS else null
    private var ready = false
    private var failed = false
    private var bangla = false
    private var pending: String? = null
    private var speaking = 0
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, { status -> main.post { onInit(status) } }, engine)

    /** Short description for Settings, e.g. "Google voice · Bangla ready". */
    var info = "Starting the voice…"
        private set
    val banglaMissing get() = ready && !bangla

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { if (speaking++ == 0) { MicLock.acquire(lockTag); onSpeaking(true) } } }
            override fun onDone(id: String?) { main.post { finished() } }
            @Deprecated("old API") override fun onError(id: String?) { main.post { finished() } }
            override fun onStop(id: String?, interrupted: Boolean) { main.post { finished() } }
        })
    }

    private fun finished() {
        if (speaking == 0) return
        speaking--
        if (speaking == 0) { MicLock.release(lockTag); onSpeaking(false) }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            info = "No voice engine works on this phone. Install \"Speech Services by Google\"."
            return
        }
        ready = true
        bangla = tts.isLanguageAvailable(Locale("bn", "BD")) >= TextToSpeech.LANG_AVAILABLE ||
            tts.isLanguageAvailable(Locale("bn")) >= TextToSpeech.LANG_AVAILABLE
        prefs.banglaVoice = bangla
        val name = if (engine == GOOGLE_TTS) "Google voice" else (tts.defaultEngine ?: "Phone voice")
        info = name + if (bangla) " · Bangla ready" else " · no Bangla voice (Bangla replies use English letters)"
        pending?.let { pending = null; say(it) }
    }

    fun say(text: String) {
        if (failed || text.isBlank()) return
        if (!ready) { pending = text; return }
        val isBangla = text.any { it in 'ঀ'..'৿' }
        val loc = if (isBangla && bangla) Locale("bn", "BD") else Locale.US
        tts.language = loc
        tts.setSpeechRate(prefs.voiceRate.coerceIn(0.5f, 2f))
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.voiceVolume.coerceIn(0f, 1f)) }
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, "jarvis-" + System.nanoTime())
    }

    fun stop() {
        pending = null
        tts.stop()
        if (speaking > 0) { speaking = 0; MicLock.release(lockTag); onSpeaking(false) }
    }

    fun shutdown() { stop(); tts.shutdown() }
}
