package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

data class ProfileEditScreenUiState(
    val content: Content,
    val backButtonEnabled: Boolean,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data class Error(
            val message: String,
        ) : Content

        /**
         * @param iconPreview 選んで送った（送っている）画像。あれば [currentIconUrl] より優先して出す
         * @param currentIconUrl 保存済みのプロフィール画像。未設定なら null
         * @param headerPreview 選んで送った（送っている）ヘッダー。あれば [currentHeaderUrl] より優先して出す
         * @param currentHeaderUrl 保存済みのヘッダー。未設定なら null
         * @param headerCropDialog 選んだ画像が縦に長く、使う範囲を選んでいる間だけ出す
         */
        data class Loaded(
            val iconPreview: ImageBitmap?,
            val iconLoadingVisible: Boolean,
            val currentIconUrl: String?,
            val iconSelectButtonEnabled: Boolean,
            val headerPreview: ImageBitmap?,
            val headerLoadingVisible: Boolean,
            val currentHeaderUrl: String?,
            val headerSelectButtonEnabled: Boolean,
            val headerCropDialog: HeaderCropDialog?,
            val displayName: String,
            val displayNameInputEnabled: Boolean,
            val displayNameHasError: Boolean,
            val summary: String,
            val summaryInputEnabled: Boolean,
            val summaryHasError: Boolean,
            val links: List<Link>,
            val addLinkButtonEnabled: Boolean,
            val likesPublic: Boolean,
            val likesPublicSwitchEnabled: Boolean,
            val saveButtonEnabled: Boolean,
        ) : Content
    }

    /**
     * @param image 横幅をヘッダーに合わせた画像。縦は切らずに全部出す
     * @param cropTopRatio 選んでいる範囲の位置。0 で一番上、1 で一番下
     * @param cropHeightRatio 選んでいる範囲の高さの、[image] の高さに対する割合
     */
    data class HeaderCropDialog(
        val image: ImageBitmap,
        val cropTopRatio: Float,
        val cropHeightRatio: Float,
    )

    /**
     * @param title カードの見出し。何件目かを出す
     */
    data class Link(
        val title: String,
        val name: String,
        val url: String,
        val inputEnabled: Boolean,
        val hasError: Boolean,
        val removeButtonEnabled: Boolean,
    )

    @Immutable
    interface Listener {
        fun onClickSelectIcon()

        fun onIconPicked(file: PickedImageFile)

        fun onClickSelectHeader()

        fun onHeaderPicked(file: PickedImageFile)

        /**
         * @param cropTopRatio [HeaderCropDialog.cropTopRatio] と同じ。範囲の外の値も渡してよい
         */
        fun onHeaderCropMoved(cropTopRatio: Float)

        fun onClickHeaderCropConfirm()

        fun onClickHeaderCropCancel()

        fun onDisplayNameChanged(text: String)

        fun onSummaryChanged(text: String)

        fun onLinkNameChanged(
            index: Int,
            text: String,
        )

        fun onLinkUrlChanged(
            index: Int,
            text: String,
        )

        fun onClickAddLink()

        fun onClickRemoveLink(index: Int)

        fun onLikesPublicChanged(likesPublic: Boolean)

        fun onClickSave()

        fun onClickRetry()

        fun onClickBack()
    }
}
