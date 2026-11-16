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
     * 投稿者が投稿を消した後か。一覧は消える前の内容のまま出すので、消えたことを示す
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
     * 投稿者が編集していなければ null
     */
    val editedLabel: EditedLabel?,
    /**
     * 本文のリンクごとのカード。OGP が取れるまでは URL だけで埋めておく
     */
    val linkPreviews: List<LinkPreview>,
    val images: List<Image>,
    /**
     * 1 件も無ければ null で、リプライのアイコンだけを出す
     */
    val replyCountText: String?,
    /**
     * 引用でなければ null
     */
    val quote: Quote?,
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
    /**
     * リアクションできない投稿は null で、リアクションのアイコンを押せなくする
     */
    val reactionButton: ReactionButton?,
    /**
     * 先に押された絵文字の順
     */
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
     * @param actionText actorName に続ける「がブースト」など。行に収まらないときは actorName だけを省略する
     * @param cancelledLabelVisible ブーストが取り消された後か。一覧は読み直すまでそのまま出すので、取り消されたことを示す
     */
    data class ActorLabel(
        val icon: ActorLabelIcon,
        val actorName: TextWithEmojis,
        val actionText: String,
        val iconUrl: String?,
        val cancelledLabelVisible: Boolean,
        val listener: ActorLabelListener?,
    )

    /**
     * @param listener 編集前の版を出せない場所では null で、押せなくする
     */
    data class EditedLabel(
        val listener: EditedLabelListener?,
    )

    sealed interface Quote {
        data object Loading : Quote

        /**
         * 引用先が消されたか、読めないか、引用先のサーバーが応えない
         */
        data object Unavailable : Quote

        /**
         * @param iconUrl 引用先の投稿者のプロフィール画像が無ければ null
         * @param listener 押しても移る先が無いときは null で、押せなくする
         */
        data class Loaded(
            val iconUrl: String?,
            val displayName: TextWithEmojis,
            val acct: String,
            val body: TextWithEmojis,
            val listener: QuoteListener?,
        ) : Quote
    }

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

    /**
     * @param menuItems 押すと出す、ブーストと引用の選択肢
     */
    data class BoostButton(
        val iconHighlighted: Boolean,
        val menuItems: List<MenuItem>,
    )

    /**
     * 付けたリアクションはスタンプの方に出すので、ボタンはいつも同じ見た目にする
     */
    data class ReactionButton(
        val listener: ReactionButtonListener,
    )

    /**
     * @param imageUrl カスタム絵文字の画像。null なら [emoji] の文字で見せる
     * @param countText 数を受け取らない他のサーバーの投稿では null
     * @param selected 自分が付けたリアクションか
     * @param listener 押しても付けられない絵文字（他のサーバーのカスタム絵文字など）は null で、押せなくする
     */
    data class Stamp(
        val emoji: String,
        val imageUrl: String?,
        val countText: String?,
        val selected: Boolean,
        val listener: StampListener?,
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
    interface ReactionButtonListener {
        fun onClick()
    }

    @Immutable
    interface StampListener {
        fun onClick()
    }

    @Immutable
    interface ActorLabelListener {
        fun onClick()
    }

    @Immutable
    interface EditedLabelListener {
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
    interface QuoteListener {
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
