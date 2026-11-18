package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostDetailResult
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 返信先の投稿者の読み込みの状態。投稿の id ごとに持つ
 */
internal sealed interface ReplyTargetAuthorLoad {
    data object Loading : ReplyTargetAuthorLoad

    data object Unavailable : ReplyTargetAuthorLoad

    data class Loaded(val author: UserHomeTimelineResult.Actor) : ReplyTargetAuthorLoad
}

/**
 * @param postId [UserHomeTimelineResult.Post.id]
 * @return 取れなかったら [ReplyTargetAuthorLoad.Unavailable]
 */
internal suspend fun UserApi.loadReplyTargetAuthor(postId: String): ReplyTargetAuthorLoad =
    when (val result = replyTarget(postId)) {
        is UserPostDetailResult.Found -> ReplyTargetAuthorLoad.Loaded(
            UserHomeTimelineResult.Actor(
                username = result.post.username,
                host = result.post.host,
                displayName = result.post.displayName,
                displayNameEmojis = result.post.displayNameEmojis,
                iconUrl = result.post.iconUrl,
            ),
        )

        UserPostDetailResult.NotFound,
        is UserPostDetailResult.Failure,
        -> ReplyTargetAuthorLoad.Unavailable
    }

/**
 * 読み込みを始める前も、行を先に出すために [PostItemUiState.ReplyLabel] を返す
 *
 * @param load まだ読み込みを始めていなければ null
 * @param onOpenProfile acct は返信先の投稿者の user@host。このサーバーのアカウントなら user だけ
 * @return 返信でなければ null
 */
internal fun createPostItemReplyLabel(
    hasReplyTarget: Boolean,
    load: ReplyTargetAuthorLoad?,
    onOpenProfile: (acct: String) -> Unit,
): PostItemUiState.ReplyLabel? {
    if (!hasReplyTarget) return null
    return PostItemUiState.ReplyLabel(
        target = when (load) {
            null, ReplyTargetAuthorLoad.Loading, ReplyTargetAuthorLoad.Unavailable -> null

            is ReplyTargetAuthorLoad.Loaded -> {
                val author = load.author
                PostItemUiState.ReplyLabelTarget(
                    iconUrl = author.iconUrl,
                    name = createTextWithEmojis(text = author.displayName, emojis = author.displayNameEmojis),
                    listener = object : PostItemUiState.ReplyLabelListener {
                        override fun onClick() {
                            onOpenProfile(author.host?.let { host -> "${author.username}@$host" } ?: author.username)
                        }
                    },
                )
            }
        },
    )
}
