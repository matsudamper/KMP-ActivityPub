package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserUnknownObjectTypesResult {
    /**
     * @param types 初めて見た新しい順
     * @param nextCursor 続きが無ければ null
     */
    data class Success(
        val types: List<UserUnknownObjectType>,
        val nextCursor: String?,
    ) : UserUnknownObjectTypesResult

    data object NotLoggedIn : UserUnknownObjectTypesResult
}

/**
 * 届いたものの中にあった、サーバーが読み方を知らない ActivityPub の type。型と出どころの組ごとに 1 件
 *
 * @param sourceUrl 型を見つけた object の id。無ければ包んでいたアクティビティの id
 * @param seenCount 同じ型と出どころを見た回数
 */
data class UserUnknownObjectType(
    val typeName: String,
    val sourceUrl: String,
    val seenCount: Int,
    val firstSeenAt: String,
    val lastSeenAt: String,
)
