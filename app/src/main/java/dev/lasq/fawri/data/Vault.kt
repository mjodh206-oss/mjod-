package dev.lasq.fawri.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.room.*
import dev.lasq.fawri.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName="clips", indices=[Index("time")])
data class Clip(@PrimaryKey(autoGenerate=true) val id: Long = 0, val body: String, val time: Long,
    val source: String, val kind: String, val pinned: Boolean = false, val bucket: String = Group.NONE.name)
@Entity(tableName="snippets")
data class Snippet(@PrimaryKey(autoGenerate=true) val id: Long = 0, val title: String, val body: String)
@Dao interface ClipDao {
    @Insert fun add(c: Clip): Long
    @Insert fun addAll(c: List<Clip>)
    @Query("SELECT * FROM clips WHERE time > :after AND (instr(lower(body),lower(:query))>0 OR instr(lower(source),lower(:query))>0) AND (:groupName='' OR bucket=:groupName) AND (:pins=0 OR pinned=1) ORDER BY pinned DESC, time DESC, id DESC LIMIT :limit")
    fun search(after: Long, query: String, groupName: String, pins: Boolean, limit: Int): List<Clip>
    @Query("SELECT * FROM clips WHERE time > :after ORDER BY time DESC,id DESC LIMIT 3") fun recent(after: Long): List<Clip>
    @Query("SELECT count(*) FROM clips WHERE time > :after") fun count(after: Long): Int
    @Query("UPDATE clips SET pinned=NOT pinned WHERE id=:id") fun pin(id: Long)
    @Query("UPDATE clips SET bucket=:bucket WHERE id=:id") fun group(id: Long, bucket: String)
    @Query("DELETE FROM clips WHERE id=:id") fun delete(id: Long)
    @Query("DELETE FROM clips WHERE time <= :before") fun expire(before: Long): Int
    @Query("SELECT MIN(time) FROM clips") fun oldest(): Long?
    @Query("SELECT COALESCE(SUM(length(CAST(body AS BLOB))+length(CAST(source AS BLOB))+40),0) FROM clips WHERE time > :after") fun exportSize(after: Long): Long
    @Query("SELECT * FROM clips WHERE time > :after ORDER BY time,id LIMIT :limit") fun export(after: Long, limit: Int): List<Clip>
    @Query("SELECT * FROM snippets ORDER BY id DESC") fun templates(): List<Snippet>
    @Insert fun addSnippet(s: Snippet): Long
    @Query("DELETE FROM snippets WHERE id=:id") fun deleteSnippet(id: Long)
}
@Database(entities=[Clip::class, Snippet::class], version=1, exportSchema=true)
abstract class PrivateDb : RoomDatabase() { abstract fun clips(): ClipDao }

/** The random SQLCipher secret is wrapped by a non-exportable Keystore AES key. No cleartext fallback. */
class Vault(private val ctx: Context) {
    private val name = "lasq-vault.db"; private val alias = "lasq.wrap.v1"
    private val wrapped = AtomicFile(File(ctx.noBackupFilesDir,"lasq.wrap"))
    private val monitor = Any(); private var db: PrivateDb? = null; private var memoryKey: ByteArray? = null
    val revision = MutableStateFlow(0L)
    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun password(): ByteArray {
        val existing = ks().getKey(alias, null) as? SecretKey
        val bytes = try { wrapped.readFully() } catch (_: java.io.FileNotFoundException) { null }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (bytes != null) {
            require(existing != null && bytes.size == 60)
            cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            return cipher.doFinal(bytes,12,bytes.size-12)
        }
        check(!ctx.getDatabasePath(name).exists()) // Never silently replace the key for an existing vault.
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }; cipher.init(Cipher.ENCRYPT_MODE, key)
        val stream = wrapped.startWrite()
        try { stream.write(cipher.iv + cipher.doFinal(secret)); wrapped.finishWrite(stream) }
        catch (e: Exception) { wrapped.failWrite(stream); secret.fill(0); throw e }
        return secret
    }
    private fun open(): PrivateDb = db ?: run {
        System.loadLibrary("sqlcipher")
        val pass = password()
        Room.databaseBuilder(ctx, PrivateDb::class.java,name).openHelperFactory(SupportOpenHelperFactory(pass))
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build().also { db = it; memoryKey = pass }
    }
    suspend fun <T> read(f: (ClipDao) -> T): T = withContext(Dispatchers.IO) { synchronized(monitor) { f(open().clips()) } }
    suspend fun <T> write(f: (ClipDao) -> T): T = read(f).also { revision.update { n -> n+1 } }
    suspend fun prune(days: Int): Long? {
        val (changed, first) = read { it.expire(System.currentTimeMillis() - days*DAY) to it.oldest() }
        if (changed > 0) revision.update { it+1 }; return first?.plus(days*DAY)
    }
    suspend fun erase() = withContext(Dispatchers.IO) {
        synchronized(monitor) {
            db?.close(); db = null; memoryKey?.fill(0); memoryKey = null
            ks().deleteEntry(alias); wrapped.delete(); ctx.deleteDatabase(name)
            revision.update { it+1 }
        }
    }
}
