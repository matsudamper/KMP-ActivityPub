package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * @param iconUrl 投稿者のプロフィール画像が無ければ null
 */
data class PostItemUiState(
    val iconUrl: String?,
    val displayName: String,
    val acct: String,
    val body: String,
    val postedAt: String,
    /**
     * 本文のリンクごとのカード。OGP が取れるまでは URL だけで埋めておく
     */
    val linkPreviews: List<LinkPreview>,
    val authorClickable: Boolean,
    val listener: Listener,
) {
    /**
     * @param imageUrl null でも画像の枠は出す
     */
    data class LinkPreview(
        val title: String,
        val siteName: String,
        val imageUrl: String?,
        val listener: LinkPreviewListener,
    )

    @Immutable
    interface Listener {
        /**
         * 投稿が画面に入ったとき。OGP はここで初めて取りに行く
         */
        fun onVisible()

        fun onClickAuthor()
    }

    @Immutable
    interface LinkPreviewListener {
        fun onClick()
    }
}
