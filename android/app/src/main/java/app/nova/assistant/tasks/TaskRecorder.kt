package app.nova.assistant.tasks

import android.content.Context
import app.nova.assistant.data.ActionLogEntity
import app.nova.assistant.data.JarvisDb
import app.nova.assistant.data.TaskEntity
import app.nova.assistant.data.TaskStatus
import app.nova.assistant.data.TaskStepEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Writes every request and its steps to the database, so the user can see what JARVIS did,
 * whether it worked, and why it failed. Writes run in order on one background coroutine queue.
 */
class TaskRecorder(context: Context, private val source: String) {

    private val db = JarvisDb.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    private fun now() = System.currentTimeMillis()

    fun start(description: String): String {
        val id = UUID.randomUUID().toString()
        val t = now()
        scope.launch { db.tasks().insert(TaskEntity(id, description.take(500), TaskStatus.RUNNING.name, source, createdAt = t, updatedAt = t)) }
        return id
    }

    fun step(id: String, text: String, ok: Boolean = true) {
        val t = now()
        scope.launch { db.tasks().insertStep(TaskStepEntity(taskId = id, text = text.take(2000), ok = ok, at = t)) }
    }

    fun status(id: String, status: TaskStatus) {
        val t = now()
        scope.launch { db.tasks().setStatus(id, status.name, t) }
    }

    fun finish(id: String, status: TaskStatus, result: String = "", error: String = "") {
        val t = now()
        scope.launch { db.tasks().finish(id, status.name, result.take(1000), error.take(1000), t) }
    }

    fun action(taskId: String, tool: String, detail: String, result: String, ok: Boolean, app: String) {
        val t = now()
        scope.launch { db.actions().insert(ActionLogEntity(taskId = taskId, tool = tool, detail = detail.take(300), result = result.take(500), ok = ok, app = app, at = t)) }
    }

    companion object {
        /** Marks tasks that were still running when the app process died. Call once at app start. */
        fun cleanUp(context: Context) {
            CoroutineScope(Dispatchers.IO).launch { JarvisDb.get(context).tasks().failInterrupted(System.currentTimeMillis()) }
        }
    }
}
