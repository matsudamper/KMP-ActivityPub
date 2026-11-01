package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.ProfileIconUrls
import net.matsudamper.kmp.activitypub.logic.ProfileUpdateService
import net.matsudamper.kmp.activitypub.logic.RemoteIconProxy
import net.matsudamper.kmp.activitypub.logic.UserProfileService
import net.matsudamper.kmp.activitypub.logic.UserSearchService
import net.matsudamper.kmp.activitypub.session.LoginThrottle

class DiContainer(
    val accountService: AccountService,
    val followingService: FollowingService,
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
     * DOMAIN が無いと Actor の URL を作れないので null
     */
    val profileIconUrls: ProfileIconUrls?,
    /**
     * DOMAIN が無いと中継 URL を作れないので null
     */
    val remoteIconProxy: RemoteIconProxy?,
)
