package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * それぞれ [UserPostBody] の同じ名前の項目と同じ意味
 */
data class UserPostCounts(
    val replyCount: Int,
    val favouriteCount: Int?,
    val boostCount: Int?,
)

/**
 * 取れなかったときは前の数のまま見せればよいので、失敗の理由は分けない
 */
fun UserPostBody.withCounts(counts: UserPostCounts): UserPostBody = copy(
    replyCount = counts.replyCount,
    favouriteCount = counts.favouriteCount,
    boostCount = counts.boostCount,
)
