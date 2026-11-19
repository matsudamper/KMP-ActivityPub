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
import net.matsudamper.kmp.activitypub.logic.BlockLists
import net.matsudamper.kmp.activitypub.logic.BoostService
import net.matsudamper.kmp.activitypub.logic.CustomEmojiService
import net.matsudamper.kmp.activitypub.logic.CustomEmojiUrls
import net.matsudamper.kmp.activitypub.logic.LikeService
import net.matsudamper.kmp.activitypub.logic.LinkPreviewService
import net.matsudamper.kmp.activitypub.logic.LocalNoteEmojiObjects
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.NoteShareUrls
import net.matsudamper.kmp.activitypub.logic.NotificationService
import net.matsudamper.kmp.activitypub.logic.PollService
import net.matsudamper.kmp.activitypub.logic.PostCountService
import net.matsudamper.kmp.activitypub.logic.PostRepliesService
import net.matsudamper.kmp.activitypub.logic.ProfileHeaderUrls
import net.matsudamper.kmp.activitypub.logic.ProfileIconUrls
import net.matsudamper.kmp.activitypub.logic.ProfileUpdateService
import net.matsudamper.kmp.activitypub.logic.RemoteImageProxy
import net.matsudamper.kmp.activitypub.logic.RemoteLikedCollections
import net.matsudamper.kmp.activitypub.logic.RemoteProfileRefreshService
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfiles
import net.matsudamper.kmp.activitypub.logic.ReplyTargetService
import net.matsudamper.kmp.activitypub.logic.UnsavedRemoteNotes
import net.matsudamper.kmp.activitypub.logic.UploadedNoteImageCleaner
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRepository
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.InstanceActorKeyRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.NoteCountRepository
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.PollRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorEndpoint
import net.matsudamper.kotpub.actor.ActorHeaderEndpoint
import net.matsudamper.kotpub.actor.ActorIconEndpoint
import net.matsudamper.kotpub.actor.HttpRemoteActors
import net.matsudamper.kotpub.actor.InstanceActorEndpoint
import net.matsudamper.kotpub.delivery.HttpActivityDelivery
import net.matsudamper.kotpub.follower.FollowerEndpoint
import net.matsudamper.kotpub.follower.FollowingEndpoint
import net.matsudamper.kotpub.httpsignature.HttpSignatureSigner
import net.matsudamper.kotpub.httpsignature.HttpSignatureVerifier
import net.matsudamper.kotpub.httpsignature.SignedFetchActivityPubHttpClient
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
import net.matsudamper.kotpub.inbox.UpdateHandler
import net.matsudamper.kotpub.like.LikedEndpoint
import net.matsudamper.kotpub.nodeinfo.NodeInfoEndpoint
import net.matsudamper.kotpub.nodeinfo.NodeInfoSoftware
import net.matsudamper.kotpub.note.FeaturedEndpoint
import net.matsudamper.kotpub.note.HttpRemoteNoteCounts
import net.matsudamper.kotpub.note.HttpRemoteNotes
import net.matsudamper.kotpub.note.HttpRemoteOutbox
import net.matsudamper.kotpub.note.NoteEndpoint
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.OutboxEndpoint
import net.matsudamper.kotpub.quote.QuoteAuthorizationEndpoint
import net.matsudamper.kotpub.quote.QuoteRequestHandler
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
    private val timelineRepository: TimelineRepository,
    timeline: TimelineService,
    notificationService: NotificationService,
    remoteActorRepository: RemoteActorRepository,
    favouriteRepository: FavouriteRepository,
    likeRepository: LikeRepository,
    accountBoostRepository: AccountBoostRepository,
    stampRepository: StampRepository,
    boostRepository: BoostRepository,
    customEmojiRepository: CustomEmojiRepository,
    blockLists: BlockLists,
    noteCountRepository: NoteCountRepository,
    pollRepository: PollRepository,
    imageStorage: ImageStorage?,
    instanceActorKeyRepository: InstanceActorKeyRepository,
    remoteIconProxySigningKey: ByteArray,
    openTelemetry: OpenTelemetry,
) : AutoCloseable {
    private val instanceActor = storedInstanceActor(domain = domain, keys = instanceActorKeyRepository)
    private val httpClient = SignedFetchActivityPubHttpClient(
        client = OkHttpActivityPubClient(),
        signingActor = instanceActor,
        signer = HttpSignatureSigner(),
    )
    private val publicAddressHttpClient = PublicAddressHttpClient()
    private val directory = ActorDirectory(domain = domain, stored = AccountActorNames(accountService))
    private val remoteActors = HttpRemoteActors(client = httpClient, openTelemetry = openTelemetry)
    val noteImageUrls: NoteImageUrls = NoteImageUrls(domain)
    val noteShareUrls: NoteShareUrls = NoteShareUrls(domain)
    val customEmojiUrls: CustomEmojiUrls = CustomEmojiUrls(domain)
    val customEmojiService: CustomEmojiService = CustomEmojiService(
        customEmojis = customEmojiRepository,
        imageStorage = imageStorage,
        clock = Clock.systemUTC(),
    )
    private val noteEmojiObjects = LocalNoteEmojiObjects(customEmojis = customEmojiService, customEmojiUrls = customEmojiUrls)
    private val notes = RepositoryNoteStore(
        noteRepository = noteRepository,
        imageUrls = noteImageUrls,
        emojiObjects = noteEmojiObjects,
        polls = pollRepository,
    )
    private val localActors = LocalActors(directory = directory, accountService = accountService)
    private val actorProfiles = AccountActorProfiles(accountService)
    private val actorAppearances = AccountActorAppearances(accountService = accountService, imageAvailable = imageStorage != null)
    private val webPages = FrontendWebPageUrls(domain)
    private val inboxBlocks = InboxBlocks(accountRepository, noteRepository, blockLists)
    private val favourites = RecordedFavourites(favouriteRepository, notificationService, inboxBlocks, timeline)
    private val stamps = RecordedStamps(stampRepository, notificationService, inboxBlocks, timeline)
    private val receivedNotes = ReceivedRemoteNotes(
        timelineRepository,
        remoteActors,
        remoteActorRepository,
        directory,
        inboxBlocks,
        timeline,
        notificationService,
        Clock.systemUTC(),
        deleteRemoteNote = { actorUri, objectUri -> boostService.deleteRemoteNote(actorUri, objectUri) },
    )
    private val boosts = RecordedBoosts(boostRepository, receivedNotes, notificationService, inboxBlocks, timeline, Clock.systemUTC())

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
            blocks = inboxBlocks,
            notifications = notificationService,
            onAcceptEnqueued = deliveryQueueWorker::wakeUp,
        )

    private val kotpubRemoteUserLookup =
        KotpubRemoteUserLookup(webFinger = HttpWebFingerLookup(httpClient), remoteActors = remoteActors, storedActors = remoteActorRepository)

    val remoteUserLookup: RemoteUserLookup = kotpubRemoteUserLookup

    val notePostService: NotePostService =
        NotePostService(
            directory = directory,
            publisher = NotePublisher(notes, null),
            accountService = accountService,
            noteRepository = noteRepository,
            polls = pollRepository,
            imageStorage = imageStorage,
            imageUrls = noteImageUrls,
            emojiObjects = noteEmojiObjects,
            followerRepository = followerRepository,
            remoteActors = remoteActorRepository,
            remoteUsers = kotpubRemoteUserLookup,
            deliveryQueueRepository = deliveryQueueRepository,
            notifications = notificationService,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
            onPosted = { publicId ->
                val position = timelineRepository.localPosition(publicId.value)
                if (position != null) timeline.enqueue(position)
            },
            onEdited = timeline::publishLocalNoteChanged,
        )

    private val remoteNotes = HttpRemoteNotes(httpClient)
    val unsavedRemoteNotes: UnsavedRemoteNotes = FetchingUnsavedRemoteNotes(
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
            customEmojis = customEmojiService,
            customEmojiUrls = customEmojiUrls,
            unsavedNotes = unsavedRemoteNotes,
            notifications = notificationService,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
            onLocalReactionsChanged = { member -> timeline.publishUpdated(listOf(member)) },
        )

    val replyTargetService: ReplyTargetService =
        ReplyTargetService(
            domain = domain,
            timeline = timelineRepository,
            unsavedNotes = unsavedRemoteNotes,
        )

    val postRepliesService: PostRepliesService =
        PostRepliesService(
            timeline = timelineRepository,
            noteShareUrls = noteShareUrls,
            blockLists = blockLists,
        )

    val pollService: PollService = PollService(pollRepository)

    val postCountService: PostCountService =
        PostCountService(
            domain = domain,
            counts = noteCountRepository,
            remoteCounts = KotpubRemoteNoteCounts(HttpRemoteNoteCounts(httpClient)),
            clock = Clock.systemUTC(),
        )

    val boostService: BoostService =
        BoostService(
            directory = directory,
            accountService = accountService,
            boosts = accountBoostRepository,
            unsavedNotes = unsavedRemoteNotes,
            followerRepository = followerRepository,
            notifications = notificationService,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
        )

    val followDelivery = QueuedFollowDelivery(
        accountService,
        directory,
        remoteActors,
        remoteActorRepository,
        followingRepository,
        blockLists,
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

    val webFingerEndpoint: WebFingerEndpoint = WebFingerEndpoint(directory = directory, instanceActor = instanceActor.urls)

    val instanceActorEndpoint: InstanceActorEndpoint = InstanceActorEndpoint(instanceActor)

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

    private val remoteOutbox = HttpRemoteOutbox(httpClient)

    val remoteUserProfiles: RemoteUserProfiles = KotpubRemoteUserProfiles(
        users = kotpubRemoteUserLookup,
        outbox = remoteOutbox,
    )

    val remoteLikedCollections: RemoteLikedCollections = KotpubRemoteLikedCollections(remoteOutbox)

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
     * 画像の置き場が無ければ作らない。Actor にも `image` を出さない
     */
    val actorHeaderEndpoint: ActorHeaderEndpoint? =
        imageStorage?.let { storage ->
            ActorHeaderEndpoint(directory = directory, headers = AccountActorHeaders(accountService = accountService, imageStorage = storage))
        }

    /**
     * 画像の置き場が無ければ画像付きの投稿もできないので作らない
     */
    val noteImageFiles: NoteImageFiles? = imageStorage?.let { NoteImageFiles(noteRepository = noteRepository, imageStorage = it) }

    /**
     * 画像の置き場が無ければカスタム絵文字も置けないので作らない
     */
    val customEmojiFiles: CustomEmojiFiles? = imageStorage?.let { CustomEmojiFiles(customEmojiService = customEmojiService, imageStorage = it) }

    val uploadedNoteImageCleaner: UploadedNoteImageCleaner? =
        imageStorage?.let { UploadedNoteImageCleaner(noteRepository = noteRepository, imageStorage = it, clock = Clock.systemUTC()) }

    val profileIconUrls: ProfileIconUrls =
        ProfileIconUrls { username ->
            val iconVersion = actorAppearances.find(username).iconVersion
            if (iconVersion == null) null else directory.resolve(username)?.icon(iconVersion)
        }

    val profileHeaderUrls: ProfileHeaderUrls =
        ProfileHeaderUrls { username ->
            val headerVersion = actorAppearances.find(username).headerVersion
            if (headerVersion == null) null else directory.resolve(username)?.header(headerVersion)
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

    val followingEndpoint: FollowingEndpoint =
        FollowingEndpoint(directory = directory, following = AccountFollowingStore(followingRepository))

    val noteEndpoint: NoteEndpoint = NoteEndpoint(domain, notes, null)

    /**
     * ブラウザで開いたときに見せる、フロントエンドの投稿の画面のパス。このサーバーの投稿でなければ null
     */
    fun notePagePath(publicId: String): String? =
        timelineRepository.localPosition(publicId)?.let { "/posts/${it.member}" }

    private val quoteApprovals =
        QueuedQuoteApprovals(
            localActors = localActors,
            accountRepository = accountRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            blocks = inboxBlocks,
            clock = Clock.systemUTC(),
            onEnqueued = deliveryQueueWorker::wakeUp,
        )

    val quoteAuthorizationEndpoint: QuoteAuthorizationEndpoint =
        QuoteAuthorizationEndpoint(domain = domain, notes = notes, approvals = quoteApprovals)

    val outboxEndpoint: OutboxEndpoint = OutboxEndpoint(directory, notes, AccountBoostOutbox(accountBoostRepository), null)

    val featuredEndpoint: FeaturedEndpoint =
        FeaturedEndpoint(directory = directory, featuredNotes = RepositoryFeaturedNoteStore(pins = notePinRepository, notes = notes), webPages = null)

    val likedEndpoint: LikedEndpoint =
        LikedEndpoint(directory = directory, profiles = actorProfiles, likedObjects = RepositoryLikedObjectStore(domain = domain, likes = likeRepository))

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
                    QuoteRequestHandler(
                        domain = domain,
                        notes = notes,
                        remoteActors = remoteActors,
                        remoteNotes = remoteNotes,
                        approvals = quoteApprovals,
                    ),
                    UpdateHandler(actors = UpdateActorHandler(followers), notes = receivedNotes),
                    CreateNoteHandler(
                        notes = receivedNotes,
                        pollVotes = RecordedPollVotes(domain = domain, polls = pollRepository, blocks = inboxBlocks, timeline = timeline, clock = Clock.systemUTC()),
                    ),
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
