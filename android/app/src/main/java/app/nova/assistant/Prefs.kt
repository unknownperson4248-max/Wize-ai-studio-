package app.nova.assistant

import android.content.Context

/** Small settings store (app-private storage). */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("nova", Context.MODE_PRIVATE)

    var server: String
        get() = p.getString("server", BuildConfig.DEFAULT_SERVER) ?: BuildConfig.DEFAULT_SERVER
        set(v) = p.edit().putString("server", v).apply()

    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v).apply()

    /** Speech recognition language: "bn-BD" or "en-US". */
    var voiceLang: String
        get() = p.getString("voiceLang", "bn-BD") ?: "bn-BD"
        set(v) = p.edit().putString("voiceLang", v).apply()

    var speakReplies: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()

    /** Show the floating bubble (needs phone control turned on). */
    var bubble: Boolean
        get() = p.getBoolean("bubble", true)
        set(v) = p.edit().putBoolean("bubble", v).apply()

    var bubbleX: Int
        get() = p.getInt("bubbleX", -1)
        set(v) = p.edit().putInt("bubbleX", v).apply()

    var bubbleY: Int
        get() = p.getInt("bubbleY", -1)
        set(v) = p.edit().putInt("bubbleY", v).apply()

    /** Emergency stop / pause: while true, no phone action runs. */
    var automationPaused: Boolean
        get() = p.getBoolean("paused", false)
        set(v) = p.edit().putBoolean("paused", v).apply()

    /** Text-to-speech speed, 0.5 .. 2.0 (1.0 normal). */
    var voiceRate: Float
        get() = p.getFloat("voiceRate", 1.0f)
        set(v) = p.edit().putFloat("voiceRate", v).apply()

    /** Text-to-speech volume, 0.0 .. 1.0, relative to the media volume. */
    var voiceVolume: Float
        get() = p.getFloat("voiceVolume", 1.0f)
        set(v) = p.edit().putFloat("voiceVolume", v).apply()

    val paired get() = token.length == 64
}
