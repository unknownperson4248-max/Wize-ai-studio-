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

    val paired get() = token.length == 64
}
