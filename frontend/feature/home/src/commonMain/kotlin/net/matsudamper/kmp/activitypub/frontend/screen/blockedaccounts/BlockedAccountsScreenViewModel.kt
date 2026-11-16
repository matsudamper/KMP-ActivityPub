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
import net.matsudamper.kmp.activitypub.frontend.ui.UnblockDialogUiState
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
                unblock = UnblockState.None,
            ),
        )

    private val listener =
        object : BlockedAccountsScreenUiState.Listener {
            override fun onClickClose() {
                viewModelScope.launch { events.send { it.closeToHome() } }
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

                is UserBlockedAccountsResult.Failure -> {
                    events.send { it.showMessage(result.message) }
                    AccountsState.Failed
                }
            }
            viewModelStateFlow.update { it.copy(accounts = accounts) }
        }
    }

    private val unblockDialogListener =
        object : UnblockDialogUiState.Listener {
            override fun onClickUnblock() {
                unblock()
            }

            override fun onDismiss() {
                viewModelStateFlow.update { state ->
                    if (state.unblock is UnblockState.Confirming) state.copy(unblock = UnblockState.None) else state
                }
            }
        }

    private fun unblock() {
        val account = (viewModelStateFlow.value.unblock as? UnblockState.Confirming)?.account ?: return

        viewModelStateFlow.update { it.copy(unblock = UnblockState.Unblocking(account)) }
        viewModelScope.launch {
            val error =
                try {
                    api.unblockAccount(account.actorUri)
                } finally {
                    viewModelStateFlow.update { it.copy(unblock = UnblockState.None) }
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
                            unblockButtonEnabled = viewModelState.unblock == UnblockState.None,
                            listener = object : BlockedAccountsScreenUiState.AccountListener {
                                override fun onClickUnblock() {
                                    viewModelStateFlow.update { state ->
                                        if (state.unblock == UnblockState.None) state.copy(unblock = UnblockState.Confirming(account)) else state
                                    }
                                }
                            },
                        )
                    },
                    emptyMessageVisible = accounts.accounts.isEmpty(),
                )

                AccountsState.Failed -> BlockedAccountsScreenUiState.Content.LoadFailed
            },
            unblockDialog = when (val unblock = viewModelState.unblock) {
                UnblockState.None -> null
                is UnblockState.Confirming -> createUnblockDialog(account = unblock.account, buttonsEnabled = true)
                is UnblockState.Unblocking -> createUnblockDialog(account = unblock.account, buttonsEnabled = false)
            },
            listener = listener,
        )
    }

    private fun createUnblockDialog(account: UserBlockedAccount, buttonsEnabled: Boolean): UnblockDialogUiState = UnblockDialogUiState(
        message = "@${account.acct} のブロックを解除します。このアカウントの投稿・ブースト・通知がまた届くようになります",
        buttonsEnabled = buttonsEnabled,
        listener = unblockDialogListener,
    )

    internal interface Event {
        fun closeToHome()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val accounts: AccountsState,
        val unblock: UnblockState,
    )

    private sealed interface UnblockState {
        data object None : UnblockState

        data class Confirming(val account: UserBlockedAccount) : UnblockState

        data class Unblocking(val account: UserBlockedAccount) : UnblockState
    }

    private sealed interface AccountsState {
        data object Loading : AccountsState

        /**
         * @param accounts 新しくブロックした順
         */
        data class Loaded(val accounts: List<UserBlockedAccount>) : AccountsState

        data object Failed : AccountsState
    }
}
