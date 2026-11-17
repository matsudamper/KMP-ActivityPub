package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * ホームとプロフィールで同じ見せ方にするため、投稿の中身はこの型でまとめて渡す
 *
 * @param linkUrls カードにするリンク。OGP は [UserApi.linkPreviews] で別に取る
 * @param language BCP 47 の言語タグ。このサーバーの投稿と、相手のサーバーが付けていない投稿は null
 * @param mentions [content] に書かれたメンション
 * @param favouriteCount 他のサーバーの投稿は、相手のサーバーの数を取ったことがなければ null
 * @param boostCount 他のサーバーの投稿は、相手のサーバーの数を取ったことがなければ null
 * @param replyCount 他のサーバーの投稿は、このサーバーが知っている返信と、相手のサーバーの数の大きい方
 * @param stampCountsReceived スタンプの数を受け取っているか。他のサーバーの投稿はスタンプを受け取らないので false
 * @param stamps 先に押された絵文字の順
 * @param emojis [content] と [summary] に入るカスタム絵文字
 * @param shareUrl 共有するときに渡す投稿の URL。作れなければ null
 * @param likedByMe ログイン中のアカウントがいいねしているか。リアクションを付けていても true。いいねできない投稿は null
 * @param viewerReaction ログイン中のアカウントが付けたリアクション。付けていないか、絵文字の無いいいねなら null
 * @param boostedByMe ログイン中のアカウントがブーストしているか。ブーストできない投稿は null
 * @param edited 投稿者が編集した投稿か。編集前の版は [UserApi.postRevisions] で取る
 */
data class UserPostBody(
    val content: String,
    val language: String?,
    val publishedAt: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkUrls: List<String>,
    val mentions: List<UserPostMention>,
    val images: List<UserPostImage>,
    val favouriteCount: Int?,
    val boostCount: Int?,
    val replyCount: Int,
    val stampCountsReceived: Boolean,
    val stamps: List<UserPostStamp>,
    val emojis: List<UserCustomEmoji>,
    val shareUrl: String?,
    val likedByMe: Boolean?,
    val viewerReaction: UserViewerReaction?,
    val boostedByMe: Boolean?,
    val edited: Boolean,
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
 * @param customEmojiShortcode このサーバーのカスタム絵文字なら shortcode。他のサーバーの絵文字や消された絵文字は null
 */
data class UserPostStamp(
    val emoji: String,
    val imageUrl: String?,
    val count: Int,
    val customEmojiShortcode: String?,
) {
    /**
     * 同じ絵文字でリアクションするときのもの。他のサーバーのカスタム絵文字や消された絵文字は付けられないので null
     */
    fun toReaction(): UserReaction? {
        val imageUrl = imageUrl
        return when {
            customEmojiShortcode != null && imageUrl != null -> UserReaction.Custom(shortcode = customEmojiShortcode, imageUrl = imageUrl)
            imageUrl == null && !emoji.startsWith(":") -> UserReaction.Unicode(emoji)
            else -> null
        }
    }
}

/**
 * 絵文字の無いいいねに変える。付けていたリアクションは外れる
 */
fun UserPostBody.withLiked(liked: Boolean): UserPostBody = withLikeState(if (liked) UserLikeState.Liked else UserLikeState.None)

/**
 * いいねかリアクションを付け替える。1 つの投稿に付けられるのはどちらか 1 つ。
 * お気に入りの数とスタンプは、自分の分だけ増減させる。他の人の分は取り直すまで分からない
 */
fun UserPostBody.withLikeState(state: UserLikeState): UserPostBody {
    if (likedByMe == null || likeState() == state) return this
    val nowPlainLiked = state == UserLikeState.Liked
    val nextReaction = (state as? UserLikeState.Reacted)?.reaction
    return copy(
        likedByMe = state != UserLikeState.None,
        viewerReaction = nextReaction,
        // 他のサーバーの投稿は数を受け取らないので、スタンプも自分の分を足さない
        stamps = if (favouriteCount == null) stamps else stamps.withoutOne(viewerReaction).withOneMore(nextReaction),
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

private fun List<UserPostStamp>.withoutOne(reaction: UserViewerReaction?): List<UserPostStamp> {
    if (reaction == null) return this
    return mapNotNull { stamp ->
        when {
            !stamp.isSameAs(reaction) -> stamp
            stamp.count <= 1 -> null
            else -> stamp.copy(count = stamp.count - 1)
        }
    }
}

/**
 * 無い絵文字は最後に足す。スタンプは先に押された絵文字の順に並ぶため
 */
private fun List<UserPostStamp>.withOneMore(reaction: UserViewerReaction?): List<UserPostStamp> {
    if (reaction == null) return this
    if (none { it.isSameAs(reaction) }) {
        // 自分が付けられるカスタム絵文字はこのサーバーのものだけなので、画像があれば :shortcode: から名前を取れる
        val shortcode = if (reaction.imageUrl == null) null else reaction.emoji.removeSurrounding(":")
        return this + UserPostStamp(emoji = reaction.emoji, imageUrl = reaction.imageUrl, count = 1, customEmojiShortcode = shortcode)
    }
    return map { if (it.isSameAs(reaction)) it.copy(count = it.count + 1) else it }
}

private fun UserPostStamp.isSameAs(reaction: UserViewerReaction): Boolean = emoji == reaction.emoji && imageUrl == reaction.imageUrl
