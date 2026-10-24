package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ユーザーの追加は sqlite3 から直接行う
 */
interface AccountRepository {
    fun findById(id: AccountId): Account?

    fun findByUsername(username: String): Account?

    /**
     * 名前順に並べ、[afterUsername] より後ろを [limit] 件まで返す
     */
    fun searchByUsername(
        keyword: String,
        afterUsername: String?,
        limit: Int,
    ): List<Account>

    fun findCredential(username: String): AccountCredential?

    fun findPublicKeyPem(username: String): String?

    fun findPrivateKeyPem(username: String): String?

    fun hasPassword(id: AccountId): Boolean

    /**
     * パスキーが無いとログインできなくなるので、パスキーがあるときだけ消す
     *
     * @return パスキーかパスワードが無ければ消さずに false
     */
    fun deletePasswordIfPasskeyExists(id: AccountId): Boolean
}

/**
 * @param displayName 未設定なら null
 */
data class Account(
    val id: AccountId,
    val username: String,
    val createdAt: Instant,
    val displayName: String?,
)

/**
 * ハッシュを API に渡す経路を作らないため [Account] と分ける
 */
data class AccountCredential(
    val id: AccountId,
    val username: String,
    val passwordHash: String,
    val passwordHashType: String,
)
