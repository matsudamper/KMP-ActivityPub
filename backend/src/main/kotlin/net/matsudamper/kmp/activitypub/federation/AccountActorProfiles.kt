package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.StoredActorProfiles

internal class AccountActorProfiles(
    private val accountService: AccountService,
) : StoredActorProfiles {
    override fun find(username: String): ActorProfile {
        val detail = accountService.profileDetail(username)
        return ActorProfile(
            displayName = accountService.account(username)?.displayName,
            summary = detail?.summary,
            likedPublic = detail?.likesPublic == true,
        )
    }
}
