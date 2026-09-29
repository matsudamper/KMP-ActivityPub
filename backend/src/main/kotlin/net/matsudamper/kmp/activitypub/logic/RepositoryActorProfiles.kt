package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.activitypub.actor.ActorProfile
import net.matsudamper.activitypub.actor.StoredActorProfiles
import net.matsudamper.kmp.activitypub.repository.AccountRepository

/**
 * ActivityPub 側の [StoredActorProfiles] を DB に繋ぐ。
 *
 * 毎回引き直す。持ち回すと、編集した後も古い表示名を返し続ける。
 */
class RepositoryActorProfiles(
    private val accounts: AccountRepository,
) : StoredActorProfiles {
    override fun find(username: String): ActorProfile {
        val account = accounts.findByUsername(username) ?: return ActorProfile.EMPTY
        return ActorProfile(
            displayName = account.displayName,
            // kotpub は summary が null だと RSS 配信用の既定の文言を出す。空文字なら何も出ない
            summary = account.summary.orEmpty(),
        )
    }
}
