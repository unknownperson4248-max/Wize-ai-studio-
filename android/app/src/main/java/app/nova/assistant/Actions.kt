package app.nova.assistant

import android.Manifest
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import org.json.JSONObject

/**
 * Runs the phone actions the assistant asks for and returns a short result text for the AI.
 * Calls and SMS only open the dialer / messages app with everything filled in: the user presses call or send.
 */
class Actions(private val ctx: Context) {

    class NeedsPermission(val permission: String) : Exception()

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(a.optString("app"))
            "open_url" -> openUrl(a.optString("url"))
            "web_search" -> start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(a.optString("query")))), "Searched the web for: ${a.optString("query")}")
            "call" -> call(a.optString("to"))
            "send_sms" -> sms(a.optString("to"), a.optString("message"))
            "set_alarm" -> setAlarm(a.optInt("hour", -1), a.optInt("minute", 0), a.optString("label"))
            "set_timer" -> setTimer(a.optInt("seconds", 0), a.optString("label"))
            "torch" -> torch(a.optBoolean("on", true))
            "set_volume" -> volume(a.optInt("percent", 50))
            "battery_status" -> battery()
            "open_settings" -> settings(a.optString("page"))
            "navigate" -> start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(a.optString("destination")))),
                "Started directions to ${a.optString("destination")}",
                fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.optString("destination")))))
            "read_screen" -> screen { it.read() }
            "tap" -> screen { it.tap(a.optString("target")) }
            "type_text" -> screen { it.type(a.optString("text"), a.optString("field")) }
            "scroll" -> screen { it.scroll(a.optString("direction", "down")) }
            "press" -> screen { it.press(a.optString("button")) }
            "whatsapp_message" -> whatsapp(a.optString("to"), a.optString("message"))
            else -> "Failed: this phone does not know the action $name yet."
        }
    } catch (e: NeedsPermission) {
        throw e
    } catch (e: Exception) {
        "Failed: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Human-readable description for the confirmation dialog. */
    fun describe(name: String, a: JSONObject): String = when (name) {
        "call" -> "Call ${a.optString("to")}?"
        "send_sms" -> "SMS to ${a.optString("to")}:\n\n\"${a.optString("message")}\""
        "whatsapp_message" -> "WhatsApp to ${a.optString("to")}:\n\n\"${a.optString("message")}\""
        "tap" -> "Tap \"${a.optString("target")}\" on the screen?"
        else -> "$name ${a}"
    }

    /** Taps that send, pay, delete, buy or post always need a Yes from the user, whatever the AI says. */
    fun risky(name: String, a: JSONObject) = name == "tap" && Safety.isRiskyTap(a.optString("target"))

    /** Status text while an action runs. */
    fun label(name: String) = when (name) {
        "read_screen" -> "Looking at the screen…"
        "tap" -> "Tapping…"
        "type_text" -> "Typing…"
        "scroll" -> "Scrolling…"
        "whatsapp_message" -> "Sending on WhatsApp…"
        else -> "Working…"
    }

    /** Short version of a result for the chat. */
    fun short(name: String, result: String) = if (name == "read_screen" && !result.startsWith("Failed")) "Looked at the screen" else result.take(140)

    private fun screen(f: (NovaAccessibility) -> String): String =
        NovaAccessibility.instance?.let(f) ?: "Failed: phone control is off. The user can turn it on in NOVA settings, Phone control."

    private fun installed(pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }

    /** Opens the WhatsApp chat with the message written in, then presses send if phone control is on. The user already said Yes. */
    private fun whatsapp(to: String, message: String): String {
        if (message.isBlank()) return "Failed: no message given."
        val (number, who) = resolveNumber(to)
        val digits = Safety.whatsappNumber(number)
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { installed(it) } ?: return "Failed: WhatsApp is not installed."
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=" + Uri.encode(message))).setPackage(pkg)
        val opened = start(i, "ok")
        if (opened != "ok") return opened
        val svc = NovaAccessibility.instance ?: return "The message to $who is written in WhatsApp. The user presses send (phone control is off)."
        return if (svc.pressSend(pkg)) "Sent on WhatsApp to $who." else "The message to $who is written in WhatsApp, but I could not press send. The user presses send."
    }


    private fun start(i: Intent, ok: String, fallback: Intent? = null): String {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i); ok
        } catch (e: ActivityNotFoundException) {
            if (fallback == null) "Failed: no app on this phone can do that."
            else { fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(fallback); ok }
        }
    }

    private fun openApp(query: String): String {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return "Failed: no app name given."
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val hit = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
            ?: apps.firstOrNull { q.contains(it.first.lowercase()) && it.first.length > 2 }
            ?: return "Failed: no app called \"$query\" is installed."
        val launch = pm.getLaunchIntentForPackage(hit.second) ?: return "Failed: ${hit.first} cannot be opened."
        return start(launch, "Opened ${hit.first}")
    }

    private fun openUrl(raw: String): String {
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opened $url")
    }

    /** A number stays a number; a name is looked up in the contacts (needs the contacts permission). */
    private fun resolveNumber(to: String): Pair<String, String> {
        val digits = to.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 3 && to.none { it.isLetter() }) return digits to digits
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            throw NeedsPermission(Manifest.permission.READ_CONTACTS)
        val cr = ctx.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        cr.query(uri, cols, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$to%"), null)?.use { c ->
            if (c.moveToFirst()) return c.getString(1) to c.getString(0)
        }
        throw IllegalStateException("no contact called \"$to\"")
    }

    private fun call(to: String): String {
        val (number, who) = resolveNumber(to)
        return start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), "Dialer opened for $who ($number). The user presses call.")
    }

    private fun sms(to: String, message: String): String {
        val (number, who) = resolveNumber(to)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", message)
        return start(i, "Message to $who is written in the SMS app. The user presses send.")
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        if (hour !in 0..23 || minute !in 0..59) return "Failed: invalid time."
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return start(i, "Alarm set for %02d:%02d".format(hour, minute))
    }

    private fun setTimer(seconds: Int, label: String): String {
        if (seconds <= 0) return "Failed: invalid timer length."
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return start(i, "Timer started for $seconds seconds")
    }

    private fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "Failed: this phone has no flashlight."
        cm.setTorchMode(id, on)
        return if (on) "Flashlight is on" else "Flashlight is off"
    }

    private fun volume(percent: Int): String {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val v = (percent.coerceIn(0, 100) * max + 50) / 100
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
        return "Media volume set to ${percent.coerceIn(0, 100)}%"
    }

    private fun battery(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery $level%, " + if (bm.isCharging) "charging" else "not charging"
    }

    private fun settings(page: String): String {
        val action = when (page.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return start(Intent(action), "Opened ${page.ifBlank { "main" }} settings")
    }
}
