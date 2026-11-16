package net.matsudamper.kmp.activitypub.frontend.screen.security

import androidx.compose.runtime.Immutable

data class SecurityScreenUiState(
    val passkeyList: PasskeyList,
    val addPasskeyButtonVisible: Boolean,
    val addPasskeyButtonEnabled: Boolean,
    val passkeyUnavailableMessageVisible: Boolean,
    val addDialog: AddDialog?,
    val deleteDialog: DeleteDialog?,
    val password: Password?,
    val passwordDeleteDialog: PasswordDeleteDialog?,
    val listener: Listener,
) {
    sealed interface PasskeyList {
        data object Loading : PasskeyList

        data object Empty : PasskeyList

        data class Error(
            val message: String,
        ) : PasskeyList

        data class Items(
            val items: List<PasskeyItem>,
        ) : PasskeyList
    }

    data class PasskeyItem(
        val name: String,
        val deleteButtonEnabled: Boolean,
        val listener: PasskeyItemListener,
    )

    data class AddDialog(
        val passkeyName: String,
        val nameInputEnabled: Boolean,
        val addButtonEnabled: Boolean,
        /** 登録の途中で閉じると、結果を受け取る先が無くなるので false になる */
        val closeEnabled: Boolean,
    )

    data class DeleteDialog(
        val passkeyName: String,
        val deleteButtonEnabled: Boolean,
    )

    data class Password(
        val description: String,
        val deleteButtonVisible: Boolean,
        val deleteButtonEnabled: Boolean,
    )

    data class PasswordDeleteDialog(
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

        fun onClickConfirmAdd()

        fun onDismissAddDialog()

        fun onClickConfirmDelete()

        fun onDismissDeleteDialog()

        fun onClickDeletePassword()

        fun onClickConfirmDeletePassword()

        fun onDismissDeletePasswordDialog()

        fun onClickBack()
    }
}
