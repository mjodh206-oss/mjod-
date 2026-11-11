package app.majwuda.data

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactEntry(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<String>,
    val hasPhoto: Boolean,
) {
    val primaryNumber: String? get() = phoneNumbers.firstOrNull()
}

/** On-device contact search. Returns empty list (never throws) when permission is missing. */
class ContactRepository(private val context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun search(query: String, limit: Int = 6): List<ContactEntry> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val results = ArrayList<ContactEntry>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val cursor = resolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_ID,
                ),
                selection,
                arrayOf("%$trimmed%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT ${limit * 4}",
            ) ?: return@withContext emptyList()
            val byContact = LinkedHashMap<Long, MutableList<String>>()
            val names = HashMap<Long, String>()
            val photo = HashMap<Long, Boolean>()
            cursor.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    names[id] = c.getString(nameIdx) ?: ""
                    val number = (c.getString(numIdx) ?: "").trim()
                    if (number.isNotEmpty()) {
                        byContact.getOrPut(id) { ArrayList() }.add(number)
                    }
                    if (photoIdx >= 0) photo[id] = c.getInt(photoIdx) > 0
                }
            }
            for ((id, numbers) in byContact) {
                results += ContactEntry(
                    id = id,
                    displayName = names[id].orEmpty(),
                    phoneNumbers = numbers.distinct(),
                    hasPhoto = photo[id] == true,
                )
                if (results.size >= limit) break
            }
        } catch (_: SecurityException) {
            // Permission not granted.
        } catch (_: Exception) {
            // Provider unavailable.
        }
        results
    }

    /** Exact/fuzzy best match above [minScore] across the device contacts (bounded scan). */
    suspend fun bestMatch(query: String, minScore: Double = 0.55): ContactEntry? = withContext(Dispatchers.IO) {
        val candidates = ArrayList<ContactEntry>()
        try {
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            ) ?: return@withContext null
            val byContact = LinkedHashMap<Long, MutableList<String>>()
            val names = HashMap<Long, String>()
            var scanned = 0
            cursor.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext() && scanned < 4000) {
                    scanned++
                    val id = c.getLong(idIdx)
                    names[id] = c.getString(nameIdx) ?: ""
                    val number = (c.getString(numIdx) ?: "").trim()
                    if (number.isNotEmpty()) byContact.getOrPut(id) { ArrayList() }.add(number)
                }
            }
            for ((id, numbers) in byContact) {
                candidates += ContactEntry(id, names[id].orEmpty(), numbers.distinct(), false)
            }
        } catch (_: SecurityException) {
            return@withContext null
        }
        candidates.map { it to app.majwuda.core.ArabicText.matchScore(query, it.displayName) }
            .filter { it.second >= minScore }
            .maxByOrNull { it.second }
            ?.first
    }
}
