package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
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
 * @return 取れなかったら [QuoteLoad.Unavailable]。ログインが切れていたら null で、読み込み前に戻す
 */
internal suspend fun UserApi.loadQuote(postId: String): QuoteLoad? =
    when (val result = quoteTarget(postId)) {
        is UserPostDetailResult.Found -> QuoteLoad.Loaded(result.post)

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
    post: UserHomeTimelineResult.Post,
    load: QuoteLoad?,
    onOpenPostDetail: (postId: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
): PostItemUiState.Quote? {
    if (!post.hasQuoteTarget) return null
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

/**
 * 公開範囲に制限のある投稿は引用できない。ブーストできる投稿と同じ範囲なので、ブーストできるかで決める
 *
 * @param postId 引用する [UserHomeTimelineResult.Post.id]
 */
internal fun createQuoteMenuItems(
    postId: String,
    body: UserPostBody,
    onQuote: (postId: String) -> Unit,
): List<PostItemUiState.MenuItem> {
    if (body.boostedByMe == null) return listOf()
    return listOf(
        PostItemUiState.MenuItem(
            label = "引用",
            listener = object : PostItemUiState.MenuItemListener {
                override fun onClick() {
                    onQuote(postId)
                }
            },
        ),
    )
}
