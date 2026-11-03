package net.matsudamper.kmp.activitypub.frontend.screen

import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

/**
 * @param preview まだ取れていなければ null。枠は先に出しておき、取れたら中身だけ差し替える
 */
internal fun createPostLinkPreview(
    url: String,
    preview: UserLinkPreviewsResult.LinkPreview?,
    onClick: () -> Unit,
): PostItemUiState.LinkPreview {
    return PostItemUiState.LinkPreview(
        title = preview?.title ?: url,
        siteName = preview?.siteName ?: url.substringAfter("://").substringBefore('/'),
        imageUrl = preview?.imageUrl,
        listener = object : PostItemUiState.LinkPreviewListener {
            override fun onClick() {
                onClick()
            }
        },
    )
}
