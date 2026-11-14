package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.BoostService
import net.matsudamper.kmp.activitypub.logic.FollowListService
import net.matsudamper.kmp.activitypub.logic.FollowingGroupService
import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kmp.activitypub.logic.LikeService
import net.matsudamper.kmp.activitypub.logic.LinkPreviewService
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.logic.NotePinService
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.NoteShareUrls
import net.matsudamper.kmp.activitypub.logic.NotificationService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.ProfileIconUrls
import net.matsudamper.kmp.activitypub.logic.ProfileUpdateService
import net.matsudamper.kmp.activitypub.logic.RemoteImageProxy
import net.matsudamper.kmp.activitypub.logic.RemoteProfileRefreshService
import net.matsudamper.kmp.activitypub.logic.UserProfileService
import net.matsudamper.kmp.activitypub.logic.UserSearchService
import net.matsudamper.kmp.activitypub.logic.ViewablePostService
import net.matsudamper.kmp.activitypub.session.LoginThrottle
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.AccountEvents
import net.matsudamper.kmp.activitypub.timeline.TimelineService

class DiContainer(
    val timelineService: TimelineService,
    val accountEvents: AccountEvents,
    val notificationService: NotificationService,
    val accountService: AccountService,
    val followingService: FollowingService,
    val followingGroupService: FollowingGroupService,
    val followListService: FollowListService,
    val userSearchService: UserSearchService,
    val userProfileService: UserProfileService,
    val viewablePostService: ViewablePostService,
    val notePinService: NotePinService,
    /**
     * ENABLE_PASSKEY が false なら null
     */
    val passkeyService: PasskeyService?,
    val loginThrottle: LoginThrottle,
    val adminAccountIds: Set<AccountId>,
    /**
     * DOMAIN が無いと配れないので null
     */
    val notePostService: NotePostService?,
    /**
     * DOMAIN が無いと配れないので null
     */
    val profileUpdateService: ProfileUpdateService?,
    /**
     * DOMAIN が無いと配れないので null
     */
    val likeService: LikeService?,
    val boostService: BoostService?,
    /**
     * DOMAIN が無いと Actor の URL を作れないので null
     */
    val profileIconUrls: ProfileIconUrls?,
    /**
     * DOMAIN が無いと画像の URL を作れないので null
     */
    val noteImageUrls: NoteImageUrls?,
    /**
     * DOMAIN が無いとこのサーバーの投稿の URL を作れないので null
     */
    val noteShareUrls: NoteShareUrls?,
    /**
     * DOMAIN が無いと中継 URL を作れないので null
     */
    val remoteImageProxy: RemoteImageProxy?,
    /**
     * DOMAIN が無いと OGP 画像の中継 URL を作れないので null
     */
    val linkPreviewService: LinkPreviewService?,
    /**
     * DOMAIN が無いと相手のサーバーに取りに行けないので null
     */
    val remoteProfileRefreshService: RemoteProfileRefreshService?,
)
