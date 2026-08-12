package ir.peykhesab.app.data

import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 with a deterministic UTF-16BE encoding of the password chars.
 * Kept platform-independent so its output can be verified by plain JVM unit tests on every build.
 */
internal object BackupKdf {
    private const val HMAC = "HmacSHA256"
    const val OUTPUT_BYTES = 32

    fun deriveKeyBytes(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(passphrase.isNotEmpty()) { "رمز پشتیبان خالی است" }
        require(salt.isNotEmpty()) { "نمک رمزنگاری خالی است" }
        require(iterations > 0) { "تعداد تکرار KDF نامعتبر است" }

        val passwordBytes = charArrayToUtf16Be(passphrase)
        try {
            val mac = Mac.getInstance(HMAC)
            mac.init(SecretKeySpec(passwordBytes, HMAC))
            val blockInput = ByteArray(salt.size + 4)
            salt.copyInto(blockInput)
            // We derive exactly one SHA-256 block (32 bytes): INT_32_BE(1).
            blockInput[blockInput.lastIndex] = 1

            var u = mac.doFinal(blockInput)
            val output = u.copyOf()
            try {
                var round = 1
                while (round < iterations) {
                    val next = mac.doFinal(u)
                    Arrays.fill(u, 0.toByte())
                    u = next
                    for (index in output.indices) output[index] = (output[index].toInt() xor u[index].toInt()).toByte()
                    round++
                }
                return output
            } finally {
                Arrays.fill(u, 0.toByte())
                Arrays.fill(blockInput, 0.toByte())
            }
        } finally {
            Arrays.fill(passwordBytes, 0.toByte())
        }
    }

    private fun charArrayToUtf16Be(chars: CharArray): ByteArray {
        val bytes = ByteArray(Math.multiplyExact(chars.size, 2))
        chars.forEachIndexed { index, value ->
            val code = value.code
            bytes[index * 2] = (code ushr 8).toByte()
            bytes[index * 2 + 1] = code.toByte()
        }
        return bytes
    }
}
