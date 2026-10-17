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
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.RegisteredPasskey
import org.koin.core.annotation.KoinViewModel

/**
 * ログアウトはまだサーバーと繋いでいない。ボタンを押しても何も起きない
 */
@KoinViewModel
internal class SettingsScreenViewModel(
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
                registering = false,
                deleteTarget = null,
                deleting = false,
                outcome = null,
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

            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }

            override fun onClickLogout() = Unit
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
                is PasskeyListResult.Success -> PasskeysState.Loaded(result.passkeys)
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

                        is PasskeyDeleteResult.Failure -> Outcome.Error(result.message)
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(deleting = false, deleteTarget = null) }
                }
            if (outcome == Outcome.Deleted) loadPasskeys()
            viewModelStateFlow.update { it.copy(outcome = outcome) }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): SettingsScreenUiState {
        val passkeys = viewModelState.passkeys
        val busy = viewModelState.registering || viewModelState.deleting
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
            },
            deleteDialog =
            viewModelState.deleteTarget?.let { target ->
                SettingsScreenUiState.DeleteDialog(
                    passkeyName = target.name,
                    deleteButtonEnabled = viewModelState.deleting.not(),
                )
            },
            listener = listener,
        )
    }

    private fun ViewModelState.addable(): Boolean =
        passkeyAuthenticator.available &&
            registering.not() &&
            deleting.not() &&
            passkeyName.isNotBlank()

    data class ViewModelState(
        val passkeys: PasskeysState,
        val passkeyName: String,
        val registering: Boolean,
        val deleteTarget: RegisteredPasskey?,
        val deleting: Boolean,
        val outcome: Outcome?,
    )

    sealed interface PasskeysState {
        data object Loading : PasskeysState

        data class Loaded(
            val passkeys: List<RegisteredPasskey>,
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
    }

    interface Event {
        fun navigateBack()
    }
}

private const val PASSKEY_NAME_KEY = "passkeyName"
private const val NOT_LOGGED_IN_MESSAGE = "ログインし直してから操作する"
