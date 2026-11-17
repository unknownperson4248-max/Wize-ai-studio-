package app.nova.assistant.wake

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * The offline speech model used only to hear "Hey Jarvis" (Vosk small English model, Apache-2.0, about 40 MB).
 * Downloaded once on the phone when the user turns hands-free on; nothing it hears leaves the phone.
 */
object WakeModel {
    private const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MIN_ZIP_BYTES = 30_000_000L

    fun dir(context: Context) = File(context.filesDir, "wake-model")

    fun ready(context: Context) = File(dir(context), "am/final.mdl").isFile && File(dir(context), "graph/HCLr.fst").isFile

    /** Downloads and unpacks the model. Blocking: call from a background thread. progress: 0..1. */
    fun download(context: Context, progress: (Float) -> Unit) {
        val zip = File(context.cacheDir, "wake-model.zip")
        val conn = URL(URL_ZIP).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20000; conn.readTimeout = 30000
            if (conn.responseCode != 200) throw IllegalStateException("Download failed (${conn.responseCode}).")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - last > 512 * 1024) { last = done; progress((done.toFloat() / total) * 0.9f) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (zip.length() < MIN_ZIP_BYTES) { zip.delete(); throw IllegalStateException("The download was incomplete. Try again.") }

        val target = dir(context)
        val tmp = File(context.filesDir, "wake-model.tmp")
        tmp.deleteRecursively(); tmp.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                // strip the top folder ("vosk-model-small-en-us-0.15/...") and refuse paths that escape the folder
                val rel = e.name.substringAfter('/', "")
                if (rel.isEmpty() || rel.contains("..")) continue
                val f = File(tmp, rel)
                if (!f.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) continue
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); FileOutputStream(f).use { z.copyTo(it) } }
            }
        }
        zip.delete()
        target.deleteRecursively()
        if (!tmp.renameTo(target)) throw IllegalStateException("Could not save the model.")
        if (!ready(context)) throw IllegalStateException("The model files are incomplete. Try again.")
        progress(1f)
    }
}
