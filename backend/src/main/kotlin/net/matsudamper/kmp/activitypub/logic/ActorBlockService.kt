package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * アカウントごとに他のサーバーのアクターをブロックする。ブロックしたアクターの投稿・ブースト・通知は、読むときに除く
 */
class ActorBlockService(
    private val accounts: AccountRepository,
    private val actorBlocks: ActorBlockRepository,
    private val profiles: UserProfileService,
    private val clock: Clock,
) {
    /**
     * 新しくブロックした順
     */
    fun list(accountId: AccountId): List<BlockedActor> = actorBlocks.list(accountId)

    /**
     * 相手のサーバーにプロフィールを引きに行くことがある
     *
     * @param acct プロフィールを開くときと同じ username@host
     */
    suspend fun block(accountId: AccountId?, acct: String): BlockActorResult {
        if (accountId == null || accounts.findById(accountId) == null) return BlockActorResult.NotLoggedIn
        val user = profiles.profile(acct, accountId)?.user ?: return BlockActorResult.NotFound
        val actorUri = user.actorUri ?: return BlockActorResult.LocalAccount
        val actor = BlockedActor(actorUri = actorUri, acct = "${user.username}@${user.host}")
        actorBlocks.add(accountId, actor, clock.instant())
        return BlockActorResult.Blocked(actor)
    }

    /**
     * ブロックしていなくても成功する
     */
    fun unblock(accountId: AccountId?, actorUri: String): UnblockActorResult {
        if (accountId == null || accounts.findById(accountId) == null) return UnblockActorResult.NotLoggedIn
        actorBlocks.remove(accountId, actorUri)
        return UnblockActorResult.Unblocked
    }
}

sealed interface BlockActorResult {
    data class Blocked(val actor: BlockedActor) : BlockActorResult
    data object NotLoggedIn : BlockActorResult
    data object NotFound : BlockActorResult

    /**
     * このサーバーのユーザーの投稿は読むときに除けないので、ブロックできない
     */
    data object LocalAccount : BlockActorResult
}

sealed interface UnblockActorResult {
    data object Unblocked : UnblockActorResult
    data object NotLoggedIn : UnblockActorResult
}
