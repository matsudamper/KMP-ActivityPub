package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.AccountBoostPosition
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kotpub.activity.BoostAudience
import net.matsudamper.kotpub.note.OutboxBoostStore
import net.matsudamper.kotpub.note.OutboxPosition
import net.matsudamper.kotpub.note.StoredBoost

/**
 * outbox に並べる、このサーバーのアカウントが付けたブースト
 */
internal class AccountBoostOutbox(
    private val boosts: AccountBoostRepository,
) : OutboxBoostStore {
    override fun list(username: String, after: OutboxPosition?, limit: Int): List<StoredBoost> =
        boosts.listForOutbox(
            username = username,
            after = after?.let { AccountBoostPosition(boostedAt = it.at, announceId = it.key) },
            limit = limit,
        ).map { boost ->
            StoredBoost(
                announceId = boost.announceId,
                objectUri = boost.objectUri,
                objectAuthor = boost.objectAuthor,
                boostedAt = boost.boostedAt,
                audience = if (boost.unlisted) BoostAudience.UNLISTED else BoostAudience.PUBLIC,
            )
        }

    override fun count(username: String): Long = boosts.countForOutbox(username)
}
