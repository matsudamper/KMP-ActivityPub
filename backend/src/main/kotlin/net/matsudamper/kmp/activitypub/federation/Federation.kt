package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.delivery.DeliveryQueueWorker
import net.matsudamper.kmp.activitypub.delivery.DeliveryRetryPolicy
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.BoostService
import net.matsudamper.kmp.activitypub.logic.LikeService
import net.matsudamper.kmp.activitypub.logic.LinkPreviewService
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.NoteShareUrls
import net.matsudamper.kmp.activitypub.logic.ProfileIconUrls
import net.matsudamper.kmp.activitypub.logic.ProfileUpdateService
import net.matsudamper.kmp.activitypub.logic.RemoteImageProxy
import net.matsudamper.kmp.activitypub.logic.RemoteProfileRefreshService
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfiles
import net.matsudamper.kmp.activitypub.logic.ReplyTargetService
import net.matsudamper.kmp.activitypub.logic.UploadedNoteImageCleaner
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.timeline.AccountEvents
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorIconEndpoint
import net.matsudamper.kotpub.actor.HttpRemoteActors
import net.matsudamper.kotpub.delivery.HttpActivityDelivery
import net.matsudamper.kotpub.follower.FollowerEndpoint
import net.matsudamper.kotpub.httpsignature.HttpSignatureVerifier
import net.matsudamper.kotpub.inbox.AnnounceHandler
import net.matsudamper.kotpub.inbox.CreateNoteHandler
import net.matsudamper.kotpub.inbox.DeleteActorHandler
import net.matsudamper.kotpub.inbox.DeleteHandler
import net.matsudamper.kotpub.inbox.FollowHandler
import net.matsudamper.kotpub.inbox.FollowResponseHandler
import net.matsudamper.kotpub.inbox.InboxEndpoint
import net.matsudamper.kotpub.inbox.InboxService
import net.matsudamper.kotpub.inbox.ReactionHandler
import net.matsudamper.kotpub.inbox.RecordedFallbackPublicKeys
import net.matsudamper.kotpub.inbox.UndoBoostHandler
import net.matsudamper.kotpub.inbox.UndoFollowHandler
import net.matsudamper.kotpub.inbox.UndoHandler
import net.matsudamper.kotpub.inbox.UndoReactionHandler
import net.matsudamper.kotpub.inbox.UpdateActorHandler
import net.matsudamper.kotpub.nodeinfo.NodeInfoEndpoint
import net.matsudamper.kotpub.nodeinfo.NodeInfoSoftware
import net.matsudamper.kotpub.note.FeaturedEndpoint
import net.matsudamper.kotpub.note.HttpRemoteNotes
import net.matsudamper.kotpub.note.HttpRemoteOutbox
import net.matsudamper.kotpub.note.NoteEndpoint
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.OutboxEndpoint
import net.matsudamper.kotpub.webfinger.HttpWebFingerLookup
import net.matsudamper.kotpub.webfinger.WebFingerEndpoint

