package net.matsudamper.kmp.activitypub.frontend.screen.admin.deletedremotenotes

import androidx.compose.runtime.Immutable

data class DeletedRemoteNotesScreenUiState(
    val content: Content,
    val purgeAllButtonEnabled: Boolean,
    /**
     * 全て消す前に確かめるダイアログ。出していなければ null
     */
    val purgeAllDialog: PurgeAllDialog?,
    val listener: Listener,
) {
    sealed interface Content {
        data object Loading : Content

        data object LoadFailed : Content

        data object Empty : Content

        /**
         * @param notes 受け取った新しい順
         */
        data class Loaded(
            val notes: List<DeletedNote>,
            val footer: Footer,
        ) : Content
    }

    /**
     * 一覧の末尾。続きの読み込みの状態を出す
     */
    sealed interface Footer {
        data object None : Footer

        data object Loading : Footer

        data object LoadFailed : Footer
    }

    /**
     * @param listKey 続きを読んで行が増えても、同じ投稿を同じ行として扱う
     * @param objectUri 押すとブラウザで開く
     */
    data class DeletedNote(
        val listKey: String,
        val actorText: String,
        val contentText: String,
        val objectUri: String,
        val dateText: String,
        val purgeButtonEnabled: Boolean,
        val listener: DeletedNoteListener,
    )

    data class PurgeAllDialog(
        val message: String,
        val buttonsEnabled: Boolean,
        val listener: PurgeAllDialogListener,
    )

    @Immutable
    interface DeletedNoteListener {
        fun onClickObjectUri()

        fun onClickPurge()
    }

    @Immutable
    interface PurgeAllDialogListener {
        fun onClickPurge()

        fun onDismiss()
    }

    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickRetry()

        fun onScrolledToEnd()

        fun onClickRetryLoadMore()

        fun onClickPurgeAll()
    }
}
