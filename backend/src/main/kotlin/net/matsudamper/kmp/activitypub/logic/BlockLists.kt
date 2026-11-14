package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.ServerBlockRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.AccountEvent

/**
 * アカウントごとにブロックしたサーバーとアクターをまとめて読む
 */
class BlockLists(
    private val serverBlocks: ServerBlockRepository,
    private val actorBlocks: ActorBlockRepository,
) {
    fun of(accountId: AccountId): BlockList = BlockList(
        hosts = serverBlocks.hosts(accountId).toSet(),
        actorUris = actorBlocks.list(accountId).map { it.actorUri }.toSet(),
    )

    fun hides(accountId: AccountId, event: AccountEvent): Boolean = when (event) {
        is AccountEvent.PostArrived -> of(accountId).hides(event.post)
        is AccountEvent.NotificationArrived -> of(accountId).hidesActor(event.notification.actor.actorUri)
        is AccountEvent.PostsDeleted -> false
    }
}
