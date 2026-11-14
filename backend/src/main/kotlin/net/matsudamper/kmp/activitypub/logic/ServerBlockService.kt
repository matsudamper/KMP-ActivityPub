package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.ServerBlockRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.AccountEvent

/**
 * アカウントごとに他のサーバーをブロックする。ブロックしたサーバーのアクターの投稿・ブースト・通知は、読むときに除く
 */
class ServerBlockService(
    private val accounts: AccountRepository,
    private val serverBlocks: ServerBlockRepository,
    private val clock: Clock,
) {
    /**
     * 新しくブロックした順
     */
    fun hosts(accountId: AccountId): List<String> = serverBlocks.hosts(accountId)

    fun blockedServers(accountId: AccountId): BlockedServers = BlockedServers(serverBlocks.hosts(accountId).toSet())

    /**
     * @param server ホスト名か、その中の URL やユーザーのアカウント名
     */
    fun block(accountId: AccountId?, server: String): BlockServerResult {
        if (accountId == null || accounts.findById(accountId) == null) return BlockServerResult.NotLoggedIn
        val host = ServerHost.parse(server) ?: return BlockServerResult.InvalidServer
        serverBlocks.add(accountId, host, clock.instant())
        return BlockServerResult.Blocked(host)
    }

    /**
     * ブロックしていなくても成功する
     */
    fun unblock(accountId: AccountId?, host: String): UnblockServerResult {
        if (accountId == null || accounts.findById(accountId) == null) return UnblockServerResult.NotLoggedIn
        serverBlocks.remove(accountId, host)
        return UnblockServerResult.Unblocked
    }

    fun hides(accountId: AccountId, event: AccountEvent): Boolean = when (event) {
        is AccountEvent.PostArrived -> blockedServers(accountId).hides(event.post)
        is AccountEvent.NotificationArrived -> blockedServers(accountId).hidesActor(event.notification.actor.actorUri)
        is AccountEvent.PostsDeleted -> false
    }
}

sealed interface BlockServerResult {
    /**
     * @param host 入力から取り出して保存したホスト
     */
    data class Blocked(val host: String) : BlockServerResult
    data object NotLoggedIn : BlockServerResult
    data object InvalidServer : BlockServerResult
}

sealed interface UnblockServerResult {
    data object Unblocked : UnblockServerResult
    data object NotLoggedIn : UnblockServerResult
}
