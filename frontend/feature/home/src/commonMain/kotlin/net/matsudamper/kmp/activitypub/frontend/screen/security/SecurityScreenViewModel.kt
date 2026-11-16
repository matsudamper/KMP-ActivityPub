package net.matsudamper.kmp.activitypub.frontend.screen.security

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyApi
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyListResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyOptionsResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyRegistrationResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasswordDeleteResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.RegisteredPasskey
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SecurityScreenViewModel(
    private val passkeyApi: PasskeyApi,
    private val passkeyAuthenticator: PasskeyAuthenticator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                passkeys = PasskeysState.Loading,
                passkeyName = savedStateHandle.get<String>(PASSKEY_NAME_KEY).orEmpty(),
                addDialogVisible = false,
                registering = false,
                passkeyDeleteDialog = PasskeyDeleteDialogState.Hidden,
                passwordDeleteDialog = PasswordDeleteDialogState.Hidden,
            ),
        )

    private val listener =
        object : SecurityScreenUiState.Listener {
            override fun onPasskeyNameChanged(text: String) {
                savedStateHandle[PASSKEY_NAME_KEY] = text
                viewModelStateFlow.update { it.copy(passkeyName = text) }
            }

            override fun onClickAddPasskey() {
                if (viewModelStateFlow.value.busy()) return
                viewModelStateFlow.update { it.copy(addDialogVisible = true) }
            }

            override fun onClickConfirmAdd() {
                addPasskey()
            }

            override fun onDismissAddDialog() {
                if (viewModelStateFlow.value.registering) return
                viewModelStateFlow.update { it.copy(addDialogVisible = false) }
            }

            override fun onClickConfirmDelete() {
                deletePasskey()
            }

            override fun onDismissDeleteDialog() {
                if (viewModelStateFlow.value.passkeyDeleteDialog is PasskeyDeleteDialogState.Deleting) return
                viewModelStateFlow.update { it.copy(passkeyDeleteDialog = PasskeyDeleteDialogState.Hidden) }
            }

            override fun onClickDeletePassword() {
                requestDeletePassword()
            }

            override fun onClickConfirmDeletePassword() {
                deletePassword()
            }

            override fun onDismissDeletePasswordDialog() {
                if (viewModelStateFlow.value.passwordDeleteDialog == PasswordDeleteDialogState.Deleting) return
                viewModelStateFlow.update { it.copy(passwordDeleteDialog = PasswordDeleteDialogState.Hidden) }
            }

            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }
        }

    val uiStateFlow: StateFlow<SecurityScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            loadPasskeys()
        }
    }

    private suspend fun loadPasskeys() {
        val passkeys =
            when (val result = passkeyApi.passkeys()) {
                is PasskeyListResult.Success -> PasskeysState.Loaded(passkeys = result.passkeys, hasPassword = result.hasPassword)
                PasskeyListResult.NotLoggedIn -> PasskeysState.Error(NOT_LOGGED_IN_MESSAGE)
                is PasskeyListResult.Failure -> PasskeysState.Error(result.message)
            }
        viewModelStateFlow.update { it.copy(passkeys = passkeys) }
    }

    private fun addPasskey() {
        val state = viewModelStateFlow.value
        if (state.addable().not()) return

        viewModelStateFlow.update { it.copy(registering = true) }
        viewModelScope.launch {
            val outcome =
                try {
                    registerPasskey(name = state.passkeyName.trim())
                } finally {
                    viewModelStateFlow.update { it.copy(registering = false) }
                }
            if (outcome == Outcome.Added) {
                savedStateHandle.remove<String>(PASSKEY_NAME_KEY)
                viewModelStateFlow.update { it.copy(passkeyName = "", addDialogVisible = false) }
                loadPasskeys()
            }
            showOutcome(outcome)
        }
    }

    /**
     * @return 閉じられたときは何も出さないので null
     */
    private suspend fun registerPasskey(name: String): Outcome? {
        val request =
            when (val options = passkeyApi.registrationOptions()) {
                is PasskeyOptionsResult.Success -> options.options
                PasskeyOptionsResult.NotLoggedIn -> return Outcome.Error(NOT_LOGGED_IN_MESSAGE)
                is PasskeyOptionsResult.Failure -> return Outcome.Error(options.message)
            }
        val creation =
            when (val result = passkeyAuthenticator.create(request)) {
                is PasskeyAuthenticator.Result.Success -> result.value
                PasskeyAuthenticator.Result.Cancelled -> return null
                is PasskeyAuthenticator.Result.Failure -> return Outcome.Error(result.message)
            }
        return when (val result = passkeyApi.register(name = name, creation = creation)) {
            PasskeyRegistrationResult.Success -> Outcome.Added
            PasskeyRegistrationResult.NotLoggedIn -> Outcome.Error(NOT_LOGGED_IN_MESSAGE)
            PasskeyRegistrationResult.NotAccepted -> Outcome.Error("パスキーを確認できなかった。もう一度試す")
            PasskeyRegistrationResult.AlreadyRegistered -> Outcome.Error("このパスキーは追加済み")
            PasskeyRegistrationResult.InvalidName -> Outcome.Error("名前が長すぎる")
            is PasskeyRegistrationResult.Failure -> Outcome.Error(result.message)
        }
    }

    private fun requestDelete(passkey: RegisteredPasskey) {
        if (viewModelStateFlow.value.passkeyDeleteDialog is PasskeyDeleteDialogState.Deleting) return
        viewModelStateFlow.update { it.copy(passkeyDeleteDialog = PasskeyDeleteDialogState.Confirming(passkey)) }
    }

    private fun deletePasskey() {
        val confirming = viewModelStateFlow.value.passkeyDeleteDialog as? PasskeyDeleteDialogState.Confirming ?: return
        val target = confirming.target

        viewModelStateFlow.update { it.copy(passkeyDeleteDialog = PasskeyDeleteDialogState.Deleting(target)) }
        viewModelScope.launch {
            val outcome =
                try {
                    when (val result = passkeyApi.delete(target.id)) {
                        PasskeyDeleteResult.Success -> Outcome.Deleted

                        PasskeyDeleteResult.NotLoggedIn -> Outcome.Error(NOT_LOGGED_IN_MESSAGE)

                        // 別の画面で消してあった。一覧を読み直せば消える
                        PasskeyDeleteResult.NotFound -> Outcome.Deleted

                        PasskeyDeleteResult.LastLoginMethod -> Outcome.Error("パスワードが無いので、最後のパスキーは削除できない")

                        is PasskeyDeleteResult.Failure -> Outcome.Error(result.message)
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(passkeyDeleteDialog = PasskeyDeleteDialogState.Hidden) }
                }
            // 失敗でも、別の端末でパスワードやパスキーが変わっていれば表示が古いままになる
            loadPasskeys()
            showOutcome(outcome)
        }
    }

    private fun requestDeletePassword() {
        if (viewModelStateFlow.value.busy()) return
        viewModelStateFlow.update { it.copy(passwordDeleteDialog = PasswordDeleteDialogState.Confirming) }
    }

    private fun deletePassword() {
        if (viewModelStateFlow.value.passwordDeleteDialog != PasswordDeleteDialogState.Confirming) return

        viewModelStateFlow.update { it.copy(passwordDeleteDialog = PasswordDeleteDialogState.Deleting) }
        viewModelScope.launch {
            val outcome =
                try {
                    when (val result = passkeyApi.deletePassword()) {
                        PasswordDeleteResult.Success -> Outcome.PasswordDeleted
                        PasswordDeleteResult.NotLoggedIn -> Outcome.PasswordError(NOT_LOGGED_IN_MESSAGE)
                        PasswordDeleteResult.NoPasskey -> Outcome.PasswordError("パスキーが無いので削除できない")
                        is PasswordDeleteResult.Failure -> Outcome.PasswordError(result.message)
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(passwordDeleteDialog = PasswordDeleteDialogState.Hidden) }
                }
            loadPasskeys()
            showOutcome(outcome)
        }
    }

    private suspend fun showOutcome(outcome: Outcome?) {
        val message =
            when (outcome) {
                null -> return
                Outcome.Added -> "パスキーを追加した"
                Outcome.Deleted -> "パスキーを削除した"
                is Outcome.Error -> outcome.message
                Outcome.PasswordDeleted -> "パスワードを削除した"
                is Outcome.PasswordError -> outcome.message
            }
        events.send { it.showMessage(message) }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
    ): SecurityScreenUiState {
        val passkeys = viewModelState.passkeys
        val busy = viewModelState.busy()
        return SecurityScreenUiState(
            passkeyList =
            when (passkeys) {
                PasskeysState.Loading -> {
                    SecurityScreenUiState.PasskeyList.Loading
                }

                is PasskeysState.Error -> {
                    SecurityScreenUiState.PasskeyList.Error(passkeys.message)
                }

                is PasskeysState.Loaded -> {
                    if (passkeys.passkeys.isEmpty()) {
                        SecurityScreenUiState.PasskeyList.Empty
                    } else {
                        SecurityScreenUiState.PasskeyList.Items(
                            passkeys.passkeys.map { passkey ->
                                SecurityScreenUiState.PasskeyItem(
                                    name = passkey.name,
                                    deleteButtonEnabled = busy.not(),
                                    listener =
                                    object : SecurityScreenUiState.PasskeyItemListener {
                                        override fun onClickDelete() {
                                            requestDelete(passkey)
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            },
            addPasskeyButtonVisible = passkeyAuthenticator.available,
            addPasskeyButtonEnabled = busy.not(),
            passkeyUnavailableMessageVisible = passkeyAuthenticator.available.not(),
            addDialog =
            if (viewModelState.addDialogVisible) {
                SecurityScreenUiState.AddDialog(
                    passkeyName = viewModelState.passkeyName,
                    nameInputEnabled = viewModelState.registering.not(),
                    addButtonEnabled = viewModelState.addable(),
                    closeEnabled = viewModelState.registering.not(),
                )
            } else {
                null
            },
            deleteDialog =
            when (val dialog = viewModelState.passkeyDeleteDialog) {
                PasskeyDeleteDialogState.Hidden -> null

                is PasskeyDeleteDialogState.Confirming -> SecurityScreenUiState.DeleteDialog(
                    passkeyName = dialog.target.name,
                    deleteButtonEnabled = true,
                )

                is PasskeyDeleteDialogState.Deleting -> SecurityScreenUiState.DeleteDialog(
                    passkeyName = dialog.target.name,
                    deleteButtonEnabled = false,
                )
            },
            password =
            when (passkeys) {
                PasskeysState.Loading, is PasskeysState.Error -> {
                    null
                }

                is PasskeysState.Loaded -> {
                    SecurityScreenUiState.Password(
                        description =
                        when {
                            passkeys.hasPassword.not() -> "パスワードは無い。パスキーでログインする"
                            passkeys.passkeys.isEmpty() -> "パスキーを追加すると、パスワードを削除できる"
                            else -> "パスワードを削除すると、パスキーでしかログインできなくなる"
                        },
                        deleteButtonVisible = passkeys.hasPassword,
                        deleteButtonEnabled = passkeys.passkeys.isNotEmpty() && busy.not(),
                    )
                }
            },
            passwordDeleteDialog =
            when (viewModelState.passwordDeleteDialog) {
                PasswordDeleteDialogState.Hidden -> null
                PasswordDeleteDialogState.Confirming -> SecurityScreenUiState.PasswordDeleteDialog(deleteButtonEnabled = true)
                PasswordDeleteDialogState.Deleting -> SecurityScreenUiState.PasswordDeleteDialog(deleteButtonEnabled = false)
            },
            listener = listener,
        )
    }

    private fun ViewModelState.addable(): Boolean =
        passkeyAuthenticator.available &&
            busy().not() &&
            passkeyName.isNotBlank()

    private fun ViewModelState.busy(): Boolean =
        registering ||
            passkeyDeleteDialog is PasskeyDeleteDialogState.Deleting ||
            passwordDeleteDialog == PasswordDeleteDialogState.Deleting

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val passkeys: PasskeysState,
        val passkeyName: String,
        val addDialogVisible: Boolean,
        val registering: Boolean,
        val passkeyDeleteDialog: PasskeyDeleteDialogState,
        val passwordDeleteDialog: PasswordDeleteDialogState,
    )

    private sealed interface PasskeyDeleteDialogState {
        data object Hidden : PasskeyDeleteDialogState

        data class Confirming(
            val target: RegisteredPasskey,
        ) : PasskeyDeleteDialogState

        data class Deleting(
            val target: RegisteredPasskey,
        ) : PasskeyDeleteDialogState
    }

    private enum class PasswordDeleteDialogState {
        Hidden,
        Confirming,
        Deleting,
    }

    private sealed interface PasskeysState {
        data object Loading : PasskeysState

        data class Loaded(
            val passkeys: List<RegisteredPasskey>,
            val hasPassword: Boolean,
        ) : PasskeysState

        data class Error(
            val message: String,
        ) : PasskeysState
    }

    private sealed interface Outcome {
        data object Added : Outcome

        data object Deleted : Outcome

        data class Error(
            val message: String,
        ) : Outcome

        data object PasswordDeleted : Outcome

        data class PasswordError(
            val message: String,
        ) : Outcome
    }
}

private const val PASSKEY_NAME_KEY = "passkeyName"
private const val NOT_LOGGED_IN_MESSAGE = "ログインし直してから操作する"
