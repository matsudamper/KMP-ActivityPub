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
 * @return 取れなかったか、引用先が消されていたら [QuoteLoad.Unavailable]
 */
internal suspend fun UserApi.loadQuote(postId: String): QuoteLoad =
    when (val result = quoteTarget(postId)) {
        is UserPostDetailResult.Found -> if (result.post.deleted) QuoteLoad.Unavailable else QuoteLoad.Loaded(result.post)

        UserPostDetailResult.NotFound,
        is UserPostDetailResult.Failure,
        -> QuoteLoad.Unavailable
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
    sensitiveImagesBlurred: Boolean,
    blurToggledImageUrls: Set<String>,
    onOpenPostDetail: (postId: String) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
    onOpenImage: (urls: List<String>, index: Int) -> Unit,
    onToggleImageBlur: (imageUrls: List<String>, toggled: Boolean) -> Unit,
): PostItemUiState.Quote? {
    if (!hasQuoteTarget) return null
    return createQuote(
        load = load,
        sensitiveImagesBlurred = sensitiveImagesBlurred,
        blurToggledImageUrls = blurToggledImageUrls,
        onOpenPostDetail = onOpenPostDetail,
        onOpenUrl = onOpenUrl,
        onOpenProfile = onOpenProfile,
        onOpenImage = onOpenImage,
        onToggleImageBlur = onToggleImageBlur,
    )
}

/**
 * @param onOpenPostDetail 押しても移る先が無いときは null
 */
internal fun createQuote(
    load: QuoteLoad?,
    sensitiveImagesBlurred: Boolean,
    blurToggledImageUrls: Set<String>,
    onOpenPostDetail: ((postId: String) -> Unit)?,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
    onOpenImage: (urls: List<String>, index: Int) -> Unit,
    onToggleImageBlur: (imageUrls: List<String>, toggled: Boolean) -> Unit,
): PostItemUiState.Quote {
    return when (load) {
        null, QuoteLoad.Loading -> PostItemUiState.Quote.Loading

        QuoteLoad.Unavailable -> PostItemUiState.Quote.Unavailable

        is QuoteLoad.Loaded -> {
            val quoted = load.post
            val imageUrls = quoted.body.images.map { it.url }
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
                images = quoted.body.images.mapIndexed { index, image ->
                    createPostImage(
                        image = image,
                        sensitive = quoted.body.sensitive,
                        sensitiveImagesBlurred = sensitiveImagesBlurred,
                        blurToggled = image.url in blurToggledImageUrls,
                        onOpen = { onOpenImage(imageUrls, index) },
                        onToggleBlur = { onToggleImageBlur(imageUrls, image.url !in blurToggledImageUrls) },
                    )
                },
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
