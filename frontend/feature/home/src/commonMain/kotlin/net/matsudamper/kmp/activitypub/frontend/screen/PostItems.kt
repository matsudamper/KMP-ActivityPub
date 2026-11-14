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
 * @param actorLabel 投稿の上に出す「〜がブースト」「〜がお気に入り」の行の元。出さなければ null
 * @param pinned プロフィールの先頭に固定されている投稿か
 * @param deleted 投稿者が投稿を消した後か
 * @param boostUndone ブーストした相手がブーストを取り消した後か
 * @param onOpenImage urls は左右に送って見られる投稿の画像すべて
 * @param onToggleImageBlur 1枚の開け閉めを同じ投稿の画像すべてに連動させるため、投稿の画像すべてと、開け閉め後の toggled を渡す
 */
internal fun createPostItemUiState(
    actorLabel: PostActorLabel?,
    pinned: Boolean,
    deleted: Boolean,
    boostUndone: Boolean,
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
        actorLabel = actorLabel?.let { label ->
            val actor = label.actor
            PostItemUiState.ActorLabel(
                icon = when (label) {
                    is PostActorLabel.Boost, is PostActorLabel.BoostNotification -> PostItemUiState.ActorLabelIcon.Boost
                    is PostActorLabel.FavouriteNotification -> PostItemUiState.ActorLabelIcon.Favourite
                },
                actorName = createTextWithEmojis(text = actor.displayName, emojis = actor.displayNameEmojis),
                actionText = when (label) {
                    is PostActorLabel.Boost, is PostActorLabel.BoostNotification -> "がブースト"
                    is PostActorLabel.FavouriteNotification -> "がお気に入り"
                },
                iconUrl = actor.iconUrl,
                cancelledLabelVisible = label is PostActorLabel.Boost && (boostUndone || (label.byViewer && body.boostedByMe == false)),
                listener = label.navigateToProfile?.let { navigateToProfile ->
                    object : PostItemUiState.ActorLabelListener {
                        override fun onClick() {
                            navigateToProfile(actor.host?.let { host -> "${actor.username}@$host" } ?: actor.username)
                        }
                    }
                },
            )
        },
        pinnedLabelVisible = pinned,
        deletedLabelVisible = deleted,
        iconUrl = iconUrl,
        displayName = createTextWithEmojis(text = displayName, emojis = displayNameEmojis),
        acct = acct,
        body = createPostBodyText(content = body.content, emojis = body.emojis, onOpenUrl = onOpenUrl),
        postedAt = createPostedAtText(
            publishedAt = body.publishedAt,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault(),
        ),
        fullPostedAt = createFullPostedAtText(publishedAt = body.publishedAt, timeZone = TimeZone.currentSystemDefault()),
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
            is PostTapAction.OpenPostDetail -> object : PostItemUiState.PostListener {
                override fun onClick() {
                    postTapAction.navigate(postTapAction.postId)
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
     */
    class OpenPostDetail(
        val postId: String,
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
 * 投稿の上に出す「〜がブースト」「〜がお気に入り」の行の元
 */
internal sealed interface PostActorLabel {
    val actor: UserHomeTimelineResult.Actor

    /**
     * username は相手の user@host。このサーバーのアカウントなら user だけ。その相手のプロフィールを見ているときは null
     */
    val navigateToProfile: ((username: String) -> Unit)?

    /**
     * フォローしている相手か自分のブーストとして載った投稿
     *
     * @param byViewer ログイン中のアカウントのブーストか。外した後は取り消しのラベルを出す
     */
    class Boost(
        override val actor: UserHomeTimelineResult.Actor,
        val byViewer: Boolean,
        override val navigateToProfile: ((username: String) -> Unit)?,
    ) : PostActorLabel

    /**
     * 自分の投稿がブーストされた通知
     */
    class BoostNotification(
        override val actor: UserHomeTimelineResult.Actor,
        override val navigateToProfile: ((username: String) -> Unit)?,
    ) : PostActorLabel

    /**
     * 自分の投稿がお気に入りされた通知
     */
    class FavouriteNotification(
        override val actor: UserHomeTimelineResult.Actor,
        override val navigateToProfile: ((username: String) -> Unit)?,
    ) : PostActorLabel
}
