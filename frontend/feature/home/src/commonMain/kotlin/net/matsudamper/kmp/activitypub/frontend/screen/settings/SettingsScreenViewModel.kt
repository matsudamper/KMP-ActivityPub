package net.matsudamper.kmp.activitypub.frontend.screen.settings

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
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticatorResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyOptionsResult
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyRegistrationResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsScreenViewModel(
    private val passkeyApi: PasskeyApi,
    private val passkeyAuthenticator: PasskeyAuthenticator,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                registering = false,
                outcome = null,
            ),
        )

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener =
                object : SettingsScreenUiState.Listener {
                    override fun onClickAddPasskey() {
                        addPasskey()
                    }

                    override fun onClickBack() {
                        viewModelScope.launch {
                            events.send { it.navigateBack() }
                        }
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        createUiState(
                            viewModelState = viewModelState,
                            listener = uiState.listener,
                        )
                    }
                }
            }
        }.asStateFlow()

    private fun addPasskey() {
        if (viewModelStateFlow.value.registering) return

        viewModelStateFlow.update {
            it.copy(
                registering = true,
                outcome = null,
            )
        }
        viewModelScope.launch {
            val outcome =
                try {
                    registerPasskey()
                } finally {
                    viewModelStateFlow.update { it.copy(registering = false) }
                }
            viewModelStateFlow.update { it.copy(outcome = outcome) }
        }
    }

    /**
     * @return 閉じられたときは何も出さないので null
     */
    private suspend fun registerPasskey(): RegistrationOutcome? {
        val request =
            when (val options = passkeyApi.registrationOptions()) {
                is PasskeyOptionsResult.Success -> options.options
                PasskeyOptionsResult.NotLoggedIn -> return RegistrationOutcome.Error(NOT_LOGGED_IN_MESSAGE)
                is PasskeyOptionsResult.Failure -> return RegistrationOutcome.Error(options.message)
            }
        val creation =
            when (val result = passkeyAuthenticator.create(request)) {
                is PasskeyAuthenticatorResult.Success -> result.value
                PasskeyAuthenticatorResult.Cancelled -> return null
                is PasskeyAuthenticatorResult.Failure -> return RegistrationOutcome.Error(result.message)
            }
        return when (val result = passkeyApi.register(creation)) {
            PasskeyRegistrationResult.Success -> RegistrationOutcome.Added
            PasskeyRegistrationResult.NotLoggedIn -> RegistrationOutcome.Error(NOT_LOGGED_IN_MESSAGE)
            PasskeyRegistrationResult.NotAccepted -> RegistrationOutcome.Error("パスキーを確認できなかった。もう一度試す")
            PasskeyRegistrationResult.AlreadyRegistered -> RegistrationOutcome.Error("このパスキーは追加済み")
            is PasskeyRegistrationResult.Failure -> RegistrationOutcome.Error(result.message)
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: SettingsScreenUiState.Listener,
    ): SettingsScreenUiState {
        val passkeyMessage =
            when {
                passkeyAuthenticator.available.not() -> {
                    SettingsScreenUiState.PasskeyMessage(text = "この端末ではパスキーを追加できない", isError = false)
                }

                else -> {
                    when (val outcome = viewModelState.outcome) {
                        null -> null
                        RegistrationOutcome.Added -> SettingsScreenUiState.PasskeyMessage(text = "パスキーを追加した", isError = false)
                        is RegistrationOutcome.Error -> SettingsScreenUiState.PasskeyMessage(text = outcome.message, isError = true)
                    }
                }
            }
        return SettingsScreenUiState(
            addPasskeyButtonVisible = passkeyAuthenticator.available,
            addPasskeyButtonEnabled = viewModelState.registering.not(),
            passkeyMessage = passkeyMessage,
            listener = listener,
        )
    }

    data class ViewModelState(
        val registering: Boolean,
        val outcome: RegistrationOutcome?,
    )

    sealed interface RegistrationOutcome {
        data object Added : RegistrationOutcome

        data class Error(
            val message: String,
        ) : RegistrationOutcome
    }

    interface Event {
        fun navigateBack()
    }
}

private const val NOT_LOGGED_IN_MESSAGE = "ログインし直してから追加する"
