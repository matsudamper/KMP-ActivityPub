package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakePasskeyChallengeStore
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty
import net.matsudamper.kmp.activitypub.passkey.TestAuthenticator
import net.matsudamper.kmp.activitypub.passkey.TestPasskeyAlgorithm
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId

class PasskeyServiceTest {
    private val repositories = FakeRepositories()
    private val service =
        PasskeyService(
            relyingParty = PasskeyRelyingParty(id = TestAuthenticator.RP_ID, origins = setOf(TestAuthenticator.ORIGIN)),
            passkeyRepository = repositories.passkeyRepository,
            challengeStore = FakePasskeyChallengeStore(),
            clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
        )

    private val account =
        Account(
            id = repositories.accountRepository.add(username = "user1", passwordHash = "hash", passwordHashType = "type"),
            username = "user1",
            createdAt = Instant.EPOCH,
        )
    private val authenticator = TestAuthenticator(TestPasskeyAlgorithm.ES256)

    @Test
    fun `登録したパスキーでログインできる`() {
        val userHandle = register(account)

        assertEquals(account.id, login(userHandle = userHandle))
    }

    @Test
    fun `同じ challenge では二度ログインできない`() {
        val userHandle = register(account)
        val challenge = loginChallenge()

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
        val otherOptions = parse(service.registrationOptions(Account(id = AccountId(2), username = "user2", createdAt = Instant.EPOCH)))

        assertNull(login(userHandle = otherOptions.userHandle()))
    }

    @Test
    fun `別のアカウント向けの challenge では登録できない`() {
        val otherOptions = parse(service.registrationOptions(Account(id = AccountId(2), username = "user2", createdAt = Instant.EPOCH)))

        assertEquals(PasskeyService.RegistrationResult.NotAccepted, register(account, challenge = otherOptions.challenge()))
    }

    @Test
    fun `同じパスキーは二度登録できない`() {
        register(account)

        assertEquals(PasskeyService.RegistrationResult.AlreadyRegistered, register(account, challenge = parse(service.registrationOptions(account)).challenge()))
    }

    @Test
    fun `登録済みのパスキーを除外の対象に返す`() {
        register(account)

        val excludeCredentialIds =
            parse(service.registrationOptions(account))
                .getValue("excludeCredentials")
                .jsonArray
                .map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf(TestAuthenticator.base64Url(authenticator.credentialId)), excludeCredentialIds)
    }

    @Test
    fun `空の名前では登録できない`() {
        val result = register(account, challenge = parse(service.registrationOptions(account)).challenge(), name = " ")

        assertEquals(PasskeyService.RegistrationResult.InvalidName, result)
    }

    @Test
    fun `JSON として読めない応答ではログインできない`() {
        register(account)
        loginChallenge()

        assertNull(service.login(credentialJson = "{"))
    }

    @Test
    fun `消したパスキーではログインできない`() {
        val userHandle = register(account)

        service.delete(accountId = account.id, credentialId = PasskeyCredentialId(TestAuthenticator.base64Url(authenticator.credentialId)))

        assertNull(login(userHandle = userHandle))
    }

    /**
     * @return user handle
     */
    private fun register(account: Account): String {
        val options = parse(service.registrationOptions(account))
        assertEquals(PasskeyService.RegistrationResult.Success, register(account, challenge = options.challenge()))
        return options.userHandle()
    }

    private fun register(
        account: Account,
        challenge: String,
        name: String = "端末1",
    ): PasskeyService.RegistrationResult {
        val clientData = authenticator.clientData(type = "webauthn.create", challenge = challenge)
        return service.register(
            accountId = account.id,
            name = name,
            credentialJson = authenticator.registrationResponseJson(clientData = clientData, attestationObject = authenticator.attestationObject()),
        )
    }

    private fun loginChallenge(): String = parse(service.loginOptions()).challenge()

    private fun login(
        userHandle: String,
        challenge: String = loginChallenge(),
        signCount: Long = 1,
    ): AccountId? {
        val clientData = authenticator.clientData(type = "webauthn.get", challenge = challenge)
        return service.login(
            credentialJson =
            authenticator.authenticationResponseJson(
                clientData = clientData,
                authenticatorData = authenticator.authenticatorData(signCount = signCount),
                userHandle = userHandle,
            ),
        )
    }

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun JsonObject.challenge(): String = getValue("challenge").jsonPrimitive.content

    private fun JsonObject.userHandle(): String = getValue("user").jsonObject.getValue("id").jsonPrimitive.content
}
