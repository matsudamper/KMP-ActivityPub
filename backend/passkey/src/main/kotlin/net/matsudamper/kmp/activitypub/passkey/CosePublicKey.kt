package net.matsudamper.kmp.activitypub.passkey

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * COSE_Key（RFC 9052 / RFC 9053）で表した公開鍵。
 * 登録時に受け取ったバイト列をそのまま保存し、ログインのたびにここで組み立て直す
 */
internal class CosePublicKey private constructor(
    private val algorithm: CoseAlgorithm,
    private val publicKey: PublicKey,
) {
    fun verify(
        data: ByteArray,
        signature: ByteArray,
    ): Boolean =
        try {
            Signature.getInstance(algorithm.jcaSignatureName).run {
                initVerify(publicKey)
                update(data)
                verify(signature)
            }
        } catch (_: GeneralSecurityException) {
            // DER として読めない署名なども、通らなかったものとして扱う
            false
        }

    companion object {
        private const val KEY_TYPE = 1L
        private const val ALGORITHM = 3L

        private const val EC2_CURVE = -1L
        private const val EC2_X = -2L
        private const val EC2_Y = -3L

        private const val RSA_N = -1L
        private const val RSA_E = -2L

        private const val OKP_CURVE = -1L
        private const val OKP_X = -2L

        private const val KEY_TYPE_OKP = 1L
        private const val KEY_TYPE_EC2 = 2L
        private const val KEY_TYPE_RSA = 3L

        private const val CURVE_P256 = 1L
        private const val CURVE_ED25519 = 6L

        private const val P256_COORDINATE_SIZE = 32
        private const val ED25519_KEY_SIZE = 32

        /**
         * 2048 bit 未満の RSA は NIST SP 800-131A で使用不可
         */
        private const val MIN_RSA_BITS = 2048

        /**
         * Ed25519 の SubjectPublicKeyInfo のうち、鍵の 32 バイトより前の固定部分（RFC 8410）
         */
        private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        private val P256_PARAMETERS: ECParameterSpec by lazy {
            AlgorithmParameters
                .getInstance("EC")
                .apply { init(ECGenParameterSpec("secp256r1")) }
                .getParameterSpec(ECParameterSpec::class.java)
        }

        fun parse(encoded: ByteArray): CosePublicKey {
            val reader = CborReader(encoded, startIndex = 0)
            val key = reader.read()
            if (reader.position != encoded.size) throw PasskeyVerificationException("COSE 鍵の後ろに余分なバイトがある")
            return fromMap(key as? Map<*, *> ?: throw PasskeyVerificationException("COSE 鍵が map ではない"))
        }

        fun fromMap(key: Map<*, *>): CosePublicKey {
            val algorithm =
                CoseAlgorithm.entries.firstOrNull { it.coseValue == key[ALGORITHM] }
                    ?: throw PasskeyVerificationException("対応していない署名方式")
            val keyType = key[KEY_TYPE]

            val publicKey =
                try {
                    when (algorithm) {
                        CoseAlgorithm.ES256 -> {
                            requireKeyType(keyType, KEY_TYPE_EC2)
                            if (key[EC2_CURVE] != CURVE_P256) throw PasskeyVerificationException("ES256 の曲線が P-256 ではない")
                            val x = key.bytes(EC2_X, P256_COORDINATE_SIZE)
                            val y = key.bytes(EC2_Y, P256_COORDINATE_SIZE)
                            KeyFactory.getInstance("EC").generatePublic(
                                ECPublicKeySpec(ECPoint(BigInteger(1, x), BigInteger(1, y)), P256_PARAMETERS),
                            )
                        }

                        CoseAlgorithm.RS256 -> {
                            requireKeyType(keyType, KEY_TYPE_RSA)
                            val modulus = BigInteger(1, key.bytes(RSA_N, size = null))
                            if (modulus.bitLength() < MIN_RSA_BITS) throw PasskeyVerificationException("RSA の鍵が短すぎる")
                            val exponent = BigInteger(1, key.bytes(RSA_E, size = null))
                            KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
                        }

                        CoseAlgorithm.ED25519 -> {
                            requireKeyType(keyType, KEY_TYPE_OKP)
                            if (key[OKP_CURVE] != CURVE_ED25519) throw PasskeyVerificationException("EdDSA の曲線が Ed25519 ではない")
                            val x = key.bytes(OKP_X, ED25519_KEY_SIZE)
                            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(ED25519_SPKI_PREFIX + x))
                        }
                    }
                } catch (e: GeneralSecurityException) {
                    throw PasskeyVerificationException("公開鍵を組み立てられない", e)
                }

            return CosePublicKey(algorithm = algorithm, publicKey = publicKey)
        }

        private fun requireKeyType(
            actual: Any?,
            expected: Long,
        ) {
            if (actual != expected) throw PasskeyVerificationException("鍵の種類が署名方式と合わない")
        }

        private fun Map<*, *>.bytes(
            label: Long,
            size: Int?,
        ): ByteArray {
            val value = this[label] as? ByteArray ?: throw PasskeyVerificationException("COSE 鍵に $label が無い")
            if (size != null && value.size != size) throw PasskeyVerificationException("COSE 鍵の $label の長さが違う")
            return value
        }
    }
}

/**
 * [coseValue] は IANA の COSE Algorithms の値
 */
internal enum class CoseAlgorithm(
    val coseValue: Long,
    val jcaSignatureName: String,
) {
    ES256(coseValue = -7, jcaSignatureName = "SHA256withECDSA"),
    ED25519(coseValue = -8, jcaSignatureName = "Ed25519"),
    RS256(coseValue = -257, jcaSignatureName = "SHA256withRSA"),
}
