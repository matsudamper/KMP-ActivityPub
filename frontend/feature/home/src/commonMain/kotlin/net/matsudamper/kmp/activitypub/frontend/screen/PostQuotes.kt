package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostDetailResult
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 引用先の読み込みの状態。投稿の id ごとに持つ
 */
internal sealed interface QuoteLoad {
    data object Loading : QuoteLoad

    data object Unavailable : QuoteLoad

    data class Loaded(val post: UserHomeTimelineResult.Post) : QuoteLoad
}

/**
 * @param postId [UserHomeTimelineResult.Post.id]
 * @return 取れなかったか、引用先が消されていたら [QuoteLoad.Unavailable]。ログインが切れていたら null で、読み込み前に戻す
 */
internal suspend fun UserApi.loadQuote(postId: String): QuoteLoad? =
    when (val result = quoteTarget(postId)) {
        is UserPostDetailResult.Found -> if (result.post.deleted) QuoteLoad.Unavailable else QuoteLoad.Loaded(result.post)

        UserPostDetailResult.NotFound,
        is UserPostDetailResult.Failure,
        -> QuoteLoad.Unavailable

        UserPostDetailResult.NotLoggedIn -> null
    }

/**
 * 読み込みを始める前も、枠を先に出すために [PostItemUiState.Quote.Loading] にする
 *
 * @param load まだ読み込みを始めていなければ null
 * @return 引用でなければ null
 */
internal fun createPostItemQuote(
    hasQuoteTarget: Boolean,
    load: QuoteLoad?,
    onOpenPostDetail: (postId: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
): PostItemUiState.Quote? {
    if (!hasQuoteTarget) return null
    return createQuote(load = load, onOpenPostDetail = onOpenPostDetail, onOpenUrl = onOpenUrl, onOpenProfile = onOpenProfile)
}

/**
 * @param onOpenPostDetail 押しても移る先が無いときは null
 */
internal fun createQuote(
    load: QuoteLoad?,
    onOpenPostDetail: ((postId: String) -> Unit)?,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
): PostItemUiState.Quote {
    return when (load) {
        null, QuoteLoad.Loading -> PostItemUiState.Quote.Loading

        QuoteLoad.Unavailable -> PostItemUiState.Quote.Unavailable

        is QuoteLoad.Loaded -> {
            val quoted = load.post
            PostItemUiState.Quote.Loaded(
                iconUrl = quoted.iconUrl,
                displayName = createTextWithEmojis(text = quoted.displayName, emojis = quoted.displayNameEmojis),
                acct = quoted.host?.let { host -> "@${quoted.username}@$host" } ?: "@${quoted.username}",
                body = createPostBodyText(
                    content = quoted.body.content,
                    mentions = quoted.body.mentions,
                    emojis = quoted.body.emojis,
                    onOpenUrl = onOpenUrl,
                    onOpenProfile = onOpenProfile,
                ),
                listener = onOpenPostDetail?.let { navigate ->
                    object : PostItemUiState.QuoteListener {
                        override fun onClick() {
                            navigate(quoted.id)
                        }
                    }
                },
            )
        }
    }
}
