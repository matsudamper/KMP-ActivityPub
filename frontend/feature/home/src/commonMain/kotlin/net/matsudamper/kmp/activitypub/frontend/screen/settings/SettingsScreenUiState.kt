package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val passkeyItems: List<PasskeyItem>,
    /**
     * 読み込み中・0 件・読み込みの失敗を、一覧の代わりに出す
     */
    val passkeyListText: String?,
    val passkeyNameInputVisible: Boolean,
    val passkeyName: String,
    val passkeyNameInputEnabled: Boolean,
    val addPasskeyButtonVisible: Boolean,
    val addPasskeyButtonEnabled: Boolean,
    val passkeyMessage: PasskeyMessage?,
    val deleteDialog: DeleteDialog?,
    val listener: Listener,
) {
    data class PasskeyItem(
        val name: String,
        val deleteButtonEnabled: Boolean,
        val listener: PasskeyItemListener,
    )

    data class PasskeyMessage(
        val text: String,
        val isError: Boolean,
    )

    data class DeleteDialog(
        val passkeyName: String,
        val deleteButtonEnabled: Boolean,
    )

    @Immutable
    interface PasskeyItemListener {
        fun onClickDelete()
    }

    @Immutable
    interface Listener {
        fun onPasskeyNameChanged(text: String)

        fun onClickAddPasskey()

        fun onClickConfirmDelete()

        fun onDismissDeleteDialog()

        fun onClickBack()
    }
}
