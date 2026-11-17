package app.nova.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Task statuses (spec section 11). Stored as their name. */
enum class TaskStatus { PENDING, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED }

/** One request to JARVIS and everything done for it. */
@Entity(tableName = "tasks", indices = [Index("createdAt")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val description: String,
    val status: String,
    val source: String,          // "app" or "bubble"
    val result: String = "",
    val error: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** One step of a task: a reply, an action, its result, an approval. */
@Entity(tableName = "task_steps", indices = [Index("taskId")])
data class TaskStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val text: String,
    val ok: Boolean = true,
    val at: Long,
)

/** Audit log of every phone action JARVIS ran (spec sections 2 and 13). */
@Entity(tableName = "action_log", indices = [Index("at")])
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val tool: String,
    val detail: String,
    val result: String,
    val ok: Boolean,
    val app: String,             // foreground app when it ran, if known
    val at: Long,
)

/** Inventory product (spec section 6A). The inventory screens arrive in phase 5; the dashboard already reads low stock. */
@Entity(tableName = "products", indices = [Index(value = ["code"], unique = true), Index("name")])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val category: String = "",
    val description: String = "",
    val unit: String = "pcs",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val quantity: Double = 0.0,
    val minStock: Double = 0.0,
    val supplier: String = "",
    val updatedAt: Long = 0,
)

data class StatusCount(val status: String, val n: Int)

@Dao
interface TaskDao {
    @Insert suspend fun insert(t: TaskEntity)
    @Insert suspend fun insertStep(s: TaskStepEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :at WHERE id = :id")
    suspend fun setStatus(id: String, status: String, at: Long)

    @Query("UPDATE tasks SET status = :status, result = :result, error = :error, updatedAt = :at WHERE id = :id")
    suspend fun finish(id: String, status: String, result: String, error: String, at: Long)

    @Query("SELECT * FROM tasks WHERE (:status = '' OR status = :status) AND (:q = '' OR description LIKE '%' || :q || '%' OR result LIKE '%' || :q || '%') ORDER BY createdAt DESC LIMIT 300")
    fun search(q: String, status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun task(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM task_steps WHERE taskId = :id ORDER BY id")
    fun steps(id: String): Flow<List<TaskStepEntity>>

    @Query("SELECT status, COUNT(*) AS n FROM tasks WHERE createdAt >= :since GROUP BY status")
    fun countsSince(since: Long): Flow<List<StatusCount>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC LIMIT :n")
    fun recent(n: Int): Flow<List<TaskEntity>>

    @Query("DELETE FROM task_steps WHERE taskId = :id")
    suspend fun deleteSteps(id: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM task_steps")
    suspend fun clearSteps()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    /** Tasks left running when the app was killed. */
    @Query("UPDATE tasks SET status = 'FAILED', error = 'Stopped: the app was closed while it ran.', updatedAt = :at WHERE status IN ('RUNNING', 'WAITING_APPROVAL', 'PENDING')")
    suspend fun failInterrupted(at: Long)
}

@Dao
interface ActionLogDao {
    @Insert suspend fun insert(a: ActionLogEntity)

    @Query("SELECT * FROM action_log ORDER BY at DESC LIMIT :n")
    fun recent(n: Int): Flow<List<ActionLogEntity>>

    @Query("DELETE FROM action_log")
    suspend fun clear()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE quantity <= minStock AND minStock > 0 ORDER BY name")
    fun lowStock(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products")
    fun count(): Flow<Int>
}

@Database(entities = [TaskEntity::class, TaskStepEntity::class, ActionLogEntity::class, ProductEntity::class], version = 1, exportSchema = false)
abstract class JarvisDb : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun actions(): ActionLogDao
    abstract fun products(): ProductDao

    companion object {
        @Volatile private var one: JarvisDb? = null

        fun get(context: Context): JarvisDb = one ?: synchronized(this) {
            one ?: Room.databaseBuilder(context.applicationContext, JarvisDb::class.java, "jarvis.db").build().also { one = it }
        }
    }
}
