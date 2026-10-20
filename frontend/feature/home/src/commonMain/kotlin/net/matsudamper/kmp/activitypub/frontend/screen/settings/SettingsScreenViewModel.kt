package net.matsudamper.kmp.activitypub.frontend.screen.settings

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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLogoutResult
import org.koin.core.annotation.KoinViewModel

/**
 * ログアウトはまだサーバーと繋いでいない。ボタンを押しても何も起きない
 */
@KoinViewModel
internal class SettingsScreenViewModel(
    private val passkeyApi: PasskeyApi,
    private val passkeyAuthenticator: PasskeyAuthenticator,
    private val savedStateHandle: SavedStateHandle,
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                passkeys = PasskeysState.Loading,
                passkeyName = savedStateHandle.get<String>(PASSKEY_NAME_KEY).orEmpty(),
                registering = false,
                deleteTarget = null,
                deleting = false,
                passwordDeleteDialogVisible = false,
                deletingPassword = false,
                outcome = null,
                loggingOut = false,
                error = null,
            ),
        )

    private val listener =
        object : SettingsScreenUiState.Listener {
            override fun onPasskeyNameChanged(text: String) {
                savedStateHandle[PASSKEY_NAME_KEY] = text
                viewModelStateFlow.update { it.copy(passkeyName = text) }
            }

            override fun onClickAddPasskey() {
                addPasskey()
            }

            override fun onClickConfirmDelete() {
                deletePasskey()
            }

            override fun onDismissDeleteDialog() {
                if (viewModelStateFlow.value.deleting) return
                viewModelStateFlow.update { it.copy(deleteTarget = null) }
            }

            override fun onClickDeletePassword() {
                requestDeletePassword()
            }

            override fun onClickConfirmDeletePassword() {
                deletePassword()
            }

            override fun onDismissDeletePasswordDialog() {
                if (viewModelStateFlow.value.deletingPassword) return
                viewModelStateFlow.update { it.copy(passwordDeleteDialogVisible = false) }
            }

            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }

            override fun onClickLogout() {
                logout()
            }
        }

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
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

        viewModelStateFlow.update {
            it.copy(
                registering = true,
                outcome = null,
            )
        }
        viewModelScope.launch {
            val outcome =
                try {
                    registerPasskey(name = state.passkeyName.trim())
                } finally {
                    viewModelStateFlow.update { it.copy(registering = false) }
                }
            if (outcome == Outcome.Added) {
                savedStateHandle.remove<String>(PASSKEY_NAME_KEY)
                viewModelStateFlow.update { it.copy(passkeyName = "") }
                loadPasskeys()
            }
            viewModelStateFlow.update { it.copy(outcome = outcome) }
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
        if (viewModelStateFlow.value.deleting) return
        viewModelStateFlow.update { it.copy(deleteTarget = passkey, outcome = null) }
    }

    private fun deletePasskey() {
        val target = viewModelStateFlow.value.deleteTarget ?: return
        if (viewModelStateFlow.value.deleting) return

        viewModelStateFlow.update { it.copy(deleting = true) }
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
                    viewModelStateFlow.update { it.copy(deleting = false, deleteTarget = null) }
                }
            if (outcome == Outcome.Deleted) loadPasskeys()
            viewModelStateFlow.update { it.copy(outcome = outcome) }
        }
    }

    private fun requestDeletePassword() {
        if (viewModelStateFlow.value.busy()) return
        viewModelStateFlow.update { it.copy(passwordDeleteDialogVisible = true, outcome = null) }
    }

    private fun deletePassword() {
        if (viewModelStateFlow.value.deletingPassword) return

        viewModelStateFlow.update { it.copy(deletingPassword = true) }
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
                    viewModelStateFlow.update { it.copy(deletingPassword = false, passwordDeleteDialogVisible = false) }
                }
            loadPasskeys()
            viewModelStateFlow.update { it.copy(outcome = outcome) }
        }
    }

    private fun logout() {
        if (viewModelStateFlow.value.loggingOut) return

        viewModelStateFlow.update {
            it.copy(
                loggingOut = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val error =
                try {
                    when (val result = api.logout()) {
                        UserLogoutResult.Success -> null
                        is UserLogoutResult.Failure -> result.message
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(loggingOut = false) }
                }
            viewModelStateFlow.update { it.copy(error = error) }
            if (error == null) {
                events.send { it.loggedOut() }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): SettingsScreenUiState {
        val passkeys = viewModelState.passkeys
        val busy = viewModelState.busy()
        return SettingsScreenUiState(
            passkeyList =
            when (passkeys) {
                PasskeysState.Loading -> {
                    SettingsScreenUiState.PasskeyList.Loading
                }

                is PasskeysState.Error -> {
                    SettingsScreenUiState.PasskeyList.Error(passkeys.message)
                }

                is PasskeysState.Loaded -> {
                    if (passkeys.passkeys.isEmpty()) {
                        SettingsScreenUiState.PasskeyList.Empty
                    } else {
                        SettingsScreenUiState.PasskeyList.Items(
                            passkeys.passkeys.map { passkey ->
                                SettingsScreenUiState.PasskeyItem(
                                    name = passkey.name,
                                    deleteButtonEnabled = busy.not(),
                                    listener =
                                    object : SettingsScreenUiState.PasskeyItemListener {
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
            passkeyNameInputVisible = passkeyAuthenticator.available,
            passkeyName = viewModelState.passkeyName,
            passkeyNameInputEnabled = busy.not(),
            addPasskeyButtonVisible = passkeyAuthenticator.available,
            addPasskeyButtonEnabled = viewModelState.addable(),
            passkeyMessage =
            when (val outcome = viewModelState.outcome) {
                null -> {
                    if (passkeyAuthenticator.available) {
                        null
                    } else {
                        SettingsScreenUiState.PasskeyMessage(text = "この端末ではパスキーを追加できない", isError = false)
                    }
                }

                Outcome.Added -> SettingsScreenUiState.PasskeyMessage(text = "パスキーを追加した", isError = false)

                Outcome.Deleted -> SettingsScreenUiState.PasskeyMessage(text = "パスキーを削除した", isError = false)

                is Outcome.Error -> SettingsScreenUiState.PasskeyMessage(text = outcome.message, isError = true)

                Outcome.PasswordDeleted, is Outcome.PasswordError -> null
            },
            deleteDialog =
            viewModelState.deleteTarget?.let { target ->
                SettingsScreenUiState.DeleteDialog(
                    passkeyName = target.name,
                    deleteButtonEnabled = viewModelState.deleting.not(),
                )
            },
            password =
            when (passkeys) {
                PasskeysState.Loading, is PasskeysState.Error -> {
                    null
                }

                is PasskeysState.Loaded -> {
                    SettingsScreenUiState.Password(
                        description =
                        when {
                            passkeys.hasPassword.not() -> "パスワードは無い。パスキーでログインする"
                            passkeys.passkeys.isEmpty() -> "パスキーを追加すると、パスワードを削除できる"
                            else -> "パスワードを削除すると、パスキーでしかログインできなくなる"
                        },
                        deleteButtonVisible = passkeys.hasPassword,
                        deleteButtonEnabled = passkeys.passkeys.isNotEmpty() && busy.not(),
                        message =
                        when (val outcome = viewModelState.outcome) {
                            Outcome.PasswordDeleted -> SettingsScreenUiState.PasswordMessage(text = "パスワードを削除した", isError = false)
                            is Outcome.PasswordError -> SettingsScreenUiState.PasswordMessage(text = outcome.message, isError = true)
                            null, Outcome.Added, Outcome.Deleted, is Outcome.Error -> null
                        },
                    )
                }
            },
            passwordDeleteDialog =
            if (viewModelState.passwordDeleteDialogVisible) {
                SettingsScreenUiState.PasswordDeleteDialog(deleteButtonEnabled = viewModelState.deletingPassword.not())
            } else {
                null
            },
            logoutButtonEnabled = viewModelState.loggingOut.not(),
            error = viewModelState.error,
            listener = listener,
        )
    }

    private fun ViewModelState.addable(): Boolean =
        passkeyAuthenticator.available &&
            busy().not() &&
            passkeyName.isNotBlank()

    private fun ViewModelState.busy(): Boolean = registering || deleting || deletingPassword

    data class ViewModelState(
        val passkeys: PasskeysState,
        val passkeyName: String,
        val registering: Boolean,
        val deleteTarget: RegisteredPasskey?,
        val deleting: Boolean,
        val passwordDeleteDialogVisible: Boolean,
        val deletingPassword: Boolean,
        val outcome: Outcome?,
        val loggingOut: Boolean,
        val error: String?,
    )

    sealed interface PasskeysState {
        data object Loading : PasskeysState

        data class Loaded(
            val passkeys: List<RegisteredPasskey>,
            val hasPassword: Boolean,
        ) : PasskeysState

        data class Error(
            val message: String,
        ) : PasskeysState
    }

    sealed interface Outcome {
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

    interface Event {
        fun navigateBack()

        fun loggedOut()
    }
}

private const val PASSKEY_NAME_KEY = "passkeyName"
private const val NOT_LOGGED_IN_MESSAGE = "ログインし直してから操作する"
