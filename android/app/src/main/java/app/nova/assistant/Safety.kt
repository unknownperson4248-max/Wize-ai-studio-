package app.nova.assistant

import app.nova.assistant.data.TaskStatus

/** Pure rules with no Android code, so they are covered by unit tests (app/src/test). */
object Safety {

    private val RISKY = Regex(
        "(?i)send|pay|buy|purchase|order|checkout|transfer|delete|remove|uninstall|install|confirm|post|submit|publish|call|" +
            "পাঠা|ডিলিট|মুছ|কিন|পেমেন্ট|bkash|nagad"
    )

    /** Apps where JARVIS never taps, types or scrolls by itself: system settings, permission and install screens. */
    private val PROTECTED_APPS = setOf(
        "com.android.settings",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.hihonor.systemmanager", "com.huawei.systemmanager",
        "com.miui.securitycenter", "com.coloros.safecenter", "com.samsung.android.lool",
    )

    /** Popular games that do not mark themselves as games in Android. */
    val KNOWN_GAMES = setOf(
        "com.tencent.ig", "com.pubg.imobile", "com.pubg.krmobile", "com.vng.pubgmobile", "com.tencent.tmgp.pubgmhd",
        "com.dts.freefireth", "com.dts.freefiremax", "com.activision.callofduty.shooter", "com.mobile.legends",
        "com.supercell.clashofclans", "com.supercell.clashroyale", "com.miniclip.eightballpool", "com.kiloo.subwaysurf",
    )

    /** A tap on this label sends, pays, buys, deletes or posts something: the user must say Yes first. */
    fun isRiskyTap(target: String) = RISKY.containsMatchIn(target)

    /** JARVIS must not grant itself permissions, change system settings, or click around in its own app. */
    fun isProtectedApp(pkg: String, ownPackage: String) = pkg == ownPackage || pkg in PROTECTED_APPS

    /** Phone number for a WhatsApp link: digits only, with the Bangladesh country code added to 01XXXXXXXXX numbers. */
    fun whatsappNumber(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("01")) d = "88$d"
        return d
    }

    /** How a finished task is recorded. */
    fun finalStatus(cancelled: Boolean, error: String?, lastActionFailed: Boolean): TaskStatus = when {
        cancelled -> TaskStatus.CANCELLED
        !error.isNullOrBlank() -> TaskStatus.FAILED
        lastActionFailed -> TaskStatus.FAILED
        else -> TaskStatus.COMPLETED
    }

    /** Short, single-line form of tool arguments for the action log (no long message bodies). */
    fun summarizeArgs(json: String): String = json.replace(Regex("\\s+"), " ").take(160)

    /** True when "Hey Jarvis" (or a close mishearing) is in the wake-word recognizer's text. */
    fun isWakeWord(text: String) = Regex("\\b(jarvis|jervis)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

/**
 * What JARVIS says out loud while it works, so it feels like a person doing the job, not a silent machine.
 * style: "bn" = Bangla script, "banglish" = Bangla in English letters (when the phone has no Bangla voice), "en" = English.
 */
object Narration {

    fun line(tool: String, app: String, style: String): String? {
        val a = app.trim()
        return when (style) {
            "bn" -> when (tool) {
                "open_app" -> "ঠিক আছে, $a খুলছি।"
                "whatsapp_message" -> "হোয়াটসঅ্যাপে পাঠাচ্ছি।"
                "web_search" -> "খুঁজে দেখছি।"
                "open_url" -> "খুলছি।"
                "navigate" -> "রাস্তা দেখাচ্ছি।"
                "set_alarm" -> "অ্যালার্ম দিচ্ছি।"
                "set_timer" -> "টাইমার চালু করছি।"
                "read_screen" -> "একটু দেখে নিই।"
                else -> null
            }
            "banglish" -> when (tool) {
                "open_app" -> "Thik ache, $a khulchi."
                "whatsapp_message" -> "WhatsApp e pathacchi."
                "web_search" -> "Khuje dekhchi."
                "open_url" -> "Khulchi."
                "navigate" -> "Rasta dekhacchi."
                "set_alarm" -> "Alarm dicchi."
                "set_timer" -> "Timer chalu korchi."
                "read_screen" -> "Ektu dekhe nei."
                else -> null
            }
            else -> when (tool) {
                "open_app" -> "Sure, opening $a."
                "whatsapp_message" -> "Sending it on WhatsApp."
                "web_search" -> "Let me look that up."
                "open_url" -> "Opening it."
                "navigate" -> "Getting directions."
                "set_alarm" -> "Setting the alarm."
                "set_timer" -> "Starting the timer."
                "read_screen" -> "Let me have a look."
                else -> null
            }
        }
    }
}
