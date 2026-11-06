package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * ホームとプロフィールで同じ見せ方にするため、投稿の中身はこの型でまとめて渡す
 *
 * @param linkUrls カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
 * @param favouriteCount 他のサーバーの投稿は数を受け取らないので null
 * @param stamps 多く押された順
 * @param shareUrl 共有するときに渡す投稿の URL。作れなければ null
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
    val shareUrl: String?,
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
