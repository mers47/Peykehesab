package ir.peykhesab.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupKdfTest {
    @Test
    fun pbkdf2Sha256MatchesIndependentKnownVectors() {
        val passphrase = "password".toCharArray()
        val salt = "salt".encodeToByteArray()
        val vectors = mapOf(
            1 to "e8440af370a6181f4a74f7c4894c5d6ad213897a0b12d1248bb6a195fab392e9",
            2 to "eed5fe95b932efb9994e76b8800b0fb01791b4e152b76a03b1ad4b4c54250033",
            4096 to "b56772e66f462adca1f1c3a6cf859f8d6149c492ba2c3d5ddeb70a0894742739"
        )
        vectors.forEach { (iterations, expected) ->
            val actual = BackupKdf.deriveKeyBytes(passphrase, salt, iterations).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals(expected, actual)
        }
    }
}
