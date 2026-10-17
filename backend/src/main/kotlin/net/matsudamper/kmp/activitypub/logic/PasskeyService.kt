package net.matsudamper.kmp.activitypub.logic

import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import net.matsudamper.kmp.activitypub.passkey.PasskeyClientData
import net.matsudamper.kmp.activitypub.passkey.PasskeyRelyingParty
import net.matsudamper.kmp.activitypub.passkey.PasskeyVerificationException
import net.matsudamper.kmp.activitypub.passkey.PasskeyVerifier
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle
import org.slf4j.LoggerFactory

/**
 * バイト列の受け渡しはすべて base64url（パディング無し）
 */
class PasskeyService(
    private val relyingParty: PasskeyRelyingParty,
    private val passkeyRepository: PasskeyRepository,
    private val clock: Clock,
) {
    private val verifier = PasskeyVerifier(relyingParty)
    private val random = SecureRandom()
    private val challenges = PasskeyChallenges(clock = clock, ttl = CHALLENGE_TTL)

    fun loginOptions(): LoginOptions =
        LoginOptions(
            challenge = issueChallenge(PasskeyChallengePurpose.Authentication, accountId = null),
            rpId = relyingParty.id,
        )

    /**
     * @return 通ればログインするアカウント
     */
    fun login(
        credentialId: PasskeyCredentialId,
        clientDataJson: String,
        authenticatorData: String,
        signature: String,
        userHandle: PasskeyUserHandle?,
    ): AccountId? =
        verifyOrNull {
            val clientData = PasskeyClientData.parse(decode(clientDataJson))
            if (!challenges.consume(clientData.challenge, PasskeyChallengePurpose.Authentication, accountId = null)) {
                throw PasskeyVerificationException("発行していないか期限切れの challenge")
            }

            // 認証器が返した id と照合するため、表記を揃える
            val normalizedCredentialId = PasskeyCredentialId(encode(decode(credentialId.value)))
            val passkey =
                passkeyRepository.findByCredentialId(normalizedCredentialId)
                    ?: throw PasskeyVerificationException("登録されていない credential ID")
            // 別のアカウントのパスキーで、このアカウントの user handle を名乗っていないかを見る
            if (userHandle != null && PasskeyUserHandle(encode(decode(userHandle.value))) != passkey.userHandle) throw PasskeyVerificationException("user handle が持ち主と合わない")

            val signCount =
                verifier.verifyAuthentication(
                    clientData = clientData,
                    authenticatorData = decode(authenticatorData),
                    signature = decode(signature),
                    publicKey = passkey.publicKey,
                    storedSignCount = passkey.signCount,
                )
            passkeyRepository.updateSignCount(credentialId = normalizedCredentialId, signCount = signCount, usedAt = clock.instant())
            passkey.accountId
        }

    fun registrationOptions(account: Account): RegistrationOptions =
        RegistrationOptions(
            challenge = issueChallenge(PasskeyChallengePurpose.Registration, accountId = account.id),
            rpId = relyingParty.id,
            userHandle = passkeyRepository.userHandle(account.id) { PasskeyUserHandle(encode(randomBytes(USER_HANDLE_SIZE_BYTES))) },
            username = account.username,
            algorithms = verifier.supportedAlgorithms,
            excludeCredentialIds = passkeyRepository.passkeys(account.id).map { it.credentialId },
        )

    fun passkeys(accountId: AccountId): List<PasskeySummary> = passkeyRepository.passkeys(accountId)

    /**
     * @return [accountId] のパスキーが無ければ false
     */
    fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): Boolean = passkeyRepository.delete(accountId = accountId, credentialId = credentialId)

    fun register(
        accountId: AccountId,
        name: String,
        clientDataJson: String,
        attestationObject: String,
    ): RegistrationResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.codePointCount(0, trimmedName.length) > MAX_NAME_LENGTH) return RegistrationResult.InvalidName

        val registered =
            verifyOrNull {
                val clientData = PasskeyClientData.parse(decode(clientDataJson))
                // 別のアカウント向けに発行した challenge では足させない
                if (!challenges.consume(clientData.challenge, PasskeyChallengePurpose.Registration, accountId = accountId)) {
                    throw PasskeyVerificationException("発行していないか期限切れの challenge")
                }
                verifier.verifyRegistration(clientData, decode(attestationObject))
            } ?: return RegistrationResult.NotAccepted

        val added =
            passkeyRepository.add(
                accountId = accountId,
                credentialId = PasskeyCredentialId(encode(registered.credentialId)),
                publicKey = registered.publicKey,
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
        challenges.add(challenge = challenge, purpose = purpose, accountId = accountId)
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

    private fun decode(value: String): ByteArray =
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw PasskeyVerificationException("base64url として読めない", e)
        }

    class LoginOptions(
        val challenge: String,
        val rpId: String,
    )

    class RegistrationOptions(
        val challenge: String,
        val rpId: String,
        val userHandle: PasskeyUserHandle,
        val username: String,
        val algorithms: List<Long>,
        val excludeCredentialIds: List<PasskeyCredentialId>,
    )

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
