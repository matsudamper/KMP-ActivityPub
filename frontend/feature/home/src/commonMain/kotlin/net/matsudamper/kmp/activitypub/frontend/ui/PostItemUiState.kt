package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString

/**
 * @param iconUrl 投稿者のプロフィール画像が無ければ null
 */
data class PostItemUiState(
    val iconUrl: String?,
    val displayName: String,
    val acct: String,
    /**
     * 閲覧注意の投稿だけ持つ。投稿者が注意書きを付けていればその文言になる
     */
    val contentWarningLabel: String?,
    val body: AnnotatedString,
    val postedAt: String,
    /**
     * 本文のリンクごとのカード。OGP が取れるまでは URL だけで埋めておく
     */
    val linkPreviews: List<LinkPreview>,
    val images: List<Image>,
    /**
     * 1 件も無ければ null で、お気に入りの数を出さない
     */
    val favouriteCountText: String?,
    val stamps: List<Stamp>,
    /**
     * 押しても移る先が無いときは null で、アイコンや名前を押せなくする
     */
    val authorListener: AuthorListener?,
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

    /**
     * @param imageUrl カスタム絵文字の画像。null なら [emoji] の文字で見せる
     */
    data class Stamp(
        val emoji: String,
        val imageUrl: String?,
        val countText: String,
    )

    /**
     * @param description 画像の説明文。無ければ null
     */
    data class Image(
        val url: String,
        val description: String?,
        val listener: ImageListener,
    )

    @Immutable
    interface Listener {
        /**
         * 投稿が画面に入ったとき。OGP はここで初めて取りに行く
         */
        fun onVisible()
    }

    @Immutable
    interface AuthorListener {
        fun onClick()
    }

    @Immutable
    interface LinkPreviewListener {
        fun onClick()
    }

    @Immutable
    interface ImageListener {
        fun onClick()
    }
}