internal class Federation(
    domain: String,
    accountService: AccountService,
    accountRepository: AccountRepository,
    followerRepository: FollowerRepository,
    followingRepository: FollowingRepository,
    deliveryQueueRepository: DeliveryQueueRepository,
    noteRepository: NoteRepository,
    notePinRepository: NotePinRepository,
    timelineRepository: TimelineRepository,
    timeline: TimelineService,
    accountEvents: AccountEvents,
    notificationRepository: NotificationRepository,
    remoteActorRepository: RemoteActorRepository,
    favouriteRepository: FavouriteRepository,
    likeRepository: LikeRepository,
    accountBoostRepository: AccountBoostRepository,
    stampRepository: StampRepository,
    boostRepository: BoostRepository,
    imageStorage: ImageStorage?,
    remoteIconProxySigningKey: ByteArray,
    openTelemetry: OpenTelemetry,
) : AutoCloseable {
    private val httpClient = OkHttpActivityPubClient()
    private val publicAddressHttpClient = PublicAddressHttpClient()
    private val directory = ActorDirectory(domain = domain, stored = AccountActorNames(accountService))
    private val remoteActors = HttpRemoteActors(client = httpClient, openTelemetry = openTelemetry)
    val noteImageUrls: NoteImageUrls = NoteImageUrls(domain)
    val noteShareUrls: NoteShareUrls = NoteShareUrls(domain)
    private val notes = RepositoryNoteStore(noteRepository = noteRepository, imageUrls = noteImageUrls)
    private val localActors = LocalActors(directory = directory, accountService = accountService)
    private val actorProfiles = AccountActorProfiles(accountService)
    private val actorAppearances = AccountActorAppearances(accountService = accountService, iconAvailable = imageStorage != null)
    private val webPages = FrontendWebPageUrls(domain)
    private val receivedNotifications = ReceivedNotifications(notificationRepository, accountEvents)
    private val favourites = RecordedFavourites(favouriteRepository, receivedNotifications)
    private val stamps = RecordedStamps(stampRepository)
    private val receivedNotes = ReceivedRemoteNotes(
        timelineRepository,
        remoteActors,
        remoteActorRepository,
        directory,
        timeline,
        Clock.systemUTC(),
        deleteRemoteNote = { actorUri, objectUri -> boostService.deleteRemoteNote(actorUri, objectUri) },
    )
    private val boosts = RecordedBoosts(boostRepository, receivedNotes, receivedNotifications, timeline, Clock.systemUTC())

    val deliveryQueueWorker: DeliveryQueueWorker =
        DeliveryQueueWorker(
            queue = deliveryQueueRepository,
            senders = localActors,
            delivery = HttpActivityDelivery(client = httpClient),
            retryPolicy = DeliveryRetryPolicy(initialDelay = 30.seconds, maxDelay = 24.hours, giveUpAfter = 7.days),
            clock = Clock.systemUTC(),
        )

    private val followers =
        AccountFollowerStore(
            followerRepository = followerRepository,
            onAcceptEnqueued = deliveryQueueWorker::wakeUp,
        )

    val notePostService: NotePostService =
        NotePostService(
            directory = directory,
            publisher = NotePublisher(notes, null),
            accountService = accountService,
            noteRepository = noteRepository,
            imageStorage = imageStorage,
            imageUrls = noteImageUrls,
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
            onPosted = { publicId ->
                val position = timelineRepository.localPosition(publicId.value)
                if (position != null) timeline.enqueue(position)
            },
        )

    private val remoteNotes = HttpRemoteNotes(httpClient)
    private val unsavedRemoteNotes = FetchingUnsavedRemoteNotes(
        timeline = timelineRepository,
        remoteNotes = remoteNotes,
        receivedNotes = receivedNotes,
        remoteActors = remoteActors,
        storedActors = remoteActorRepository,
        clock = Clock.systemUTC(),
    )

    val likeService: LikeService =
        LikeService(
            directory = directory,
            accountService = accountService,
            likes = likeRepository,
            unsavedNotes = unsavedRemoteNotes,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
        )

    val replyTargetService: ReplyTargetService =
        ReplyTargetService(
            domain = domain,
            timeline = timelineRepository,
            unsavedNotes = unsavedRemoteNotes,
        )

    val boostService: BoostService =
        BoostService(
            directory = directory,
            accountService = accountService,
            boosts = accountBoostRepository,
            unsavedNotes = unsavedRemoteNotes,
            followerRepository = followerRepository,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
        )

    val followDelivery = QueuedFollowDelivery(
        accountService,
        directory,
        remoteActors,
        remoteActorRepository,
        followingRepository,
        deliveryQueueRepository,
        Clock.systemUTC(),
        deliveryQueueWorker::wakeUp,
    )

    private val followingResponses = RecordedFollowingResponses(followingRepository, Clock.systemUTC(), openTelemetry)

    val profileUpdateService: ProfileUpdateService =
        ProfileUpdateService(
            senders = localActors,
            accountService = accountService,
            appearances = actorAppearances,
            profiles = actorProfiles,
            webPages = webPages,
            imageStorage = imageStorage,
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
        )

    val webFingerEndpoint: WebFingerEndpoint = WebFingerEndpoint(directory)

    /**
     * アカウントは CLI からしか作れないので、誰でも登録できるとは出さない
     */
    val nodeInfoEndpoint: NodeInfoEndpoint =
        NodeInfoEndpoint(
            domain = domain,
            software = NodeInfoSoftware(name = "kmp-activitypub", version = "0.1.0", repository = "https://github.com/matsudamper/KMP-ActivityPub"),
            openRegistrations = false,
            usageSource = RepositoryNodeInfoUsage(accountRepository = accountRepository, noteRepository = noteRepository),
        )

    private val kotpubRemoteUserLookup =
        KotpubRemoteUserLookup(webFinger = HttpWebFingerLookup(httpClient), remoteActors = remoteActors, storedActors = remoteActorRepository)

    val remoteUserLookup: RemoteUserLookup = kotpubRemoteUserLookup

    val remoteProfileRefreshService: RemoteProfileRefreshService =
        RemoteProfileRefreshService(
            remoteActors = remoteActorRepository,
            users = remoteUserLookup,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            clock = Clock.systemUTC(),
            sameHostInterval = RemoteProfileRefreshService.DEFAULT_SAME_HOST_INTERVAL,
            requestTimeout = RemoteProfileRefreshService.DEFAULT_REQUEST_TIMEOUT,
            maxConsecutiveFailuresPerHost = RemoteProfileRefreshService.DEFAULT_MAX_CONSECUTIVE_FAILURES_PER_HOST,
        )

    val remoteUserProfiles: RemoteUserProfiles = KotpubRemoteUserProfiles(
        users = kotpubRemoteUserLookup,
        outbox = HttpRemoteOutbox(httpClient),
    )

    val actorEndpoint: ActorEndpoint =
        ActorEndpoint(
            directory = directory,
            publicKeys = AccountActorPublicKeys(accountService),
            appearances = actorAppearances,
            profiles = actorProfiles,
            webPages = webPages,
        )

    /**
     * 画像の置き場が無ければ作らない。Actor にも `icon` を出さない
     */
    val actorIconEndpoint: ActorIconEndpoint? =
        imageStorage?.let { storage ->
            ActorIconEndpoint(directory = directory, icons = AccountActorIcons(accountService = accountService, imageStorage = storage))
        }

    /**
     * 画像の置き場が無ければ画像付きの投稿もできないので作らない
     */
    val noteImageFiles: NoteImageFiles? = imageStorage?.let { NoteImageFiles(noteRepository = noteRepository, imageStorage = it) }

    val uploadedNoteImageCleaner: UploadedNoteImageCleaner? =
        imageStorage?.let { UploadedNoteImageCleaner(noteRepository = noteRepository, imageStorage = it, clock = Clock.systemUTC()) }

    val profileIconUrls: ProfileIconUrls =
        ProfileIconUrls { username ->
            val iconVersion = actorAppearances.find(username).iconVersion
            if (iconVersion == null) null else directory.resolve(username)?.icon(iconVersion)
        }

    val remoteImageProxy: RemoteImageProxy =
        RemoteImageProxy(
            origin = "https://$domain",
            signingKey = remoteIconProxySigningKey,
            fetcher = PublicAddressRemoteImageFetcher(publicAddressHttpClient),
        )

    val linkPreviewService: LinkPreviewService =
        LinkPreviewService(
            fetcher = HttpLinkPreviewPageFetcher(publicAddressHttpClient),
            imageProxyUrlOf = remoteImageProxy::proxyUrlOf,
            clock = Clock.systemUTC(),
        )

    val followerEndpoint: FollowerEndpoint = FollowerEndpoint(directory = directory, followers = followers)

    val noteEndpoint: NoteEndpoint = NoteEndpoint(domain, notes, null)

    val outboxEndpoint: OutboxEndpoint = OutboxEndpoint(directory, notes, AccountBoostOutbox(accountBoostRepository), null)

    val featuredEndpoint: FeaturedEndpoint =
        FeaturedEndpoint(directory = directory, featuredNotes = RepositoryFeaturedNoteStore(pins = notePinRepository, notes = notes), webPages = null)

    /**
     * kotpub の InboxService.default に、他のサーバーからの投稿の受け取りとフォローへの応答の受け取りを足したもの
     */
    val inboxEndpoint: InboxEndpoint =
        InboxEndpoint(
            directory = directory,
            service = InboxService(
                verifier = HttpSignatureVerifier(
                    RecordedFallbackPublicKeys(
                        remote = remoteActors,
                        followers = followers,
                        favourites = favourites,
                        stamps = stamps,
                        boosts = boosts,
                    ),
                ),
                domainBlocks = NoInboxDomainBlocks,
                handlers = listOf(
                    FollowHandler(directory = directory, remoteActors = remoteActors, followers = followers),
                    ReactionHandler(
                        type = ReactionHandler.LIKE_TYPE,
                        domain = domain,
                        remoteActors = remoteActors,
                        favourites = favourites,
                        stamps = stamps,
                        earlyUndoneLikes = favourites,
                    ),
                    ReactionHandler(
                        type = ReactionHandler.EMOJI_REACT_TYPE,
                        domain = domain,
                        remoteActors = remoteActors,
                        favourites = favourites,
                        stamps = stamps,
                        earlyUndoneLikes = favourites,
                    ),
                    AnnounceHandler(
                        domain = domain,
                        remoteActors = remoteActors,
                        remoteNotes = remoteNotes,
                        boosts = boosts,
                        earlyUndone = favourites,
                    ),
                    UndoHandler(
                        reactions = UndoReactionHandler(
                            domain = domain,
                            favourites = favourites,
                            stamps = stamps,
                            earlyUndoneLikes = favourites,
                        ),
                        boosts = UndoBoostHandler(boosts = boosts, earlyUndone = favourites),
                        follows = UndoFollowHandler(directory = directory, followers = followers),
                    ),
                    UpdateActorHandler(followers),
                    CreateNoteHandler(receivedNotes),
                    FollowResponseHandler("Accept", followingResponses),
                    FollowResponseHandler("Reject", followingResponses),
                    DeleteHandler(
                        actors = DeleteActorHandler(followers = followers, favourites = favourites, stamps = stamps, boosts = boosts),
                        notes = receivedNotes,
                    ),
                ),
            ),
        )

    override fun close() {
        httpClient.close()
        publicAddressHttpClient.close()
    }
}
