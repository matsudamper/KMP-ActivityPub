package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kotpub.note.OutboxBoostStore
import net.matsudamper.kotpub.note.OutboxPosition
import net.matsudamper.kotpub.note.StoredBoost

/**
 * このサーバーのアカウントはまだブーストできないので、outbox に並べるブーストは無い
 */
internal object NoOutboxBoosts : OutboxBoostStore {
    override fun list(username: String, after: OutboxPosition?, limit: Int): List<StoredBoost> = listOf()

    override fun count(username: String): Long = 0
}
