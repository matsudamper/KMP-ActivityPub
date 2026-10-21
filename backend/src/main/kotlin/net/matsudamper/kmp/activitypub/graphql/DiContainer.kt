package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.NotePostService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.session.LoginThrottle
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService

class DiContainer(
    val accountService: AccountService,
    val passkeyService: PasskeyService,
    val loginThrottle: LoginThrottle,
    val homeTimelineService: HomeTimelineService,
    /**
     * DOMAIN が無いと配れないので null
     */
    val notePostService: NotePostService?,
)
