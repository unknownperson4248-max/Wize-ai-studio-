package app.nova.assistant

import app.nova.assistant.data.TaskStatus

/** Pure rules with no Android code, so they are covered by unit tests (app/src/test). */
object Safety {

    private val RISKY = Regex(
        "(?i)send|pay|buy|purchase|order|checkout|transfer|delete|remove|uninstall|install|confirm|post|submit|publish|call|" +
            "পাঠা|ডিলিট|মুছ|কিন|পেমেন্ট|bkash|nagad"
    )

    /** A tap on this label sends, pays, buys, deletes or posts something: the user must say Yes first. */
    fun isRiskyTap(target: String) = RISKY.containsMatchIn(target)

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
}
