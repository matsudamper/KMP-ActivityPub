package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.delivery.DeliveryQueueWorker
import net.matsudamper.kmp.activitypub.delivery.DeliveryRetryPolicy
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.HttpRemoteActors
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.delivery.HttpActivityDelivery
import net.matsudamper.kotpub.follower.FollowerEndpoint
import net.matsudamper.kotpub.httpsignature.HttpSignatureVerifier
import net.matsudamper.kotpub.inbox.DeleteActorHandler
import net.matsudamper.kotpub.inbox.FollowHandler
import net.matsudamper.kotpub.inbox.InboxEndpoint
import net.matsudamper.kotpub.inbox.InboxService
import net.matsudamper.kotpub.inbox.RecordedFallbackPublicKeys
import net.matsudamper.kotpub.inbox.UndoFollowHandler
import net.matsudamper.kotpub.inbox.UndoHandler
import net.matsudamper.kotpub.inbox.UndoReactionHandler
import net.matsudamper.kotpub.inbox.UpdateActorHandler
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint

internal class Federation(
    domain: String,
    accountService: AccountService,
    followerRepository: FollowerRepository,
    deliveryQueueRepository: DeliveryQueueRepository,
    openTelemetry: OpenTelemetry,
) : AutoCloseable {
    private val httpClient = OkHttpActivityPubClient()
    private val directory = ActorDirectory(domain = domain, stored = AccountActorNames(accountService))
    private val remoteActors = HttpRemoteActors(client = httpClient, openTelemetry = openTelemetry)

    val deliveryQueueWorker: DeliveryQueueWorker =
        DeliveryQueueWorker(
            queue = deliveryQueueRepository,
            senders = LocalActors(directory = directory, accountService = accountService),
            delivery = HttpActivityDelivery(client = httpClient),
            retryPolicy = DeliveryRetryPolicy(initialDelay = 30.seconds, maxDelay = 24.hours, giveUpAfter = 7.days),
            clock = Clock.systemUTC(),
        )

    private val followers =
        AccountFollowerStore(
            followerRepository = followerRepository,
            onAcceptEnqueued = deliveryQueueWorker::wakeUp,
        )

    val webFingerEndpoint: WebFingerEndpoint = WebFingerEndpoint(directory)

    val actorEndpoint: ActorEndpoint =
        ActorEndpoint(
            directory = directory,
            publicKeys = AccountActorPublicKeys(accountService),
            appearances = EmptyActorAppearances,
            profiles = EmptyActorProfiles,
            webPages = null,
        )

    val followerEndpoint: FollowerEndpoint = FollowerEndpoint(directory = directory, followers = followers)

    /**
     * kotpub の InboxService.default からお気に入りとスタンプの受け付けを抜いたもの
     */
    val inboxEndpoint: InboxEndpoint =
        InboxEndpoint(
            directory = directory,
            service = InboxService(
                verifier = HttpSignatureVerifier(
                    RecordedFallbackPublicKeys(
                        remote = remoteActors,
                        followers = followers,
                        favourites = NoReactions,
                        stamps = NoReactions,
                    ),
                ),
                domainBlocks = NoInboxDomainBlocks,
                handlers = listOf(
                    FollowHandler(directory = directory, remoteActors = remoteActors, followers = followers),
                    UndoHandler(
                        reactions = UndoReactionHandler(
                            domain = domain,
                            favourites = NoReactions,
                            stamps = NoReactions,
                            earlyUndoneLikes = NoReactions,
                        ),
                        follows = UndoFollowHandler(directory = directory, followers = followers),
                    ),
                    UpdateActorHandler(followers),
                    DeleteActorHandler(followers = followers, favourites = NoReactions, stamps = NoReactions),
                ),
            ),
        )

    override fun close() {
        httpClient.close()
    }
}

private object EmptyActorAppearances : StoredActorAppearances {
    override fun find(username: String): ActorAppearance = ActorAppearance.EMPTY
}

private object EmptyActorProfiles : StoredActorProfiles {
    override fun find(username: String): ActorProfile = ActorProfile.EMPTY
}
