package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 名前は大文字小文字を区別せずに一意にする。区別して持てると同じ名前を指す行が
 * 2 つ並び、どちらでログインしたのかが引き方で変わる。
 *
 * 追加の口は持たない。ユーザーはローカルで sqlite3 から直接入れる（README を参照）
 */
interface AccountRepository {
    fun findById(id: AccountId): Account?

    /**
     * ログインの照合に使う
     */
    fun findCredential(username: String): AccountCredential?
}

data class Account(
    val id: AccountId,
    val username: String,
    val createdAt: Instant,
)

/**
 * [Account] に入れないのは、ハッシュを画面や API に渡す経路を作らないため
 *
 * @param passwordHash `:backend:crypto` の `PasswordHash.encode` の形
 */
data class AccountCredential(
    val id: AccountId,
    val username: String,
    val passwordHash: String,
)
