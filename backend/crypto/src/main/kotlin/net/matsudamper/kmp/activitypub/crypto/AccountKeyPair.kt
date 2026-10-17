package net.matsudamper.kmp.activitypub.crypto

import java.security.KeyPairGenerator
import java.util.Base64

/**
 * ActivityPub の Actor の publicKey に載せる鍵。
 * Mastodon などが RSA しか検証しないため RSA にする
 */
class AccountKeyPair(
    val publicKeyPem: String,
    val privateKeyPem: String,
) {
    companion object {
        private const val KEY_SIZE = 2048
        private const val PEM_LINE_LENGTH = 64

        fun generate(): AccountKeyPair {
            val keyPair =
                KeyPairGenerator.getInstance("RSA")
                    .apply { initialize(KEY_SIZE) }
                    .generateKeyPair()
            return AccountKeyPair(
                publicKeyPem = toPem(type = "PUBLIC KEY", encoded = keyPair.public.encoded),
                privateKeyPem = toPem(type = "PRIVATE KEY", encoded = keyPair.private.encoded),
            )
        }

        private fun toPem(
            type: String,
            encoded: ByteArray,
        ): String {
            val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray()).encodeToString(encoded)
            return "-----BEGIN $type-----\n$body\n-----END $type-----\n"
        }
    }
}
