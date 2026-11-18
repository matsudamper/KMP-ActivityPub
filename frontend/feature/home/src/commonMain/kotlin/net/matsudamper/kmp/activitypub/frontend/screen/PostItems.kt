package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostStamp
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 投稿の中身の見せ方はここだけで決める。画面ごとに組み立てると、閲覧注意の扱いなどが画面によって食い違う
 *
 * @param onToggleTranslation 翻訳のボタンを押したとき
 * @param contentWarningOpened 閲覧注意の投稿の本文を開いているか。閲覧注意でなければ見ない
 * @param onToggleContentWarning 閲覧注意の本文を開くか畳むボタンを押したとき
 * @param linkPreviews 取れた OGP。まだ取れていないリンクは URL だけのカードになる
 * @param quote [createPostItemQuote] で作る。引用でなければ null
 * @param replyLabel [createPostItemReplyLabel] で作る。「〜へのリプライ」の行を出さなければ null
 * @param actorLabel 投稿の上に出す「〜がブースト」「〜がお気に入り」の行の元。出さなければ null
 * @param pinned プロフィールの先頭に固定されている投稿か
 * @param deleted 投稿者が投稿を消した後か
 * @param boostUndone ブーストした相手がブーストを取り消した後か
 * @param onOpenProfile 本文のメンションを押したとき。acct は `user@host`
 * @param onOpenImage urls は左右に送って見られる投稿の画像すべて
 * @param onToggleImageBlur 1枚の開け閉めを同じ投稿の画像すべてに連動させるため、投稿の画像すべてと、開け閉め後の toggled を渡す
 * @param onReply リプライのアイコンを押したとき
 * @param onQuote ブーストのアイコンから引用を選んだとき
 * @param onOpenReactionPicker リアクションのアイコンを押したとき
 * @param onClickStamp スタンプを押したとき
 * @param onOpenRevisions 編集済みのラベルを押したとき。編集前の版を出さない場所では null
 */
