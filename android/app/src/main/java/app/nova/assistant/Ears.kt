package app.nova.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech to text. Uses Google's speech service directly when it is installed, because on some phones
 * (Honor, Huawei, some Oppo) the default speech service refuses other apps with "error 5".
 * If listening still fails, the caller opens the system voice dialog from [dialogIntent] instead.
 */
class Ears(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onReady()
        fun onLevel(level: Float)
        fun onPartial(text: String)
        fun onHeard(text: String)
        /** Silence or nothing understood. */
        fun onNothing()
        /** The speech service itself failed: use the voice dialog instead. */
        fun onBroken(code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null

    fun start(lang: String) {
        stop()
        val service = speechService()
        if (service == null && !SpeechRecognizer.isRecognitionAvailable(context)) { listener.onBroken(-1); return }
        val r = try {
            if (service != null) SpeechRecognizer.createSpeechRecognizer(context, service) else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            listener.onBroken(-2); return
        }
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listener.onReady() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { listener.onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                release()
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) listener.onNothing()
                else listener.onBroken(error)
            }
            override fun onResults(results: Bundle?) {
                release()
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (said.isEmpty()) listener.onNothing() else listener.onHeard(said)
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { listener.onPartial(it) }
            }
            override fun onEvent(type: Int, params: Bundle?) {}
        })
        try {
            r.startListening(intent(lang).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
        } catch (e: Exception) {
            release(); listener.onBroken(-3)
        }
    }

    /** Stops listening without reporting anything. */
    fun stop() {
        val r = rec ?: return
        try { r.cancel() } catch (_: Exception) {}
        release()
    }

    private fun release() {
        val old = rec ?: return
        rec = null
        main.post { try { old.destroy() } catch (_: Exception) {} }
    }

    /** Google's speech service if the phone has it, else null (then the phone's default is used). */
    private fun speechService(): ComponentName? {
        val list = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        val pick = list.firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
            ?: list.firstOrNull { it.serviceInfo.packageName == "com.google.android.tts" }
            ?: list.firstOrNull { it.serviceInfo.packageName.startsWith("com.google.android") }
            ?: return null
        return ComponentName(pick.serviceInfo.packageName, pick.serviceInfo.name)
    }

    companion object {
        fun intent(lang: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)

        /** The system voice-typing dialog: works on phones where the in-app recognizer fails. */
        fun dialogIntent(lang: String, prompt: String): Intent = intent(lang).putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
}
