package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * ホームとプロフィールで同じ見せ方にするため、投稿の中身はこの型でまとめて渡す
 *
 * @param linkUrls カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
 * @param mentions [content] に書かれたメンション
 * @param favouriteCount 他のサーバーの投稿は数を受け取らないので null
 * @param boostCount 他のサーバーの投稿は数を受け取らないので null
 * @param stamps 先に押された絵文字の順
 * @param emojis [content] と [summary] に入るカスタム絵文字
 * @param shareUrl 共有するときに渡す投稿の URL。作れなければ null
 * @param likedByMe ログイン中のアカウントがいいねしているか。リアクションを付けていても true。いいねできない投稿は null
 * @param viewerReaction ログイン中のアカウントが付けたリアクション。付けていないか、絵文字の無いいいねなら null
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
    val viewerReaction: UserViewerReaction?,
    val boostedByMe: Boolean?,
) {
    /**
     * 絵文字の無いいいねを付けているか。いいねとリアクションは同じ投稿に 1 つしか付けられない
     */
    val plainLikedByMe: Boolean get() = likedByMe == true && viewerReaction == null
}

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
 * 絵文字の無いいいねに変える。付けていたリアクションは外れる
 */
fun UserPostBody.withLiked(liked: Boolean): UserPostBody = withLikeState(if (liked) UserLikeState.Liked else UserLikeState.None)

/**
 * いいねかリアクションを付け替える。1 つの投稿に付けられるのはどちらか 1 つ。
 * お気に入りの数は、自分の絵文字の無いいいねの分だけ増減させる。他の人の分は取り直すまで分からない
 */
fun UserPostBody.withLikeState(state: UserLikeState): UserPostBody {
    if (likedByMe == null || likeState() == state) return this
    val nowPlainLiked = state == UserLikeState.Liked
    return copy(
        likedByMe = state != UserLikeState.None,
        viewerReaction = (state as? UserLikeState.Reacted)?.reaction,
        favouriteCount = favouriteCount?.let { count ->
            when {
                plainLikedByMe && !nowPlainLiked -> (count - 1).coerceAtLeast(0)
                !plainLikedByMe && nowPlainLiked -> count + 1
                else -> count
            }
        },
    )
}

/**
 * いいねできない投稿は [UserLikeState.None]
 */
fun UserPostBody.likeState(): UserLikeState {
    val reaction = viewerReaction
    return when {
        reaction != null -> UserLikeState.Reacted(reaction)
        likedByMe == true -> UserLikeState.Liked
        else -> UserLikeState.None
    }
}

/**
 * ログイン中のアカウントが 1 つの投稿に付けているもの
 */
sealed interface UserLikeState {
    data object None : UserLikeState

    /** 絵文字の無いいいね */
    data object Liked : UserLikeState

    data class Reacted(val reaction: UserViewerReaction) : UserLikeState
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
