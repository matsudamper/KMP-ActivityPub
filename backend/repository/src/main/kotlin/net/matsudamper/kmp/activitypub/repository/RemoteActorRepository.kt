package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * フォローなどで受け取った他のサーバーのアクター
 */
interface RemoteActorRepository {
    fun save(actor: RemoteActorRecord, updatedAt: Instant)

    fun findByAcct(username: String, host: String): RemoteActorSearchRecord?

    /**
     * ユーザー名か表示名に [keyword] を含むアクターを、ユーザー名順に [after] より後ろから [limit] 件まで返す。
     * ユーザー名が分からないアクターは acct を作れないので返さない
     */
    fun searchByName(
        keyword: String,
        after: RemoteActorSearchCursor?,
        limit: Int,
    ): List<RemoteActorSearchRecord>
}

/**
 * ユーザー名が同じアクターは別のサーバーにいくらでもいるので、アクター URL も並び順に使う
 */
data class RemoteActorSearchCursor(
    val preferredUsername: String,
    val actorUri: String,
)

data class RemoteActorSearchRecord(
    val actorUri: String,
    val preferredUsername: String,
    val displayName: String?,
    val iconUrl: String?,
    val updatedAt: Instant,
)
