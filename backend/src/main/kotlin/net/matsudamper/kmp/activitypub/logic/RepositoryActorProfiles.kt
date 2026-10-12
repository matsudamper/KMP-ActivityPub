package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kotpub.actor.ActorProfile
import net.matsudamper.kotpub.actor.StoredActorProfiles

/**
 * 毎回引き直す。持ち回すと、編集した後も古い表示名を返し続ける。
 */
class RepositoryActorProfiles(
    private val accounts: AccountRepository,
) : StoredActorProfiles {
    override fun find(username: String): ActorProfile {
        val account = accounts.findByUsername(username) ?: return ActorProfile.EMPTY
        return ActorProfile(displayName = account.displayName, summary = account.summary)
    }
}
