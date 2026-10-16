package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty
import net.matsudamper.kmp.activitypub.passkey.TestAuthenticator
import net.matsudamper.kmp.activitypub.passkey.TestPasskeyAlgorithm
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.shared.AccountId

class PasskeyServiceTest {
    private val repositories = FakeRepositories()
    private val service =
        PasskeyService(
            relyingParty = PasskeyRelyingParty(id = TestAuthenticator.RP_ID, origins = setOf(TestAuthenticator.ORIGIN)),
            passkeyRepository = repositories.passkeyRepository,
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
        )

    private val account = Account(id = AccountId(1), username = "user1", createdAt = Instant.EPOCH)
    private val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)

    @Test
    fun `登録したパスキーでログインできる`() {
        val userHandle = register(account)

        assertEquals(account.id, login(userHandle = userHandle))
    }

    @Test
    fun `同じ challenge では二度ログインできない`() {
        val userHandle = register(account)
        val challenge = service.loginOptions().challenge

        assertEquals(account.id, login(userHandle = userHandle, challenge = challenge, signCount = 1))
        assertNull(login(userHandle = userHandle, challenge = challenge, signCount = 2))
    }

    @Test
    fun `発行していない challenge ではログインできない`() {
        val userHandle = register(account)

        assertNull(login(userHandle = userHandle, challenge = TestAuthenticator.challenge()))
    }

    @Test
    fun `別のアカウントの user handle を名乗るとログインできない`() {
        register(account)
        val otherUserHandle = service.registrationOptions(Account(id = AccountId(2), username = "user2", createdAt = Instant.EPOCH)).userHandle

        assertNull(login(userHandle = otherUserHandle))
    }

    @Test
    fun `別のアカウント向けの challenge では登録できない`() {
        val challenge = service.registrationOptions(Account(id = AccountId(2), username = "user2", createdAt = Instant.EPOCH)).challenge

        val result =
            service.register(
                accountId = account.id,
                name = "端末1",
                clientDataJson = TestAuthenticator.base64Url(authenticator.clientData(type = "webauthn.create", challenge = challenge)),
                attestationObject = TestAuthenticator.base64Url(authenticator.attestationObject()),
            )

        assertEquals(PasskeyService.RegistrationResult.NotAccepted, result)
    }

    @Test
    fun `同じパスキーは二度登録できない`() {
        register(account)

        val result =
            service.register(
                accountId = account.id,
                name = "端末1",
                clientDataJson = TestAuthenticator.base64Url(authenticator.clientData(type = "webauthn.create", challenge = service.registrationOptions(account).challenge)),
                attestationObject = TestAuthenticator.base64Url(authenticator.attestationObject()),
            )

        assertEquals(PasskeyService.RegistrationResult.AlreadyRegistered, result)
    }

    @Test
    fun `登録済みのパスキーを除外の対象に返す`() {
        register(account)

        assertEquals(listOf(TestAuthenticator.base64Url(authenticator.credentialId)), service.registrationOptions(account).excludeCredentialIds)
    }

    @Test
    fun `空の名前では登録できない`() {
        val result =
            service.register(
                accountId = account.id,
                name = " ",
                clientDataJson = TestAuthenticator.base64Url(authenticator.clientData(type = "webauthn.create", challenge = service.registrationOptions(account).challenge)),
                attestationObject = TestAuthenticator.base64Url(authenticator.attestationObject()),
            )

        assertEquals(PasskeyService.RegistrationResult.InvalidName, result)
    }

    @Test
    fun `消したパスキーではログインできない`() {
        val userHandle = register(account)

        service.delete(accountId = account.id, credentialId = TestAuthenticator.base64Url(authenticator.credentialId))

        assertNull(login(userHandle = userHandle))
    }

    /**
     * @return user handle
     */
    private fun register(account: Account): String {
        val options = service.registrationOptions(account)
        val result =
            service.register(
                accountId = account.id,
                name = "端末1",
                clientDataJson = TestAuthenticator.base64Url(authenticator.clientData(type = "webauthn.create", challenge = options.challenge)),
                attestationObject = TestAuthenticator.base64Url(authenticator.attestationObject()),
            )
        assertEquals(PasskeyService.RegistrationResult.Success, result)
        return options.userHandle
    }

    private fun login(
        userHandle: String,
        challenge: String = service.loginOptions().challenge,
        signCount: Long = 1,
    ): AccountId? {
        val clientData = authenticator.clientData(type = "webauthn.get", challenge = challenge)
        val authenticatorData = authenticator.authenticatorData(signCount = signCount)
        return service.login(
            credentialId = TestAuthenticator.base64Url(authenticator.credentialId),
            clientDataJson = TestAuthenticator.base64Url(clientData),
            authenticatorData = TestAuthenticator.base64Url(authenticatorData),
            signature = TestAuthenticator.base64Url(authenticator.sign(authenticatorData, clientData)),
            userHandle = userHandle,
        )
    }
}
