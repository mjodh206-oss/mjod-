package dev.lasq.fawri.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.room.*
import dev.lasq.fawri.core.*
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "clips", indices = [Index("time")])
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    val time: Long,
    val source: String = "الحافظة",
    val kind: String = "TEXT",
    val pinned: Boolean = false,
    val bucket: String = "NONE",
)

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
)

// The v1 tables are retained verbatim: an in-place upgrade never replaces the user's history.
@Entity(tableName = "clipboard_cursor")
data class Cursor(@PrimaryKey val slot: Int = 1, val stamp: Long, val fingerprint: String)

data class Preview(val id: Long, val preview: String, val time: Long)

@Dao
abstract class ClipDao {
    @Insert abstract fun add(c: Clip): Long

    @Insert abstract fun addAll(rows: List<Clip>)

    @Query(
        "SELECT id, substr(body,1,200) AS preview, time FROM clips WHERE instr(lower(body),lower(:term))>0 ORDER BY time DESC, id DESC LIMIT :limit",
    )
    abstract fun page(term: String, limit: Int): List<Preview>

    @Query("SELECT body FROM clips WHERE id=:id") abstract fun text(id: Long): String?

    @Query("SELECT count(*) FROM clips") abstract fun count(): Int

    @Query("DELETE FROM clips WHERE id=:id") abstract fun delete(id: Long)

    @Query("DELETE FROM clips") abstract fun clear()

    @Query("SELECT * FROM clipboard_cursor WHERE slot=1") abstract fun cursor(): Cursor?

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun remember(c: Cursor)

    /** Advancing the cursor and adding the rows succeed together. Deletion never rewinds the cursor. */
    @Transaction
    open fun capture(e: CopyEvent): Boolean {
        val next = Cursor(stamp = e.stamp, fingerprint = e.fingerprint())
        if (cursor() == next) return false
        addAll(e.parts.map { Clip(body = it, time = e.stamp) })
        remember(next)
        return true
    }

    @Query(
        "SELECT COALESCE(SUM(length(CAST(body AS BLOB))+length(CAST(source AS BLOB))+40),0) FROM clips",
    )
    abstract fun exportSize(): Long

    @Query("SELECT * FROM clips ORDER BY time,id LIMIT :limit")
    abstract fun export(limit: Int): List<Clip>
}

@Database(entities = [Clip::class, Snippet::class, Cursor::class], version = 2, exportSchema = true)
abstract class PrivateDb : RoomDatabase() {
    abstract fun clips(): ClipDao

    companion object {
        val FROM_ONE =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS clipboard_cursor (slot INTEGER NOT NULL, stamp INTEGER NOT NULL, fingerprint TEXT NOT NULL, PRIMARY KEY(slot))",
                    )
                }
            }
    }
}

/**
 * The random SQLCipher secret is wrapped by a non-exportable Keystore AES key. No cleartext fallback.
 */
class Vault(private val ctx: Context) {
    private val name = "lasq-vault.db"
    private val alias = "lasq.wrap.v1"
    private val wrapped = AtomicFile(File(ctx.noBackupFilesDir, "lasq.wrap"))
    // All captures, deletes and snapshots use one ordered lane. A delayed callback cannot
    // enqueue an older write behind a deletion. Also serialises first-open/key creation.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var db: PrivateDb? = null
    val revision = MutableStateFlow(0L)

    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun password(): ByteArray {
        val existing = ks().getKey(alias, null) as? SecretKey
        val bytes =
            try {
                wrapped.readFully()
            } catch (_: java.io.FileNotFoundException) {
                null
            }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (bytes != null) {
            require(existing != null && bytes.size == 60)
            cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return cipher.doFinal(bytes, 12, bytes.size - 12)
        }
        check(
            !ctx.getDatabasePath(name).exists(),
        ) // Never silently replace the key for an existing vault.
        val key =
            existing
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    .apply {
                        init(
                            KeyGenParameterSpec.Builder(
                                    alias,
                                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                                )
                                .setKeySize(256)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build(),
                        )
                    }
                    .generateKey()
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val stream = wrapped.startWrite()
        try {
            stream.write(cipher.iv + cipher.doFinal(secret))
            wrapped.finishWrite(stream)
        } catch (e: Exception) {
            wrapped.failWrite(stream)
            secret.fill(0)
            throw e
        }
        return secret
    }

    private fun open(): PrivateDb =
        db
            ?: run {
                System.loadLibrary("sqlcipher")
                val pass = password()
                Room.databaseBuilder(ctx, PrivateDb::class.java, name)
                    .openHelperFactory(SupportOpenHelperFactory(pass))
                    .addMigrations(PrivateDb.FROM_ONE)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build()
                    .also {
                        db = it
                    }
            }

    suspend fun <T> read(f: (ClipDao) -> T): T = withContext(io) { f(open().clips()) }

    suspend fun <T> write(f: (ClipDao) -> T): T = read(f).also { revision.update { n -> n + 1 } }

    suspend fun capture(event: CopyEvent) {
        if (read { it.capture(event) }) revision.update { it + 1 }
    }
}
