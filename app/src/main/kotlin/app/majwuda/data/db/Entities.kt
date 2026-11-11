package app.majwuda.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val assistantName: String = "مجودة",
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    /** user | assistant */
    val role: String,
    /** Raw JSON array of content blocks, exactly as sent to/received from the API. */
    val contentJson: String,
    /** Plain text used for list previews and search. */
    val text: String,
    val toolUses: String,
    val toolResults: String,
    val ts: Long,
    val interrupted: Boolean = false,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val tags: String = "",
    val createdAt: Long,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val tags: String = "",
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String? = null,
    val dueAt: Long,
    val recurrence: String = Recurrence.NONE.name,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val snoozedUntil: Long? = null,
    /** Stable request code for AlarmManager so the same reminder can be re-scheduled correctly. */
    val requestCode: Int,
    val createdAt: Long,
)

enum class ScheduledAction { REMINDER, ALARM, TIMER }
