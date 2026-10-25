package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.ProfileUpdateService
import net.matsudamper.kmp.activitypub.logic.UserSearchService
import net.matsudamper.kmp.activitypub.session.LoginThrottle

class DiContainer(
    val accountService: AccountService,
    val userSearchService: UserSearchService,
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
)
