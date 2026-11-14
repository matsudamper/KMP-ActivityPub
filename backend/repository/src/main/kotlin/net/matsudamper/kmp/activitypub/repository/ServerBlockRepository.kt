package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * アカウントごとにブロックした他のサーバーのホスト
 */
interface ServerBlockRepository {
    /**
     * 新しくブロックした順に返す
     */
    fun hosts(accountId: AccountId): List<String>

    /**
     * 既にブロックしていれば何もしない
     */
    fun add(accountId: AccountId, host: String, createdAt: Instant)

    fun remove(accountId: AccountId, host: String)
}
