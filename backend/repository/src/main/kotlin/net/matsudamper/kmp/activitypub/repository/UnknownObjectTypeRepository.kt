package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 届いたものの中にあった、読み方を知らない ActivityPub の type を管理画面で見る
 */
interface UnknownObjectTypeRepository {
    /**
     * 同じ型と出どころの組は 1 行にまとめ、見た回数と最後に見た時刻を進める
     */
    fun record(
        typeNames: List<String>,
        sourceUrl: String,
        seenAt: Instant,
    )

    /**
     * 初めて見た新しい順
     *
     * @param beforeId この id より前に初めて見た分を返す。最初のページなら null
     */
    fun page(
        beforeId: Long?,
        limit: Int,
    ): List<UnknownObjectType>
}

/**
 * @param sourceUrl 型を見つけた object の id。無ければ包んでいたアクティビティの id
 */
data class UnknownObjectType(
    val id: Long,
    val typeName: String,
    val sourceUrl: String,
    val seenCount: Int,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)
