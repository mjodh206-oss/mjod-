package app.majwuda.data

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsEntry(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val isInbox: Boolean,
)

/** Read-only SMS access for "which unread message did X send" style questions. */
class SmsReader(private val context: Context) {

    suspend fun recentUnread(limit: Int = 8): List<SmsEntry> = withContext(Dispatchers.IO) {
        query(
            selection = "${Telephony.Sms.Inbox.READ} = 0",
            limit = limit,
            inbox = true,
        )
    }

    suspend fun recentFrom(addressOrName: String, limit: Int = 5): List<SmsEntry> = withContext(Dispatchers.IO) {
        val digits = addressOrName.filter { it.isDigit() || it == '+' }
        if (digits.length >= 4) {
            // try exact-ish match on digits
            query(selection = "${Telephony.Sms.ADDRESS} LIKE ?", args = arrayOf("%$digits%"), limit = limit, inbox = true)
        } else {
            recentUnread(limit)
        }
    }

    suspend fun recent(limit: Int = 10): List<SmsEntry> = withContext(Dispatchers.IO) {
        query(selection = null, limit = limit, inbox = true)
    }

    private fun query(selection: String?, args: Array<String>? = null, limit: Int, inbox: Boolean): List<SmsEntry> {
        val result = ArrayList<SmsEntry>()
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                selection,
                args,
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            ) ?: return emptyList()
            cursor.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    result += SmsEntry(
                        id = c.getLong(idIdx),
                        address = c.getString(addrIdx) ?: "",
                        body = c.getString(bodyIdx) ?: "",
                        date = c.getLong(dateIdx),
                        isInbox = inbox,
                    )
                }
            }
        } catch (_: SecurityException) {
            // READ_SMS missing
        } catch (_: Exception) {
            // provider unavailable
        }
        return result
    }
}
