package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class SeedStaffCredential(
        val username: String,
        val displayName: String,
        val role: String,
        val saltBase64: String,
        val hashBase64: String
    )

    // Precomputed salted PBKDF2-HMAC-SHA1 digests (zero plaintext passwords in code or database)
    val INITIAL_STAFF_CREDENTIALS = listOf(
        SeedStaffCredential(
            username = "ErdiBasaran",
            displayName = "Erdi Başaran",
            role = "ADMIN",
            saltBase64 = "pxUJ6rfJy7pe6r8/0At3xQ==",
            hashBase64 = "EjnI2eAO7FOYIU1LlwCq+8pcPqg6eAiPoRAzwLNXTNo="
        ),
        SeedStaffCredential(
            username = "MustafacanYalnz",
            displayName = "Mustafacan Yalnız",
            role = "ADMIN",
            saltBase64 = "lajSBsEziXrlGy99Qu8IBg==",
            hashBase64 = "twLfcyRGroS+qQ33gmNx10VdlBUXavQwYqID3Hzj0Q0="
        ),
        SeedStaffCredential(
            username = "Garson",
            displayName = "Garson",
            role = "WAITER",
            saltBase64 = "V+CdDwKZkK0wYPT0KY42Hg==",
            hashBase64 = "qTL2it8+hokKi3KWADc1vASl6nYxXvfBHJlmdsABHCU="
        )
    )

    fun hashPassword(password: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val digest = deriveKey(password, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to
            Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (password.isEmpty() || saltBase64.isBlank() || expectedHashBase64.isBlank()) return false
        return try {
            val salt = Base64.decode(saltBase64, Base64.DEFAULT)
            val expected = Base64.decode(expectedHashBase64, Base64.DEFAULT)
            val actual = deriveKey(password, salt)
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
