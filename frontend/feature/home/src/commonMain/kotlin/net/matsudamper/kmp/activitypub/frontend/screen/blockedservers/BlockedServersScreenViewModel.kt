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
                unblockingHosts = setOf(),
            ),
        )

    private val listener =
        object : BlockedServersScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch { events.send { it.navigateBack() } }
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
                is UserBlockedServersResult.Failure -> ServersState.Failed(result.message)
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

    private fun unblock(host: String) {
        if (host in viewModelStateFlow.value.unblockingHosts) return

        viewModelStateFlow.update { it.copy(unblockingHosts = it.unblockingHosts + host) }
        viewModelScope.launch {
            val error =
                try {
                    api.unblockServer(host)
                } finally {
                    viewModelStateFlow.update { it.copy(unblockingHosts = it.unblockingHosts - host) }
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
                            unblockButtonEnabled = host !in viewModelState.unblockingHosts,
                            listener = object : BlockedServersScreenUiState.ServerListener {
                                override fun onClickUnblock() {
                                    unblock(host)
                                }
                            },
                        )
                    },
                    emptyMessageVisible = servers.hosts.isEmpty(),
                )

                is ServersState.Failed -> BlockedServersScreenUiState.Content.Error(servers.message)
            },
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val servers: ServersState,
        val serverInput: String,
        val blocking: Boolean,
        val unblockingHosts: Set<String>,
    )

    private sealed interface ServersState {
        data object Loading : ServersState

        /**
         * @param hosts 新しくブロックした順
         */
        data class Loaded(val hosts: List<String>) : ServersState

        data class Failed(val message: String) : ServersState
    }
}
