package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * @param iconUrl 投稿者のプロフィール画像が無ければ null
 */
data class PostItemUiState(
    /**
     * 投稿の上に出す「〜がブースト」「〜がお気に入り」の行。フォローしている相手のブーストとして載った投稿と、通知で使う。それ以外は null
     */
    val actorLabel: ActorLabel?,
    val pinnedLabelVisible: Boolean,
    /**
     * 投稿者が投稿を消したか、ブーストした相手がブーストを取り消した後か。一覧は消える前の内容のまま出すので、消えたことを示す
     */
    val deletedLabelVisible: Boolean,
    val iconUrl: String?,
    val displayName: TextWithEmojis,
    val acct: String,
    val body: TextWithEmojis,
    val postedAt: String,
    /**
     * 投稿の単体画面で、添付の下に出す年月日と時刻
     */
    val fullPostedAt: String,
    /**
     * 本文のリンクごとのカード。OGP が取れるまでは URL だけで埋めておく
     */
    val linkPreviews: List<LinkPreview>,
    val images: List<Image>,
    /**
     * 1 件も無ければ null で、お気に入りのアイコンだけを出す
     */
    val favouriteCountText: String?,
    /**
     * 1 件も無ければ null で、ブーストのアイコンだけを出す
     */
    val boostCountText: String?,
    /**
     * いいねできない投稿は null で、お気に入りのアイコンを押せなくする
     */
    val favouriteButton: FavouriteButton?,
    /**
     * ブーストできない投稿は null で、ブーストのアイコンを押せなくする
     */
    val boostButton: BoostButton?,
    val stamps: List<Stamp>,
    /**
     * 押しても移る先が無いときは null で、アイコンや名前を押せなくする
     */
    val authorListener: AuthorListener?,
    /**
     * 空ならメニューのボタンを出さない
     */
    val menuItems: List<MenuItem>,
    /**
     * 押しても移る先が無いときは null で、投稿を押せなくする
     */
    val postListener: PostListener?,
    /**
     * 共有する URL が無いときは null で、共有のアイコンを押せなくする
     */
    val shareListener: ShareListener?,
    val listener: Listener,
) {
    /**
     * @param iconUrl 相手のプロフィール画像が無ければ null
     * @param listener その相手のプロフィールを見ているときは押せないので null
     * @param cancelledLabelVisible 自分のブーストを外した後か。一覧は読み直すまでそのまま出すので、外したことを示す
     */
    data class ActorLabel(
        val icon: ActorLabelIcon,
        val text: TextWithEmojis,
        val iconUrl: String?,
        val cancelledLabelVisible: Boolean,
        val listener: ActorLabelListener?,
    )

    enum class ActorLabelIcon {
        Boost,
        Favourite,
    }

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

    data class FavouriteButton(
        val iconHighlighted: Boolean,
        val listener: FavouriteButtonListener,
    )

    data class BoostButton(
        val iconHighlighted: Boolean,
        val listener: BoostButtonListener,
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
    interface FavouriteButtonListener {
        fun onClick()
    }

    @Immutable
    interface BoostButtonListener {
        fun onClick()
    }

    @Immutable
    interface ActorLabelListener {
        fun onClick()
    }

    @Immutable
    interface AuthorListener {
        fun onClick()
    }

    @Immutable
    interface PostListener {
        fun onClick()
    }

    @Immutable
    interface ShareListener {
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
