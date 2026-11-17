package app.nova.assistant

import android.app.Application
import app.nova.assistant.tasks.TaskRecorder

/** Runs once when the app process starts, before any screen or service. */
class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Tasks still marked running belong to a process that died: record them as failed.
        TaskRecorder.cleanUp(this)
    }
}