internal fun createPostItemUiState(
    replyLabel: PostItemUiState.ReplyLabel?,
    actorLabel: PostActorLabel?,
    pinned: Boolean,
    deleted: Boolean,
    boostUndone: Boolean,
    iconUrl: String?,
    displayName: String,
    displayNameEmojis: List<UserCustomEmoji>,
    acct: String,
    body: UserPostBody,
    translation: PostTranslationState,
    contentWarningOpened: Boolean,
    linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
    quote: PostItemUiState.Quote?,
    sensitiveImagesBlurred: Boolean,
    blurToggledImageUrls: Set<String>,
    authorTapAction: AuthorTapAction,
    menuItems: List<PostItemUiState.MenuItem>,
    postTapAction: PostTapAction,
    onOpenUrl: (url: String) -> Unit,
    onOpenProfile: (acct: String) -> Unit,
    onOpenImage: (urls: List<String>, index: Int) -> Unit,
    onToggleImageBlur: (imageUrls: List<String>, toggled: Boolean) -> Unit,
    onShare: (url: String) -> Unit,
    onToggleTranslation: (PostTranslationSource) -> Unit,
    onToggleContentWarning: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleBoost: () -> Unit,
    onReply: () -> Unit,
    onQuote: () -> Unit,
    onOpenReactionPicker: () -> Unit,
    onClickStamp: (UserPostStamp) -> Unit,
    onOpenRevisions: (() -> Unit)?,
    onVisible: () -> Unit,
): PostItemUiState {
    val shareUrl = body.shareUrl
    val imageUrls = body.images.map { it.url }
    val translationSource = body.language?.let { PostTranslationSource(text = body.content, languageTag = it) }
    val translationProgress = translationSource?.let { translation.progressBySource[it] }
    val translated = translationProgress as? PostTranslationProgress.Translated
    val summary = body.summary
    val bodyVisible = summary == null || contentWarningOpened
    return PostItemUiState(
        replyLabel = replyLabel,
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
        contentWarning = summary?.let {
            PostItemUiState.ContentWarning(
                text = createTextWithEmojis(text = summary, emojis = body.emojis),
                toggleButtonText = if (contentWarningOpened) "隠す" else "もっと見る",
                listener = object : PostItemUiState.ContentWarningListener {
                    override fun onClickToggle() {
                        onToggleContentWarning()
                    }
                },
            )
        },
        bodyVisible = bodyVisible,
        body = createPostBodyText(
            content = translated?.text ?: body.content,
            // 訳すと文字の位置がずれるので、メンションは原文のときだけ押せる
            mentions = if (translated == null) body.mentions else listOf(),
            emojis = body.emojis,
            onOpenUrl = onOpenUrl,
            onOpenProfile = onOpenProfile,
        ),
        postedAt = createPostedAtText(
            publishedAt = body.publishedAt,
            now = Clock.System.now(),
            timeZone = TimeZone.currentSystemDefault(),
        ),
        fullPostedAt = createFullPostedAtText(publishedAt = body.publishedAt, timeZone = TimeZone.currentSystemDefault()),
        editedLabel = if (body.edited) {
            PostItemUiState.EditedLabel(
                listener = onOpenRevisions?.let { openRevisions ->
                    object : PostItemUiState.EditedLabelListener {
                        override fun onClick() {
                            openRevisions()
                        }
                    }
                },
            )
        } else {
            null
        },
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
        videos = body.videos.map { video ->
            PostItemUiState.Video(
                url = video.url,
                description = video.description,
                firstFrameVisible = !(body.sensitive && sensitiveImagesBlurred),
            )
        },
        poll = body.poll?.let { poll -> createPostPoll(poll = poll, emojis = body.emojis, now = Clock.System.now()) },
        replyCountText = body.replyCount.takeIf { it > 0 }?.toString(),
        replyListener = if (deleted) {
            null
        } else {
            object : PostItemUiState.ReplyListener {
                override fun onClick() {
                    onReply()
                }
            }
        },
        quote = quote,
        favouriteCountText = body.favouriteCount?.takeIf { it > 0 }?.toString(),
        boostCountText = body.boostCount?.takeIf { it > 0 }?.toString(),
        favouriteButton = body.likedByMe?.let {
            PostItemUiState.FavouriteButton(
                // リアクションも Like で送るが、絵文字を付けたものはリアクションのアイコンに出す
                iconHighlighted = body.plainLikedByMe,
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
                menuItems = listOf(
                    PostItemUiState.MenuItem(
                        label = if (boosted) "ブーストを取り消す" else "ブースト",
                        listener = object : PostItemUiState.MenuItemListener {
                            override fun onClick() {
                                onToggleBoost()
                            }
                        },
                    ),
                    PostItemUiState.MenuItem(
                        label = "引用",
                        listener = object : PostItemUiState.MenuItemListener {
                            override fun onClick() {
                                onQuote()
                            }
                        },
                    ),
                ),
            )
        },
        // いいねとリアクションは同じ Like で送るので、いいねできる投稿だけリアクションできる
        reactionButton = body.likedByMe?.let {
            PostItemUiState.ReactionButton(
                listener = object : PostItemUiState.ReactionButtonListener {
                    override fun onClick() {
                        onOpenReactionPicker()
                    }
                },
            )
        },
        stamps = createStamps(body = body, onClickStamp = onClickStamp),
        authorListener = when (authorTapAction) {
            is AuthorTapAction.OpenProfile -> object : PostItemUiState.AuthorListener {
                override fun onClick() {
                    authorTapAction.navigate()
                }
            }

            AuthorTapAction.AlreadyOnAuthorProfile -> null
        },
        // 畳んだ本文は訳しても見えない
        translateButton = if (translationSource == null || !bodyVisible) {
            null
        } else {
            createTranslateButton(source = translationSource, progress = translationProgress, translation = translation, onToggleTranslation = onToggleTranslation)
        },
        menuItems = createShareMenuItems(shareUrl = shareUrl, onShare = onShare) + menuItems,
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

private fun createShareMenuItems(shareUrl: String?, onShare: (url: String) -> Unit): List<PostItemUiState.MenuItem> {
    if (shareUrl == null) return listOf()
    return listOf(
        PostItemUiState.MenuItem(
            label = "共有",
            listener = object : PostItemUiState.MenuItemListener {
                override fun onClick() {
                    onShare(shareUrl)
                }
            },
        ),
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

/**
 * 他のサーバーの投稿はスタンプを受け取らないので、自分のリアクションだけを数無しで並べる
 */
private fun createStamps(body: UserPostBody, onClickStamp: (UserPostStamp) -> Unit): List<PostItemUiState.Stamp> {
    val viewerReaction = body.viewerReaction
    val countReceived = body.stampCountsReceived
    val stamps = if (viewerReaction == null || body.stamps.any { it.isReactionOf(viewerReaction) }) {
        body.stamps
    } else {
        body.stamps + UserPostStamp(
            emoji = viewerReaction.emoji,
            imageUrl = viewerReaction.imageUrl,
            count = 1,
            customEmojiShortcode = if (viewerReaction.imageUrl == null) null else viewerReaction.emoji.removeSurrounding(":"),
        )
    }
    // いいねできない投稿にはリアクションもできない
    val reactable = body.likedByMe != null
    return stamps.map { stamp ->
        val selected = viewerReaction != null && stamp.isReactionOf(viewerReaction)
        PostItemUiState.Stamp(
            emoji = stamp.emoji,
            imageUrl = stamp.imageUrl,
            countText = if (countReceived) stamp.count.toString() else null,
            selected = selected,
            listener = if (reactable && (selected || stamp.toReaction() != null)) {
                object : PostItemUiState.StampListener {
                    override fun onClick() {
                        onClickStamp(stamp)
                    }
                }
            } else {
                null
            },
        )
    }
}

private fun createTranslateButton(
    source: PostTranslationSource,
    progress: PostTranslationProgress?,
    translation: PostTranslationState,
    onToggleTranslation: (PostTranslationSource) -> Unit,
): PostItemUiState.TranslateButton? {
    if (!translation.translatorSupported) return null
    if (!isWrittenInOtherLanguage(postLanguageTag = source.languageTag, viewerLanguage = translation.viewerLanguage)) return null
    return PostItemUiState.TranslateButton(
        text = when (progress) {
            null -> "翻訳"
            PostTranslationProgress.Translating -> "翻訳中"
            is PostTranslationProgress.Translated -> "原文を表示"
        },
        enabled = progress != PostTranslationProgress.Translating,
        listener = object : PostItemUiState.TranslateButtonListener {
            override fun onClick() {
                onToggleTranslation(source)
            }
        },
    )
}

/**
 * 地域や文字体系が違っても、言語が同じなら同じとみなす。設定した言語が分からなければ、違うとは言えないので false
 */
private fun isWrittenInOtherLanguage(postLanguageTag: String, viewerLanguage: LanguageTag?): Boolean {
    if (viewerLanguage == null) return false
    return !primaryLanguageOf(postLanguageTag).equals(primaryLanguageOf(viewerLanguage.value), ignoreCase = true)
}

private fun primaryLanguageOf(languageTag: String): String = languageTag.substringBefore('-')
