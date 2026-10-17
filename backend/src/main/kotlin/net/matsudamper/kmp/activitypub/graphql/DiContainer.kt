package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.session.LoginThrottle

class DiContainer(
    val accountService: AccountService,
    val passkeyService: PasskeyService,
    val loginThrottle: LoginThrottle,
)
