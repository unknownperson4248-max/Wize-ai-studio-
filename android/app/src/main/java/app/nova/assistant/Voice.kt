package app.nova.assistant

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Spoken replies with Android text-to-speech. Picks Bangla or English from the text itself. */
class Voice(context: Context, private val prefs: Prefs, private val onSpeaking: (Boolean) -> Unit) {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { ready = it == TextToSpeech.SUCCESS }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { onSpeaking(true) } }
            override fun onDone(id: String?) { main.post { onSpeaking(false) } }
            @Deprecated("old API") override fun onError(id: String?) { main.post { onSpeaking(false) } }
        })
    }

    fun say(text: String) {
        if (!ready) return
        val bangla = text.any { it in 'ঀ'..'৿' }
        val loc = if (bangla) Locale("bn", "BD") else Locale.US
        if (tts.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) tts.language = loc
        tts.setSpeechRate(prefs.voiceRate.coerceIn(0.5f, 2f))
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.voiceVolume.coerceIn(0f, 1f)) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis-" + System.nanoTime())
    }

    fun stop() { tts.stop() }

    fun shutdown() { tts.shutdown() }
}
