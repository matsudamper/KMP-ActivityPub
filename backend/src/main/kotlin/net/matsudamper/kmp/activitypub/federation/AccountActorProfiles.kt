package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.StoredActorProfiles

internal class AccountActorProfiles(
    private val accountService: AccountService,
) : StoredActorProfiles {
    override fun find(username: String): ActorProfile =
        ActorProfile(displayName = accountService.account(username)?.displayName, summary = null)
}
