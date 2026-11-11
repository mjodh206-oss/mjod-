package app.majwuda.services

/** One captured notification, kept in memory only (never persisted, never leaves the device except inside a tool you asked for). */
data class NotificationEntry(
    val id: Int,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val isClearable: Boolean,
)

/** Ring buffer shared between the notification listener and the assistant tools. */
object NotificationStore {

    private const val CAPACITY = 60

    private val lock = Any()
    private val entries = ArrayDeque<NotificationEntry>()

    fun publish(entry: NotificationEntry) {
        synchronized(lock) {
            val index = entries.indexOfFirst { it.id == entry.id && it.packageName == entry.packageName }
            if (index >= 0) entries.removeAt(index)
            entries.addFirst(entry)
            while (entries.size > CAPACITY) entries.removeLast()
        }
    }

    fun remove(id: Int, packageName: String) {
        synchronized(lock) {
            entries.removeAll { it.id == id && it.packageName == packageName }
        }
    }

    fun removeAll() {
        synchronized(lock) { entries.clear() }
    }

    fun snapshot(limit: Int = 12): List<NotificationEntry> = synchronized(lock) {
        entries.take(limit)
    }

    fun search(query: String, limit: Int = 12): List<NotificationEntry> = synchronized(lock) {
        val needle = query.trim().lowercase()
        entries.filter {
            it.title.lowercase().contains(needle) ||
                it.text.lowercase().contains(needle) ||
                it.appLabel.lowercase().contains(needle) ||
                it.packageName.lowercase().contains(needle)
        }.take(limit)
    }
}
