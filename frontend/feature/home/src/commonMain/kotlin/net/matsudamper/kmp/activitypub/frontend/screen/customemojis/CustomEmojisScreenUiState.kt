package net.matsudamper.kmp.activitypub.frontend.screen.customemojis

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

data class CustomEmojisScreenUiState(
    val shortcodeInput: String,
    val shortcodeInputEnabled: Boolean,
    /**
     * 選んだ画像。まだ選んでいなければ null
     */
    val pickedImage: PickedImage?,
    val pickImageButtonEnabled: Boolean,
    val addButtonEnabled: Boolean,
    val content: Content,
    val listener: Listener,
) {
    /**
     * 選び直すたびに作るので、中身ではなく同じものかで比べる
     */
    class PickedImage(val bytes: ByteArray)

    sealed interface Content {
        data object Loading : Content

        /**
         * @param emojis shortcode の順
         */
        data class Loaded(
            val emojis: List<Emoji>,
            val emptyMessageVisible: Boolean,
        ) : Content

        data class Error(
            val message: String,
        ) : Content
    }

    /**
     * @param deleteButton 置いた人だけが消せるので、ほかの人が置いた絵文字は null
     */
    data class Emoji(
        val shortcodeText: String,
        val imageUrl: String,
        val deleteButton: DeleteButton?,
    )

    data class DeleteButton(
        val enabled: Boolean,
        val listener: DeleteButtonListener,
    )

    @Immutable
    interface DeleteButtonListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onChangeShortcodeInput(value: String)

        fun onClickPickImage()

        fun onImagePicked(file: PickedImageFile)

        fun onClickAdd()

        fun onClickRetry()
    }
}
