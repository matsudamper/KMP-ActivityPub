package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 投稿の中身の見せ方はここだけで決める。画面ごとに組み立てると、閲覧注意の扱いなどが画面によって食い違う
 *
 * @param linkPreviews 取れた OGP。まだ取れていないリンクは URL だけのカードになる
 * @param boostedBy フォローしている相手のブーストとして載った投稿なら、ブーストした相手
 */
internal fun createPostItemUiState(
    boostedBy: UserHomeTimelineResult.Booster?,
    iconUrl: String?,
    displayName: String,
    acct: String,
    body: UserPostBody,
    linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
    sensitiveImagesBlurred: Boolean,
    blurToggledImageUrls: Set<String>,
    authorTapAction: AuthorTapAction,
    menuItems: List<PostItemUiState.MenuItem>,
    postTapAction: PostTapAction,
    onOpenUrl: (url: String) -> Unit,
    onOpenImage: (url: String) -> Unit,
    onToggleImageBlur: (url: String) -> Unit,
    onToggleLike: () -> Unit,
    onVisible: () -> Unit,
): PostItemUiState {
    return PostItemUiState(
        boostedByText = boostedBy?.let { "${it.displayName} がブースト" },
        iconUrl = iconUrl,
        displayName = displayName,
        acct = acct,
        body = createPostBodyText(content = body.content, onOpenUrl = onOpenUrl),
        postedAt = createPostedAtText(
            publishedAt = body.publishedAt,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault(),
        ),
        linkPreviews = body.linkUrls.map { url ->
            createPostLinkPreview(url = url, preview = linkPreviews.firstOrNull { it.url == url }) { onOpenUrl(url) }
        },
        images = body.images.map { image ->
            createPostImage(
                image = image,
                sensitive = body.sensitive,
                sensitiveImagesBlurred = sensitiveImagesBlurred,
                blurToggled = image.url in blurToggledImageUrls,
                onOpen = { onOpenImage(image.url) },
                onToggleBlur = { onToggleImageBlur(image.url) },
            )
        },
        favouriteCountText = body.favouriteCount?.takeIf { it > 0 }?.toString(),
        boostCountText = body.boostCount?.takeIf { it > 0 }?.toString(),
        favouriteButton = body.likedByMe?.let { liked ->
            PostItemUiState.FavouriteButton(
                iconHighlighted = liked,
                listener = object : PostItemUiState.FavouriteButtonListener {
                    override fun onClick() {
                        onToggleLike()
                    }
                },
            )
        },
        stamps = body.stamps.map { stamp ->
            PostItemUiState.Stamp(emoji = stamp.emoji, imageUrl = stamp.imageUrl, countText = stamp.count.toString())
        },
        authorListener = when (authorTapAction) {
            is AuthorTapAction.OpenProfile -> object : PostItemUiState.AuthorListener {
                override fun onClick() {
                    authorTapAction.navigate()
                }
            }

            AuthorTapAction.AlreadyOnAuthorProfile -> null
        },
        menuItems = menuItems,
        postListener = when (postTapAction) {
            is PostTapAction.OpenPostDetail -> {
                val postId = postTapAction.postId
                if (postId == null) {
                    null
                } else {
                    object : PostItemUiState.PostListener {
                        override fun onClick() {
                            postTapAction.navigate(postId)
                        }
                    }
                }
            }

            PostTapAction.AlreadyOnPostDetail -> null
        },
        listener = object : PostItemUiState.Listener {
            override fun onVisible() {
                onVisible()
            }
        },
    )
}

/**
 * 投稿を押したときの動き。押せなくするのは「今その投稿の詳細を開いている」ときだけに絞るため、null ではなく型で選ばせる
 */
internal sealed interface PostTapAction {
    /**
     * 投稿を並べる画面（タイムライン・自分やほかの人のプロフィールなど）はすべてこれ
     *
     * @param postId サーバーから id が返らなかった投稿は詳細を開けないので null
     */
    class OpenPostDetail(
        val postId: String?,
        val navigate: (postId: String) -> Unit,
    ) : PostTapAction

    data object AlreadyOnPostDetail : PostTapAction
}

/**
 * アイコンや名前を押したときの動き。押せなくするのは「今その人のプロフィールを開いている」ときだけ
 */
internal sealed interface AuthorTapAction {
    class OpenProfile(
        val navigate: () -> Unit,
    ) : AuthorTapAction

    data object AlreadyOnAuthorProfile : AuthorTapAction
}
