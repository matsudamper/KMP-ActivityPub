package net.matsudamper.kmp.activitypub.frontend.screen.blockedaccounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserBlockedAccount
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserBlockedAccountsResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class BlockedAccountsScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                accounts = AccountsState.Loading,
                unblockingActorUris = setOf(),
            ),
        )

    private val listener =
        object : BlockedAccountsScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch { events.send { it.navigateBack() } }
            }

            override fun onClickRetry() {
                load()
            }
        }

    val uiStateFlow: StateFlow<BlockedAccountsScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { uiStateFlow.value = createUiState(it) }
            }
        }.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelStateFlow.update { it.copy(accounts = AccountsState.Loading) }
        viewModelScope.launch {
            val accounts = when (val result = api.blockedAccounts()) {
                is UserBlockedAccountsResult.Success -> AccountsState.Loaded(result.accounts)
                is UserBlockedAccountsResult.Failure -> AccountsState.Failed(result.message)
            }
            viewModelStateFlow.update { it.copy(accounts = accounts) }
        }
    }

    private fun unblock(account: UserBlockedAccount) {
        if (account.actorUri in viewModelStateFlow.value.unblockingActorUris) return

        viewModelStateFlow.update { it.copy(unblockingActorUris = it.unblockingActorUris + account.actorUri) }
        viewModelScope.launch {
            val error =
                try {
                    api.unblockAccount(account.actorUri)
                } finally {
                    viewModelStateFlow.update { it.copy(unblockingActorUris = it.unblockingActorUris - account.actorUri) }
                }
            if (error == null) {
                viewModelStateFlow.update { current ->
                    val accounts = current.accounts
                    current.copy(
                        accounts = if (accounts is AccountsState.Loaded) {
                            AccountsState.Loaded(accounts.accounts.filterNot { it.actorUri == account.actorUri })
                        } else {
                            accounts
                        },
                    )
                }
                events.send { it.showMessage("@${account.acct} のブロックを解除しました") }
            } else {
                events.send { it.showMessage(error) }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): BlockedAccountsScreenUiState {
        return BlockedAccountsScreenUiState(
            content = when (val accounts = viewModelState.accounts) {
                AccountsState.Loading -> BlockedAccountsScreenUiState.Content.Loading

                is AccountsState.Loaded -> BlockedAccountsScreenUiState.Content.Loaded(
                    accounts = accounts.accounts.map { account ->
                        BlockedAccountsScreenUiState.Account(
                            acct = "@${account.acct}",
                            unblockButtonEnabled = account.actorUri !in viewModelState.unblockingActorUris,
                            listener = object : BlockedAccountsScreenUiState.AccountListener {
                                override fun onClickUnblock() {
                                    unblock(account)
                                }
                            },
                        )
                    },
                    emptyMessageVisible = accounts.accounts.isEmpty(),
                )

                is AccountsState.Failed -> BlockedAccountsScreenUiState.Content.Error(accounts.message)
            },
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val accounts: AccountsState,
        val unblockingActorUris: Set<String>,
    )

    private sealed interface AccountsState {
        data object Loading : AccountsState

        /**
         * @param accounts 新しくブロックした順
         */
        data class Loaded(val accounts: List<UserBlockedAccount>) : AccountsState

        data class Failed(val message: String) : AccountsState
    }
}
