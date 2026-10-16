package net.matsudamper.kmp.activitypub.passkey

/**
 * WebAuthn Level 3 の 6.1 authenticator data
 */
internal class AuthenticatorData private constructor(
    val rpIdHash: ByteArray,
    private val flags: Int,
    val signCount: Long,
    val attestedCredential: AttestedCredential?,
) {
    val userPresent: Boolean get() = flags and FLAG_USER_PRESENT != 0
    val userVerified: Boolean get() = flags and FLAG_USER_VERIFIED != 0

    class AttestedCredential(
        val credentialId: ByteArray,
        val publicKey: ByteArray,
    )

    companion object {
        private const val RP_ID_HASH_SIZE = 32
        private const val AAGUID_SIZE = 16

        private const val FLAG_USER_PRESENT = 0x01
        private const val FLAG_USER_VERIFIED = 0x04
        private const val FLAG_ATTESTED_CREDENTIAL = 0x40
        private const val FLAG_EXTENSIONS = 0x80

        /**
         * 仕様上の上限
         */
        private const val MAX_CREDENTIAL_ID_SIZE = 1023

        fun parse(bytes: ByteArray): AuthenticatorData {
            if (bytes.size < RP_ID_HASH_SIZE + 1 + 4) throw PasskeyVerificationException("authenticator data が短すぎる")

            var position = 0
            val rpIdHash = bytes.copyOfRange(position, position + RP_ID_HASH_SIZE)
            position += RP_ID_HASH_SIZE
            val flags = bytes[position].toInt() and 0xff
            position += 1
            val signCount = bytes.readUnsigned(position, size = 4)
            position += 4

            val attestedCredential =
                if (flags and FLAG_ATTESTED_CREDENTIAL != 0) {
                    position += AAGUID_SIZE
                    if (position + 2 > bytes.size) throw PasskeyVerificationException("attested credential data が途中で終わっている")
                    val credentialIdSize = bytes.readUnsigned(position, size = 2).toInt()
                    position += 2
                    if (credentialIdSize > MAX_CREDENTIAL_ID_SIZE) throw PasskeyVerificationException("credential ID が長すぎる")
                    if (position + credentialIdSize > bytes.size) throw PasskeyVerificationException("credential ID が途中で終わっている")
                    val credentialId = bytes.copyOfRange(position, position + credentialIdSize)
                    position += credentialIdSize

                    // COSE 鍵の長さは書かれていないので、CBOR を 1 つ読んだ位置で切る
                    val keyReader = CborReader(bytes, startIndex = position)
                    keyReader.read()
                    val publicKey = bytes.copyOfRange(position, keyReader.position)
                    position = keyReader.position

                    AttestedCredential(credentialId = credentialId, publicKey = publicKey)
                } else {
                    null
                }

            if (flags and FLAG_EXTENSIONS != 0) {
                val extensionReader = CborReader(bytes, startIndex = position)
                if (extensionReader.read() !is Map<*, *>) throw PasskeyVerificationException("拡張が map ではない")
                position = extensionReader.position
            }

            if (position != bytes.size) throw PasskeyVerificationException("authenticator data の後ろに余分なバイトがある")

            return AuthenticatorData(
                rpIdHash = rpIdHash,
                flags = flags,
                signCount = signCount,
                attestedCredential = attestedCredential,
            )
        }

        private fun ByteArray.readUnsigned(
            start: Int,
            size: Int,
        ): Long {
            var value = 0L
            for (index in start until start + size) {
                value = (value shl 8) or (this[index].toLong() and 0xff)
            }
            return value
        }
    }
}
