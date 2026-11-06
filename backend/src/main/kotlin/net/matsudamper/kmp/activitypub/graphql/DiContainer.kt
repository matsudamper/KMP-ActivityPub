package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowListService
import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kmp.activitypub.logic.LikeService
import net.matsudamper.kmp.activitypub.logic.LinkPreviewService
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.ProfileIconUrls
import net.matsudamper.kmp.activitypub.logic.ProfileUpdateService
import net.matsudamper.kmp.activitypub.logic.RemoteImageProxy
import net.matsudamper.kmp.activitypub.logic.UserProfileService
import net.matsudamper.kmp.activitypub.logic.UserSearchService
import net.matsudamper.kmp.activitypub.session.LoginThrottle
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineArrivals
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService

class DiContainer(
    val homeTimelineService: HomeTimelineService,
    val homeTimelineArrivals: HomeTimelineArrivals,
    val accountService: AccountService,
    val followingService: FollowingService,
    val followListService: FollowListService,
    val userSearchService: UserSearchService,
    val userProfileService: UserProfileService,
    /**
     * ENABLE_PASSKEY が false なら null
     */
    val passkeyService: PasskeyService?,
    val loginThrottle: LoginThrottle,
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
    /**
     * DOMAIN が無いと Actor の URL を作れないので null
     */
    val profileIconUrls: ProfileIconUrls?,
    /**
     * DOMAIN が無いと画像の URL を作れないので null
     */
    val noteImageUrls: NoteImageUrls?,
    /**
     * DOMAIN が無いと中継 URL を作れないので null
     */
    val remoteImageProxy: RemoteImageProxy?,
    /**
     * DOMAIN が無いと OGP 画像の中継 URL を作れないので null
     */
    val linkPreviewService: LinkPreviewService?,
)
