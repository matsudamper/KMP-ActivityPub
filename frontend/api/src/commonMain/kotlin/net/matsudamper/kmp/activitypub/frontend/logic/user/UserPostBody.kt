package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * ホームとプロフィールで同じ見せ方にするため、投稿の中身はこの型でまとめて渡す
 *
 * @param linkUrls カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
 * @param favouriteCount 他のサーバーの投稿は数を受け取らないので null
 * @param stamps 多く押された順
 * @param likedByMe ログイン中のアカウントがいいねしているか。いいねできない投稿は null
 */
data class UserPostBody(
    val content: String,
    val publishedAt: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkUrls: List<String>,
    val images: List<UserPostImage>,
    val favouriteCount: Int?,
    val stamps: List<UserPostStamp>,
    val likedByMe: Boolean?,
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
