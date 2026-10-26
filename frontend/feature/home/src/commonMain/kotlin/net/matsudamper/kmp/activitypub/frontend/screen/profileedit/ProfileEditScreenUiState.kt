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
         */
        data class Loaded(
            val iconPreview: ImageBitmap?,
            val iconUploading: Boolean,
            val currentIconUrl: String?,
            val iconSelectButtonEnabled: Boolean,
            val displayName: String,
            val displayNameInputEnabled: Boolean,
            val displayNameHasError: Boolean,
            val saveButtonEnabled: Boolean,
        ) : Content
    }

    @Immutable
    interface Listener {
        fun onClickSelectIcon()

        fun onIconPicked(file: PickedImageFile)

        fun onDisplayNameChanged(text: String)

        fun onClickSave()

        fun onClickRetry()

        fun onClickBack()
    }
}
