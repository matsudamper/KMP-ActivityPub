package net.matsudamper.kmp.activitypub.logic

import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import net.matsudamper.kmp.activitypub.passkey.PasskeyJson
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty
import net.matsudamper.kmp.activitypub.passkey.PasskeyVerificationException
import net.matsudamper.kmp.activitypub.passkey.PasskeyVerifier
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.session.PasskeyChallengePurpose
import net.matsudamper.kmp.activitypub.session.PasskeyChallengeStore
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle
import org.slf4j.LoggerFactory

/**
 * 認証器との受け渡しは [PasskeyJson] の JSON で行う
 */
class PasskeyService(
    private val relyingParty: PasskeyRelyingParty,
    private val passkeyRepository: PasskeyRepository,
    private val challengeStore: PasskeyChallengeStore,
    private val clock: Clock,
) {
    private val verifier = PasskeyVerifier(relyingParty)
    private val random = SecureRandom()

    /**
     * @return PublicKeyCredentialRequestOptionsJSON
     */
    fun loginOptions(): String =
        PasskeyJson.requestOptions(
            relyingParty = relyingParty,
            challenge = issueChallenge(PasskeyChallengePurpose.Authentication, accountId = null),
            timeout = CHALLENGE_TTL,
        )

    /**
     * @param credentialJson AuthenticationResponseJSON
     * @return 通ればログインするアカウント
     */
    fun login(credentialJson: String): AccountId? =
        verifyOrNull {
            val authentication = verifier.parseAuthentication(credentialJson)
            if (!challengeStore.consume(authentication.challenge, PasskeyChallengePurpose.Authentication, accountId = null)) {
                throw PasskeyVerificationException("発行していないか期限切れの challenge")
            }

            val credentialId = PasskeyCredentialId(encode(authentication.credentialId))
            val passkey =
                passkeyRepository.findByCredentialId(credentialId)
                    ?: throw PasskeyVerificationException("登録されていない credential ID")
            // 別のアカウントのパスキーで、このアカウントの user handle を名乗っていないかを見る
            val userHandle = authentication.userHandle
            if (userHandle != null && PasskeyUserHandle(encode(userHandle)) != passkey.userHandle) throw PasskeyVerificationException("user handle が持ち主と合わない")

            val signCount =
                verifier.verifyAuthentication(
                    authentication = authentication,
                    attestedCredentialData = passkey.attestedCredentialData,
                    storedSignCount = passkey.signCount,
                )
            passkeyRepository.updateSignCount(credentialId = credentialId, signCount = signCount, usedAt = clock.instant())
            passkey.accountId
        }

    /**
     * @return PublicKeyCredentialCreationOptionsJSON
     */
    fun registrationOptions(account: Account): String =
        PasskeyJson.creationOptions(
            relyingParty = relyingParty,
            challenge = issueChallenge(PasskeyChallengePurpose.Registration, accountId = account.id),
            userHandle = passkeyRepository.userHandle(account.id) { PasskeyUserHandle(encode(randomBytes(USER_HANDLE_SIZE_BYTES))) }.value,
            username = account.username,
            algorithms = verifier.supportedAlgorithms,
            excludeCredentialIds = passkeyRepository.passkeys(account.id).map { it.credentialId.value },
            timeout = CHALLENGE_TTL,
        )

    fun passkeys(accountId: AccountId): List<PasskeySummary> = passkeyRepository.passkeys(accountId)

    fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): PasskeyDeleteResult = passkeyRepository.delete(accountId = accountId, credentialId = credentialId)

    /**
     * @param credentialJson RegistrationResponseJSON
     */
    fun register(
        accountId: AccountId,
        name: String,
        credentialJson: String,
    ): RegistrationResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.codePointCount(0, trimmedName.length) > MAX_NAME_LENGTH) return RegistrationResult.InvalidName

        val registered =
            verifyOrNull {
                val registration = verifier.parseRegistration(credentialJson)
                // 別のアカウント向けに発行した challenge では足させない
                if (!challengeStore.consume(registration.challenge, PasskeyChallengePurpose.Registration, accountId = accountId)) {
                    throw PasskeyVerificationException("発行していないか期限切れの challenge")
                }
                verifier.verifyRegistration(registration)
            } ?: return RegistrationResult.NotAccepted

        val added =
            passkeyRepository.add(
                accountId = accountId,
                credentialId = PasskeyCredentialId(encode(registered.credentialId)),
                attestedCredentialData = registered.attestedCredentialData,
                signCount = registered.signCount,
                name = trimmedName,
                createdAt = clock.instant(),
            )
        return if (added) RegistrationResult.Success else RegistrationResult.AlreadyRegistered
    }

    private fun issueChallenge(
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ): String {
        val challenge = encode(randomBytes(CHALLENGE_SIZE_BYTES))
        challengeStore.add(challenge = challenge, purpose = purpose, accountId = accountId, expiresAt = clock.instant().plus(CHALLENGE_TTL.toJavaDuration()))
        return challenge
    }

    private fun <T> verifyOrNull(block: () -> T): T? =
        try {
            block()
        } catch (e: PasskeyVerificationException) {
            // origin や RP ID の設定違いはここでしか分からない
            logger.info("パスキーの検証に通らなかった: {}", e.message)
            null
        }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun encode(bytes: ByteArray): String = BASE64_ENCODER.encodeToString(bytes)

    sealed interface RegistrationResult {
        data object Success : RegistrationResult

        data object NotAccepted : RegistrationResult

        data object AlreadyRegistered : RegistrationResult

        data object InvalidName : RegistrationResult
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(PasskeyService::class.java)

        /**
         * WebAuthn は 16 バイト以上を求める
         */
        const val CHALLENGE_SIZE_BYTES = 32

        /**
         * WebAuthn の user.id の上限は 64 バイト
         */
        const val USER_HANDLE_SIZE_BYTES = 32

        /**
         * 本人確認を求めるときに WebAuthn が勧めるタイムアウトの上限に合わせる
         */
        val CHALLENGE_TTL = 5.minutes

        const val MAX_NAME_LENGTH = 64

        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
