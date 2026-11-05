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
    val body: AnnotatedString,
    val postedAt: String,
    /**
     * 本文のリンクごとのカード。OGP が取れるまでは URL だけで埋めておく
     */
    val linkPreviews: List<LinkPreview>,
    val images: List<Image>,
    /**
     * 1 件も無ければ null で、お気に入りのアイコンだけを出す
     */
    val favouriteCountText: String?,
    val stamps: List<Stamp>,
    /**
     * 押しても移る先が無いときは null で、アイコンや名前を押せなくする
     */
    val authorListener: AuthorListener?,
    /**
     * 空ならメニューのボタンを出さない
     */
    val menuItems: List<MenuItem>,
    val listener: Listener,
) {
    data class MenuItem(
        val label: String,
        val listener: MenuItemListener,
    )

    /**
     * @param imageUrl null でも画像の枠は出す
     * @param faviconUrl imageUrl が null のとき、画像の枠の中央に小さく出す
     */
    data class LinkPreview(
        val title: String,
        val siteName: String,
        val imageUrl: String?,
        val faviconUrl: String?,
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
        val blurToggleButtonVisible: Boolean,
        val blurred: Boolean,
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
    interface MenuItemListener {
        fun onClick()
    }

    @Immutable
    interface ImageListener {
        fun onClick()

        fun onClickBlurToggle()
    }
}
