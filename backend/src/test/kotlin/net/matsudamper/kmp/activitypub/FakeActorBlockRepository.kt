package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class FakeActorBlockRepository : ActorBlockRepository {
    private val rows = mutableListOf<Row>()

    /** 積んだ Block。積んだ順 */
    val blockBodies = mutableListOf<String>()

    override fun list(accountId: AccountId): List<BlockedActor> = rows.filter { it.accountId == accountId }.map { it.actor }.reversed()

    override fun add(accountId: AccountId, actor: BlockedActor, createdAt: Instant) {
        val index = rows.indexOfFirst { it.accountId == accountId && it.actor.actorUri == actor.actorUri }.takeIf { it >= 0 }
        if (index == null) rows += Row(accountId, actor, null) else rows[index] = rows[index].copy(actor = actor)
    }

    override fun remove(accountId: AccountId, actorUri: String): String? {
        val removed = rows.firstOrNull { it.accountId == accountId && it.actor.actorUri == actorUri } ?: return null
        rows.remove(removed)
        return removed.blockActivityId
    }

    override fun findBlockActivityId(accountId: AccountId, actorUri: String): String? =
        rows.firstOrNull { it.accountId == accountId && it.actor.actorUri == actorUri }?.blockActivityId

    override fun recordBlockActivity(accountId: AccountId, actorUri: String, blockActivityId: String, inbox: String, body: String, now: Instant): Boolean {
        val index = rows.indexOfFirst { it.accountId == accountId && it.actor.actorUri == actorUri && it.blockActivityId == null }.takeIf { it >= 0 } ?: return false
        rows[index] = rows[index].copy(blockActivityId = blockActivityId)
        blockBodies += body
        return true
    }

    private data class Row(
        val accountId: AccountId,
        val actor: BlockedActor,
        val blockActivityId: String?,
    )
}
