package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * 投稿の中身の見せ方はここだけで決める。画面ごとに組み立てると、閲覧注意の扱いなどが画面によって食い違う
 *
 * @param linkPreviews 取れた OGP。まだ取れていないリンクは URL だけのカードになる
 */
internal fun createPostItemUiState(
    iconUrl: String?,
    displayName: String,
    acct: String,
    body: UserPostBody,
    linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
    sensitiveImagesBlurred: Boolean,
    blurToggledImageUrls: Set<String>,
    authorListener: PostItemUiState.AuthorListener?,
    onOpenUrl: (url: String) -> Unit,
    onOpenImage: (url: String) -> Unit,
    onToggleImageBlur: (url: String) -> Unit,
    onVisible: () -> Unit,
): PostItemUiState {
    return PostItemUiState(
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
        authorListener = authorListener,
        listener = object : PostItemUiState.Listener {
            override fun onVisible() {
                onVisible()
            }
        },
    )
}
