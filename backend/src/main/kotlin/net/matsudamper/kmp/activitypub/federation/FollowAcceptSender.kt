package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.PendingFollowAccept
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.crypto.RsaKeys
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.DeliveryResult
import org.slf4j.LoggerFactory

/**
 * [AccountFollowerStore] が積んだ `Accept` を送る。送れたときにフォローが成立する
 */
internal class FollowAcceptSender(
    private val followerRepository: FollowerRepository,
    private val accountService: AccountService,
    private val delivery: ActivityDelivery,
    private val domain: String,
    private val clock: Clock,
) {
    suspend fun run() {
        while (true) {
            try {
                sendDue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Accept の送信に失敗した", e)
            }
            delay(POLL_INTERVAL)
        }
    }

    suspend fun sendDue() {
        val accepts = withContext(Dispatchers.IO) { followerRepository.findDueAccepts(now = clock.instant(), limit = BATCH_SIZE) }
        accepts.forEach { send(it) }
    }

    private suspend fun send(accept: PendingFollowAccept) {
        val sender = withContext(Dispatchers.IO) { localActor(accept.followeeUsername) }
        if (sender == null) {
            logger.warn("鍵の無いアカウントなので Accept を送らない: ${accept.followeeUsername} → ${accept.followerActorUri}")
            withContext(Dispatchers.IO) { followerRepository.giveUpAccept(accept) }
            return
        }

        val result = delivery.deliver(inbox = accept.followerInbox, sender = sender, body = accept.body.toByteArray(Charsets.UTF_8))
        withContext(Dispatchers.IO) {
            when (result) {
                DeliveryResult.Delivered -> {
                    followerRepository.markAccepted(accept = accept, acceptedAt = clock.instant())
                    logger.info("Accept を送った: ${accept.followeeUsername} ← ${accept.followerActorUri}")
                }

                is DeliveryResult.Failed -> {
                    if (!result.retryable || accept.attempts + 1 >= MAX_ATTEMPTS) {
                        followerRepository.giveUpAccept(accept)
                        logger.warn("Accept を送るのを諦めた: ${accept.followeeUsername} ← ${accept.followerActorUri} ${result.reason}")
                    } else {
                        followerRepository.postponeAccept(
                            accept = accept,
                            nextAttemptAt = clock.instant() + retryDelay(accept.attempts).toJavaDuration(),
                        )
                        logger.info("Accept を送れなかったので後で送り直す: ${accept.followeeUsername} ← ${accept.followerActorUri} ${result.reason}")
                    }
                }
            }
        }
    }

    private fun localActor(username: String): LocalActor? {
        val privateKeyPem = accountService.privateKeyPem(username) ?: return null
        return LocalActor(
            urls = ActorUrls(domain = domain, username = username),
            privateKey = ActorPrivateKey(
                privateKey = RsaKeys.decodePrivateKeyPem(privateKeyPem),
                // DB の鍵に当たるものが無い。Origin は kotpub の中では読まれない
                origin = ActorPrivateKey.Origin.Environment,
            ),
        )
    }

    private fun retryDelay(attempts: Int): Duration = minOf(FIRST_RETRY_DELAY * 2.0.pow(attempts), MAX_RETRY_DELAY)

    private companion object {
        private val logger = LoggerFactory.getLogger(FollowAcceptSender::class.java)

        val POLL_INTERVAL: Duration = 5.seconds
        const val BATCH_SIZE = 20

        /**
         * 間隔の上限に届いてから 1 日ほど送り直したところで諦める
         */
        const val MAX_ATTEMPTS = 12
        val FIRST_RETRY_DELAY: Duration = 1.minutes
        val MAX_RETRY_DELAY: Duration = 6.hours
    }
}
