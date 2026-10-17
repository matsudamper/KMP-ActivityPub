package net.matsudamper.kmp.activitypub.crypto

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class AccountKeyPairTest {
    @Test
    fun `秘密鍵で署名した値を公開鍵で検証できる`() {
        val keyPair = AccountKeyPair.generate()
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(decodePem(keyPair.publicKeyPem, "PUBLIC KEY")))
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(decodePem(keyPair.privateKeyPem, "PRIVATE KEY")))
        val message = "message".toByteArray()

        val signature =
            Signature.getInstance("SHA256withRSA")
                .apply {
                    initSign(privateKey)
                    update(message)
                }.sign()

        val verified =
            Signature.getInstance("SHA256withRSA")
                .apply {
                    initVerify(publicKey)
                    update(message)
                }.verify(signature)
        assertTrue(verified)
    }

    private fun decodePem(
        pem: String,
        type: String,
    ): ByteArray {
        val body =
            pem
                .removePrefix("-----BEGIN $type-----\n")
                .removeSuffix("-----END $type-----\n")
        return Base64.getMimeDecoder().decode(body)
    }
}
