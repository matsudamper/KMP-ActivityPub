package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 投稿の中身の見せ方はここだけで決める。画面ごとに組み立てると、閲覧注意の扱いなどが画面によって食い違う
 *
 * @param linkPreviews 取れた OGP。まだ取れていないリンクは URL だけのカードになる
 * @param boostedBy フォローしている相手のブーストとして載った投稿なら、ブーストした相手
 * @param onOpenImage urls は左右に送って見られる投稿の画像すべて
 * @param onToggleImageBlur 1枚の開け閉めを同じ投稿の画像すべてに連動させるため、投稿の画像すべてと、開け閉め後の toggled を渡す
 */
internal fun createPostItemUiState(
    boostedBy: Boost?,
    iconUrl: String?,
    displayName: String,
    displayNameEmojis: List<UserCustomEmoji>,
    acct: String,
    body: UserPostBody,
    linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
    sensitiveImagesBlurred: Boolean,
    blurToggledImageUrls: Set<String>,
    authorTapAction: AuthorTapAction,
    menuItems: List<PostItemUiState.MenuItem>,
    postTapAction: PostTapAction,
    onOpenUrl: (url: String) -> Unit,
    onOpenImage: (urls: List<String>, index: Int) -> Unit,
    onToggleImageBlur: (imageUrls: List<String>, toggled: Boolean) -> Unit,
    onShare: (url: String) -> Unit,
    onToggleLike: () -> Unit,
    onToggleBoost: () -> Unit,
    onVisible: () -> Unit,
): PostItemUiState {
    val shareUrl = body.shareUrl
    val imageUrls = body.images.map { it.url }
    return PostItemUiState(
        boostedBy = boostedBy?.let { boosted ->
            val booster = boosted.booster
            PostItemUiState.BoostedBy(
                text = createTextWithEmojis(text = "${booster.displayName} がブースト", emojis = booster.displayNameEmojis),
                iconUrl = booster.iconUrl,
                listener = boosted.navigateToProfile?.let { navigateToProfile ->
                    object : PostItemUiState.BoostedByListener {
                        override fun onClick() {
                            navigateToProfile(booster.host?.let { host -> "${booster.username}@$host" } ?: booster.username)
                        }
                    }
                },
            )
        },
        iconUrl = iconUrl,
        displayName = createTextWithEmojis(text = displayName, emojis = displayNameEmojis),
        acct = acct,
        body = createPostBodyText(content = body.content, emojis = body.emojis, onOpenUrl = onOpenUrl),
        postedAt = createPostedAtText(
            publishedAt = body.publishedAt,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault(),
        ),
        linkPreviews = body.linkUrls.map { url ->
            createPostLinkPreview(url = url, preview = linkPreviews.firstOrNull { it.url == url }) { onOpenUrl(url) }
        },
        images = body.images.mapIndexed { index, image ->
            createPostImage(
                image = image,
                sensitive = body.sensitive,
                sensitiveImagesBlurred = sensitiveImagesBlurred,
                blurToggled = image.url in blurToggledImageUrls,
                onOpen = { onOpenImage(imageUrls, index) },
                onToggleBlur = { onToggleImageBlur(imageUrls, image.url !in blurToggledImageUrls) },
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
        boostButton = body.boostedByMe?.let { boosted ->
            PostItemUiState.BoostButton(
                iconHighlighted = boosted,
                listener = object : PostItemUiState.BoostButtonListener {
                    override fun onClick() {
                        onToggleBoost()
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
        shareListener = if (shareUrl == null) {
            null
        } else {
            object : PostItemUiState.ShareListener {
                override fun onClick() {
                    onShare(shareUrl)
                }
            }
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

/**
 * @param navigateToProfile username はブーストした相手の user@host。このサーバーのアカウントなら user だけ。ブーストした相手のプロフィールを見ているときは null
 */
internal class Boost(
    val booster: UserHomeTimelineResult.Booster,
    val navigateToProfile: ((username: String) -> Unit)?,
)
