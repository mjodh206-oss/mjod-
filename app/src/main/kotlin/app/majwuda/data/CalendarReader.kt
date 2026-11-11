package app.majwuda.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

data class CalendarEventEntry(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?,
    val description: String?,
    val allDay: Boolean,
    val calendarId: Long,
)

/** Reads and writes the device calendar through CalendarContract. */
class CalendarRepository(private val context: Context) {

    suspend fun upcomingEvents(fromMillis: Long = System.currentTimeMillis(), daysAhead: Int = 30, limit: Int = 20): List<CalendarEventEntry> =
        withContext(Dispatchers.IO) {
            query(fromMillis = fromMillis, toMillis = fromMillis + daysAhead * 86_400_000L, limit = limit)
        }

    suspend fun eventsOn(date: java.time.LocalDate): List<CalendarEventEntry> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        query(fromMillis = start, toMillis = end, limit = 40)
    }

    suspend fun searchEvents(query: String, limit: Int = 15): List<CalendarEventEntry> = withContext(Dispatchers.IO) {
        val results = ArrayList<CalendarEventEntry>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI
            val builder = uri.buildUpon()
            ContentUris.appendId(builder, System.currentTimeMillis() - 90L * 86_400_000L)
            ContentUris.appendId(builder, System.currentTimeMillis() + 365L * 86_400_000L)
            val cursor = context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_ID,
                ),
                "${CalendarContract.Instances.TITLE} LIKE ?",
                arrayOf("%$query%"),
                "${CalendarContract.Instances.BEGIN} ASC LIMIT $limit",
            ) ?: return@withContext emptyList()
            cursor.use { c ->
                while (c.moveToNext()) {
                    results += eventFromCursor(c)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        results
    }

    private fun query(fromMillis: Long, toMillis: Long, limit: Int): List<CalendarEventEntry> {
        val results = ArrayList<CalendarEventEntry>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI
            val builder = uri.buildUpon()
            ContentUris.appendId(builder, fromMillis)
            ContentUris.appendId(builder, toMillis)
            val cursor = context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.CALENDAR_ID,
                ),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC LIMIT $limit",
            ) ?: return emptyList()
            cursor.use { c ->
                while (c.moveToNext()) {
                    results += eventFromCursor(c)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return results
    }

    private fun eventFromCursor(c: android.database.Cursor): CalendarEventEntry = CalendarEventEntry(
        id = c.getLong(0),
        title = c.getString(1) ?: "(بدون عنوان)",
        startMillis = c.getLong(2),
        endMillis = c.getLong(3),
        location = c.getString(4),
        description = c.getString(5),
        allDay = c.getInt(6) != 0,
        calendarId = c.getLong(7),
    )

    /** Writes to the first writable calendar; returns the event id or null when not possible. */
    suspend fun insertEvent(title: String, startMillis: Long, endMillis: Long, description: String?, location: String?): Long? =
        withContext(Dispatchers.IO) {
            val calendarId = firstWritableCalendarId() ?: return@withContext null
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                if (description != null) put(CalendarContract.Events.DESCRIPTION, description)
                if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
            }
            return@withContext try {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }

    private fun firstWritableCalendarId(): Long? {
        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.ALLOWED_REMINDERS),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
                null,
                null,
            ) ?: return null
            cursor.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatTime(millis: Long): String =
        java.text.SimpleDateFormat("EEEE d MMMM، الساعة h:mm a", Locale("ar")).format(java.util.Date(millis))
}
