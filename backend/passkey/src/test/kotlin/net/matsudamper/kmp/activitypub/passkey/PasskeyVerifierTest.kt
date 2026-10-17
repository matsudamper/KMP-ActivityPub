package net.matsudamper.kmp.activitypub.passkey

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PasskeyVerifierTest {
    private val verifier =
        PasskeyVerifier(
            PasskeyRelyingParty(id = TestAuthenticator.RP_ID, origins = setOf(TestAuthenticator.ORIGIN)),
        )

    @Test
    fun `登録した鍵でログインの署名を検証できる`() {
        TestPasskeyAlgorithm.entries.forEach { algorithm ->
            val authenticator = TestAuthenticator(algorithm)

            val registered = register(authenticator)
            val signCount = authenticate(authenticator, publicKey = registered.publicKey, signCount = 1, storedSignCount = 0)

            assertContentEquals(authenticator.credentialId, registered.credentialId, "$algorithm")
            assertEquals(1, signCount, "$algorithm")
        }
    }

    @Test
    fun `別の鍵の署名は通らない`() {
        val registered = register(TestAuthenticator(TestPasskeyAlgorithm.ES256))

        assertFailsWith<PasskeyVerificationException> {
            authenticate(TestAuthenticator(TestPasskeyAlgorithm.ES256), publicKey = registered.publicKey, signCount = 1, storedSignCount = 0)
        }
    }

    @Test
    fun `許可していない origin は通らない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val clientData = authenticator.clientData(type = "webauthn.create", challenge = TestAuthenticator.challenge(), origin = "https://evil.example")

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(PasskeyClientData.parse(clientData), authenticator.attestationObject())
        }
    }

    @Test
    fun `ログイン用の clientDataJSON では登録できない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val clientData = authenticator.clientData(type = "webauthn.get", challenge = TestAuthenticator.challenge())

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(PasskeyClientData.parse(clientData), authenticator.attestationObject())
        }
    }

    @Test
    fun `別の RP ID で作られたパスキーは登録できない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256, rpId = "evil.example")
        val clientData = authenticator.clientData(type = "webauthn.create", challenge = TestAuthenticator.challenge())

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(PasskeyClientData.parse(clientData), authenticator.attestationObject())
        }
    }

    @Test
    fun `本人確認の無い登録は通らない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val clientData = authenticator.clientData(type = "webauthn.create", challenge = TestAuthenticator.challenge())

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(PasskeyClientData.parse(clientData), authenticator.attestationObject(flags = 0x01))
        }
    }

    @Test
    fun `署名回数が前回から増えていなければ通らない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val registered = register(authenticator)

        assertFailsWith<PasskeyVerificationException> {
            authenticate(authenticator, publicKey = registered.publicKey, signCount = 5, storedSignCount = 5)
        }
    }

    @Test
    fun `署名回数を数えない認証器は 0 のままでも通る`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val registered = register(authenticator)

        assertEquals(0, authenticate(authenticator, publicKey = registered.publicKey, signCount = 0, storedSignCount = 0))
    }

    @Test
    fun `authenticator data の後ろに余分なバイトがあれば通らない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val registered = register(authenticator)
        val clientData = authenticator.clientData(type = "webauthn.get", challenge = TestAuthenticator.challenge())
        val authenticatorData = authenticator.authenticatorData(signCount = 1) + byteArrayOf(0)

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyAuthentication(
                clientData = PasskeyClientData.parse(clientData),
                authenticatorData = authenticatorData,
                signature = authenticator.sign(authenticatorData, clientData),
                publicKey = registered.publicKey,
                storedSignCount = 0,
            )
        }
    }

    @Test
    fun `clientDataJSON から challenge を取り出せる`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val clientData = authenticator.clientData(type = "webauthn.get", challenge = TestAuthenticator.challenge())

        assertEquals(TestAuthenticator.challenge(), PasskeyClientData.parse(clientData).challenge)
    }

    @Test
    fun `JSON として読めない clientDataJSON は検証の失敗として扱う`() {
        assertFailsWith<PasskeyVerificationException> {
            PasskeyClientData.parse("{".encodeToByteArray())
        }
    }

    @Test
    fun `UTF-8 として読めない文字列を含む attestationObject は検証の失敗として扱う`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)
        val clientData = authenticator.clientData(type = "webauthn.create", challenge = TestAuthenticator.challenge())
        // 要素 1 の map で、キーが 1 バイトの文字列 0xFF、値が 0
        val attestationObject = byteArrayOf(0xA1.toByte(), 0x61, 0xFF.toByte(), 0x00)

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(PasskeyClientData.parse(clientData), attestationObject)
        }
    }

    private fun register(authenticator: TestAuthenticator): RegisteredPasskey {
        val clientData = authenticator.clientData(type = "webauthn.create", challenge = TestAuthenticator.challenge())
        return verifier.verifyRegistration(PasskeyClientData.parse(clientData), authenticator.attestationObject())
    }

    private fun authenticate(
        authenticator: TestAuthenticator,
        publicKey: ByteArray,
        signCount: Long,
        storedSignCount: Long,
    ): Long {
        val clientData = authenticator.clientData(type = "webauthn.get", challenge = TestAuthenticator.challenge())
        val authenticatorData = authenticator.authenticatorData(signCount = signCount)
        return verifier.verifyAuthentication(
            clientData = PasskeyClientData.parse(clientData),
            authenticatorData = authenticatorData,
            signature = authenticator.sign(authenticatorData, clientData),
            publicKey = publicKey,
            storedSignCount = storedSignCount,
        )
    }
}
