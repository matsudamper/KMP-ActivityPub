package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserLinkPreviewsResult {
    data class Success(val previews: List<LinkPreview>) : UserLinkPreviewsResult
    data class Failure(val message: String) : UserLinkPreviewsResult
    data object NotLoggedIn : UserLinkPreviewsResult

    /**
     * 投稿の本文にあるリンク 1 つ分の OGP。取れなかった項目は null
     */
    data class LinkPreview(
        val url: String,
        val title: String?,
        val siteName: String?,
        val imageUrl: String?,
        val faviconUrl: String?,
    )
}
