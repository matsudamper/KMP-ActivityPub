package net.matsudamper.kmp.activitypub.frontend.screen.blockedservers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserBlockServerResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserBlockedServersResult
import net.matsudamper.kmp.activitypub.frontend.ui.UnblockDialogUiState
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class BlockedServersScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                servers = ServersState.Loading,
                serverInput = "",
                blocking = false,
                unblock = UnblockState.None,
            ),
        )

    private val listener =
        object : BlockedServersScreenUiState.Listener {
            override fun onClickClose() {
                viewModelScope.launch { events.send { it.closeToHome() } }
            }

            override fun onChangeServerInput(value: String) {
                viewModelStateFlow.update { it.copy(serverInput = value) }
            }

            override fun onClickBlock() {
                block()
            }

            override fun onClickRetry() {
                load()
            }
        }

    val uiStateFlow: StateFlow<BlockedServersScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { uiStateFlow.value = createUiState(it) }
            }
        }.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelStateFlow.update { it.copy(servers = ServersState.Loading) }
        viewModelScope.launch {
            val servers = when (val result = api.blockedServers()) {
                is UserBlockedServersResult.Success -> ServersState.Loaded(result.hosts)

                is UserBlockedServersResult.Failure -> {
                    events.send { it.showMessage(result.message) }
                    ServersState.Failed
                }
            }
            viewModelStateFlow.update { it.copy(servers = servers) }
        }
    }

    private fun block() {
        val state = viewModelStateFlow.value
        if (state.blocking || state.serverInput.isBlank()) return

        viewModelStateFlow.update { it.copy(blocking = true) }
        viewModelScope.launch {
            val result =
                try {
                    api.blockServer(state.serverInput)
                } finally {
                    viewModelStateFlow.update { it.copy(blocking = false) }
                }
            when (result) {
                is UserBlockServerResult.Blocked -> {
                    viewModelStateFlow.update { current ->
                        val servers = current.servers
                        current.copy(
                            serverInput = "",
                            servers = if (servers is ServersState.Loaded) {
                                ServersState.Loaded(listOf(result.host) + servers.hosts.filterNot { it == result.host })
                            } else {
                                servers
                            },
                        )
                    }
                    events.send { it.showMessage("${result.host} をブロックしました") }
                }

                is UserBlockServerResult.Failure -> events.send { it.showMessage(result.message) }
            }
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
        val host = (viewModelStateFlow.value.unblock as? UnblockState.Confirming)?.host ?: return

        viewModelStateFlow.update { it.copy(unblock = UnblockState.Unblocking(host)) }
        viewModelScope.launch {
            val error =
                try {
                    api.unblockServer(host)
                } finally {
                    viewModelStateFlow.update { it.copy(unblock = UnblockState.None) }
                }
            if (error == null) {
                viewModelStateFlow.update { current ->
                    val servers = current.servers
                    current.copy(servers = if (servers is ServersState.Loaded) ServersState.Loaded(servers.hosts - host) else servers)
                }
                events.send { it.showMessage("$host のブロックを解除しました") }
            } else {
                events.send { it.showMessage(error) }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): BlockedServersScreenUiState {
        return BlockedServersScreenUiState(
            serverInput = viewModelState.serverInput,
            serverInputEnabled = viewModelState.blocking.not(),
            blockButtonEnabled = viewModelState.blocking.not() && viewModelState.serverInput.isNotBlank(),
            content = when (val servers = viewModelState.servers) {
                ServersState.Loading -> BlockedServersScreenUiState.Content.Loading

                is ServersState.Loaded -> BlockedServersScreenUiState.Content.Loaded(
                    servers = servers.hosts.map { host ->
                        BlockedServersScreenUiState.Server(
                            host = host,
                            unblockButtonEnabled = viewModelState.unblock == UnblockState.None,
                            listener = object : BlockedServersScreenUiState.ServerListener {
                                override fun onClickUnblock() {
                                    viewModelStateFlow.update { state ->
                                        if (state.unblock == UnblockState.None) state.copy(unblock = UnblockState.Confirming(host)) else state
                                    }
                                }
                            },
                        )
                    },
                    emptyMessageVisible = servers.hosts.isEmpty(),
                )

                ServersState.Failed -> BlockedServersScreenUiState.Content.LoadFailed
            },
            unblockDialog = when (val unblock = viewModelState.unblock) {
                UnblockState.None -> null
                is UnblockState.Confirming -> createUnblockDialog(host = unblock.host, buttonsEnabled = true)
                is UnblockState.Unblocking -> createUnblockDialog(host = unblock.host, buttonsEnabled = false)
            },
            listener = listener,
        )
    }

    private fun createUnblockDialog(host: String, buttonsEnabled: Boolean): UnblockDialogUiState = UnblockDialogUiState(
        message = "$host のブロックを解除します。このサーバーのユーザーの投稿・ブースト・通知がまた届くようになります",
        buttonsEnabled = buttonsEnabled,
        listener = unblockDialogListener,
    )

    internal interface Event {
        fun closeToHome()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val servers: ServersState,
        val serverInput: String,
        val blocking: Boolean,
        val unblock: UnblockState,
    )

    private sealed interface UnblockState {
        data object None : UnblockState

        data class Confirming(val host: String) : UnblockState

        data class Unblocking(val host: String) : UnblockState
    }

    private sealed interface ServersState {
        data object Loading : ServersState

        /**
         * @param hosts 新しくブロックした順
         */
        data class Loaded(val hosts: List<String>) : ServersState

        data object Failed : ServersState
    }
}
