package net.matsudamper.kmp.activitypub.passkey

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * ブラウザと認証器の代わりに、仕様どおりのバイト列を作る
 */
class TestAuthenticator(
    private val algorithm: TestPasskeyAlgorithm,
    private val rpId: String = RP_ID,
) {
    private val keyPair: KeyPair =
        when (algorithm) {
            TestPasskeyAlgorithm.ES256 -> KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            TestPasskeyAlgorithm.ED25519 -> KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            TestPasskeyAlgorithm.RS256 -> KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }

    val credentialId: ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

    fun coseKey(): ByteArray =
        when (algorithm) {
            TestPasskeyAlgorithm.ES256 -> {
                val point = (keyPair.public as ECPublicKey).w
                TestCbor.encode(
                    mapOf(
                        1L to 2L,
                        3L to -7L,
                        -1L to 1L,
                        -2L to point.affineX.toFixedBytes(32),
                        -3L to point.affineY.toFixedBytes(32),
                    ),
                )
            }

            TestPasskeyAlgorithm.ED25519 -> {
                // SubjectPublicKeyInfo の末尾 32 バイトが鍵そのもの
                val encoded = keyPair.public.encoded
                TestCbor.encode(
                    mapOf(
                        1L to 1L,
                        3L to -8L,
                        -1L to 6L,
                        -2L to encoded.copyOfRange(encoded.size - 32, encoded.size),
                    ),
                )
            }

            TestPasskeyAlgorithm.RS256 -> {
                val key = keyPair.public as RSAPublicKey
                TestCbor.encode(
                    mapOf(
                        1L to 3L,
                        3L to -257L,
                        -1L to key.modulus.toUnsignedBytes(),
                        -2L to key.publicExponent.toUnsignedBytes(),
                    ),
                )
            }
        }

    fun clientData(
        type: String,
        challenge: String,
        origin: String = ORIGIN,
    ): ByteArray = """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}""".encodeToByteArray()

    fun attestationObject(flags: Int = FLAGS_UP_UV): ByteArray {
        val attestedCredential =
            ByteArray(16) +
                byteArrayOf(0, credentialId.size.toByte()) +
                credentialId +
                coseKey()
        return TestCbor.encode(
            mapOf(
                "fmt" to "none",
                "attStmt" to mapOf<Any, Any>(),
                "authData" to authenticatorData(flags = flags or FLAG_AT, signCount = 0) + attestedCredential,
            ),
        )
    }

    fun authenticatorData(
        flags: Int = FLAGS_UP_UV,
        signCount: Long,
    ): ByteArray =
        sha256(rpId.encodeToByteArray()) +
            byteArrayOf(flags.toByte()) +
            ByteArray(4) { index -> (signCount shr (8 * (3 - index))).toByte() }

    fun sign(
        authenticatorData: ByteArray,
        clientData: ByteArray,
    ): ByteArray =
        Signature.getInstance(algorithm.jcaName).run {
            initSign(keyPair.private)
            update(authenticatorData + sha256(clientData))
            sign()
        }

    /**
     * RegistrationResponseJSON
     */
    fun registrationResponseJson(
        clientData: ByteArray,
        attestationObject: ByteArray,
    ): String {
        val id = base64Url(credentialId)
        return """{"id":"$id","rawId":"$id","type":"public-key",""" +
            """"response":{"clientDataJSON":"${base64Url(clientData)}","attestationObject":"${base64Url(attestationObject)}"}}"""
    }

    /**
     * AuthenticationResponseJSON
     */
    fun authenticationResponseJson(
        clientData: ByteArray,
        authenticatorData: ByteArray,
        userHandle: String?,
    ): String {
        val id = base64Url(credentialId)
        val userHandleJson = userHandle?.let { "\"$it\"" } ?: "null"
        return """{"id":"$id","rawId":"$id","type":"public-key",""" +
            """"response":{"clientDataJSON":"${base64Url(clientData)}","authenticatorData":"${base64Url(authenticatorData)}",""" +
            """"signature":"${base64Url(sign(authenticatorData, clientData))}","userHandle":$userHandleJson}}"""
    }

    companion object {
        const val RP_ID = "example.com"
        const val ORIGIN = "https://example.com"

        const val FLAGS_UP_UV = 0x05
        private const val FLAG_AT = 0x40

        fun challenge(): String = base64Url(ByteArray(32) { 1 })

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun BigInteger.toUnsignedBytes(): ByteArray = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()

        private fun BigInteger.toFixedBytes(size: Int): ByteArray {
            val unsigned = toUnsignedBytes()
            return ByteArray(size - unsigned.size) + unsigned
        }
    }
}

enum class TestPasskeyAlgorithm(
    val jcaName: String,
) {
    ES256("SHA256withECDSA"),
    ED25519("Ed25519"),
    RS256("SHA256withRSA"),
}

internal object TestCbor {
    fun encode(value: Any?): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

    private fun write(
        out: ByteArrayOutputStream,
        value: Any?,
    ) {
        when (value) {
            is Long -> {
                if (value >= 0) header(out, 0, value) else header(out, 1, -1 - value)
            }

            is ByteArray -> {
                header(out, 2, value.size.toLong())
                out.write(value)
            }

            is String -> {
                val bytes = value.encodeToByteArray()
                header(out, 3, bytes.size.toLong())
                out.write(bytes)
            }

            is Map<*, *> -> {
                header(out, 5, value.size.toLong())
                value.forEach { (key, element) ->
                    write(out, key)
                    write(out, element)
                }
            }

            else -> {
                error("テストで使わない型: $value")
            }
        }
    }

    private fun header(
        out: ByteArrayOutputStream,
        majorType: Int,
        argument: Long,
    ) {
        val prefix = majorType shl 5
        when {
            argument < 24 -> {
                out.write(prefix or argument.toInt())
            }

            argument < 0x100 -> {
                out.write(prefix or 24)
                out.write(argument.toInt())
            }

            argument < 0x10000 -> {
                out.write(prefix or 25)
                out.write((argument shr 8).toInt())
                out.write(argument.toInt() and 0xff)
            }

            else -> {
                error("テストで使わない長さ: $argument")
            }
        }
    }
}
