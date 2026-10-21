package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val passkeyList: PasskeyList,
    val passkeyNameInputVisible: Boolean,
    val passkeyName: String,
    val passkeyNameInputEnabled: Boolean,
    val addPasskeyButtonVisible: Boolean,
    val addPasskeyButtonEnabled: Boolean,
    val passkeyUnavailableMessageVisible: Boolean,
    val deleteDialog: DeleteDialog?,
    val password: Password?,
    val passwordDeleteDialog: PasswordDeleteDialog?,
    val logoutButtonEnabled: Boolean,
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

        fun onClickConfirmDelete()

        fun onDismissDeleteDialog()

        fun onClickDeletePassword()

        fun onClickConfirmDeletePassword()

        fun onDismissDeletePasswordDialog()

        fun onClickBack()

        fun onClickLogout()
    }
}
