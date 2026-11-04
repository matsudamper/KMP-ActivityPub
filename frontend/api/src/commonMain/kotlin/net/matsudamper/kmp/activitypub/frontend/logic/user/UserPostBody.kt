package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * ホームとプロフィールで同じ見せ方にするため、投稿の中身はこの型でまとめて渡す
 *
 * @param linkUrls カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
 * @param favouriteCount 他のサーバーの投稿は数を受け取らないので null
 */
data class UserPostBody(
    val content: String,
    val publishedAt: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkUrls: List<String>,
    val images: List<UserPostImage>,
    val favouriteCount: Int?,
)
