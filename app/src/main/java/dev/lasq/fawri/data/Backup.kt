package dev.lasq.fawri.data

import dev.lasq.fawri.core.*
import java.io.*
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.*

/** Portable authenticated archive. No Keystore key or settings are ever exported. */
object Backup {
    const val LIMIT = 4 * 1024 * 1024
    const val ROWS = 1_000
    private val magic = "LASQ\u0001".toByteArray(Charsets.US_ASCII)

    private fun cipher(mode: Int, pass: CharArray, header: ByteArray): Cipher {
        require(pass.size in 10..256 && header.copyOfRange(0, 5).contentEquals(magic))
        val spec = PBEKeySpec(pass, header.copyOfRange(5, 21), 210_000, 256)
        val key =
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(21, 33)))
                updateAAD(header)
            }
        } finally {
            key.fill(0)
        }
    }

    fun seal(rows: List<Clip>, pass: CharArray): ByteArray {
        require(rows.size <= ROWS)
        val buf = ByteArrayOutputStream()
        val out = DataOutputStream(buf)
        fun string(s: String) {
            val b = s.toByteArray()
            out.writeInt(b.size)
            out.write(b)
        }
        out.writeInt(rows.size)
        rows.forEach { c ->
            string(c.body)
            string(c.source)
            out.writeLong(c.time)
            out.writeBoolean(c.pinned)
            string(c.bucket)
            require(buf.size() <= LIMIT - 49)
        }
        val header = magic + ByteArray(28).also { SecureRandom().nextBytes(it) }
        val plain = buf.toByteArray()
        return try {
            header + cipher(Cipher.ENCRYPT_MODE, pass, header).doFinal(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun readBounded(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val block = ByteArray(8192)
        while (true) {
            val n = stream.read(block)
            if (n < 0) break
            require(out.size() + n <= LIMIT)
            out.write(block, 0, n)
        }
        return out.toByteArray()
    }

    fun open(bytes: ByteArray, pass: CharArray): List<Clip> {
        require(bytes.size in 53..LIMIT)
        val plain =
            cipher(Cipher.DECRYPT_MODE, pass, bytes.copyOfRange(0, 33))
                .doFinal(bytes, 33, bytes.size - 33)
        try {
            val input = DataInputStream(ByteArrayInputStream(plain))
            val count = input.readInt()
            require(count in 0..ROWS)
            fun string(max: Int): String {
                val n = input.readInt()
                require(n in 0..max * 4 && n <= input.available())
                return ByteArray(n)
                    .also { input.readFully(it) }
                    .toString(Charsets.UTF_8)
                    .also { require(it.length <= max) }
            }
            val rows =
                List(count) {
                    val body = string(MAX_TEXT)
                    require(body.isNotEmpty())
                    val source = string(255)
                    val time = input.readLong()
                    require(time > 0)
                    val pin = input.readBoolean()
                    val group = string(12)
                    require(group in setOf("NONE", "WORK", "PERSONAL"))
                    Clip(
                        body = body,
                        source = source,
                        time = time,
                        kind = "TEXT",
                        pinned = pin,
                        bucket = group,
                    )
                }
            require(input.available() == 0)
            return rows
        } finally {
            plain.fill(0)
        }
    }
}
