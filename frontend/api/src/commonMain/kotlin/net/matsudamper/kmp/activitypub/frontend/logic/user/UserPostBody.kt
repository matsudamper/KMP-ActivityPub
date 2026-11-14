package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * ホームとプロフィールで同じ見せ方にするため、投稿の中身はこの型でまとめて渡す
 *
 * @param linkUrls カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
 * @param mentions [content] に書かれたメンション
 * @param favouriteCount 他のサーバーの投稿は数を受け取らないので null
 * @param boostCount 他のサーバーの投稿は数を受け取らないので null
 * @param stamps 多く押された順
 * @param emojis [content] と [summary] に入るカスタム絵文字
 * @param shareUrl 共有するときに渡す投稿の URL。作れなければ null
 * @param likedByMe ログイン中のアカウントがいいねしているか。いいねできない投稿は null
 * @param boostedByMe ログイン中のアカウントがブーストしているか。ブーストできない投稿は null
 */
data class UserPostBody(
    val content: String,
    val publishedAt: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkUrls: List<String>,
    val mentions: List<UserPostMention>,
    val images: List<UserPostImage>,
    val favouriteCount: Int?,
    val boostCount: Int?,
    val stamps: List<UserPostStamp>,
    val emojis: List<UserCustomEmoji>,
    val shareUrl: String?,
    val likedByMe: Boolean?,
    val boostedByMe: Boolean?,
)

/**
 * @param start [UserPostBody.content] でメンションが始まる位置
 * @param end メンションが終わる位置。この位置の文字は含まない
 * @param acct プロフィールを開くときの `user@host`
 */
data class UserPostMention(
    val start: Int,
    val end: Int,
    val acct: String,
)

/**
 * @param imageUrl カスタム絵文字の画像。Unicode の絵文字か、画像が無ければ null で、[emoji] の文字で見せる
 */
data class UserPostStamp(
    val emoji: String,
    val imageUrl: String?,
    val count: Int,
)

/**
 * 数は自分のいいねの分だけ増減させる。他の人のいいねは取り直すまで分からない
 */
fun UserPostBody.withLiked(liked: Boolean): UserPostBody {
    if (likedByMe == liked) return this
    return copy(
        likedByMe = liked,
        favouriteCount = favouriteCount?.let { count -> if (liked) count + 1 else (count - 1).coerceAtLeast(0) },
    )
}

/**
 * 数は自分のブーストの分だけ増減させる。他の人のブーストは取り直すまで分からない
 */
fun UserPostBody.withBoosted(boosted: Boolean): UserPostBody {
    if (boostedByMe == boosted) return this
    return copy(
        boostedByMe = boosted,
        boostCount = boostCount?.let { count -> if (boosted) count + 1 else (count - 1).coerceAtLeast(0) },
    )
}
