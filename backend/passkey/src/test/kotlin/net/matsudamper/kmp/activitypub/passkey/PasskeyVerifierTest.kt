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
    fun `応答から challenge を取り出せる`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)

        assertEquals(TestAuthenticator.challenge(), verifier.parseRegistration(registrationJson(authenticator)).challenge)
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

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(verifier.parseRegistration(registrationJson(authenticator, origin = "https://evil.example")))
        }
    }

    @Test
    fun `ログイン用の clientDataJSON では登録できない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(verifier.parseRegistration(registrationJson(authenticator, type = "webauthn.get")))
        }
    }

    @Test
    fun `別の RP ID で作られたパスキーは登録できない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256, rpId = "evil.example")

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(verifier.parseRegistration(registrationJson(authenticator)))
        }
    }

    @Test
    fun `本人確認の無い登録は通らない`() {
        val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)

        assertFailsWith<PasskeyVerificationException> {
            verifier.verifyRegistration(verifier.parseRegistration(registrationJson(authenticator, flags = 0x01)))
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
    fun `JSON として読めない応答は検証の失敗として扱う`() {
        assertFailsWith<PasskeyVerificationException> {
            verifier.parseRegistration("{")
        }
        assertFailsWith<PasskeyVerificationException> {
            verifier.parseAuthentication("{")
        }
    }

    private fun registrationJson(
        authenticator: TestAuthenticator,
        type: String = "webauthn.create",
        origin: String = TestAuthenticator.ORIGIN,
        flags: Int = TestAuthenticator.FLAGS_UP_UV,
    ): String =
        authenticator.registrationResponseJson(
            clientData = authenticator.clientData(type = type, challenge = TestAuthenticator.challenge(), origin = origin),
            attestationObject = authenticator.attestationObject(flags = flags),
        )

    private fun register(authenticator: TestAuthenticator): RegisteredPasskey = verifier.verifyRegistration(verifier.parseRegistration(registrationJson(authenticator)))

    private fun authenticate(
        authenticator: TestAuthenticator,
        publicKey: ByteArray,
        signCount: Long,
        storedSignCount: Long,
    ): Long {
        val json =
            authenticator.authenticationResponseJson(
                clientData = authenticator.clientData(type = "webauthn.get", challenge = TestAuthenticator.challenge()),
                authenticatorData = authenticator.authenticatorData(signCount = signCount),
                userHandle = null,
            )
        return verifier.verifyAuthentication(verifier.parseAuthentication(json), publicKey = publicKey, storedSignCount = storedSignCount)
    }
}
