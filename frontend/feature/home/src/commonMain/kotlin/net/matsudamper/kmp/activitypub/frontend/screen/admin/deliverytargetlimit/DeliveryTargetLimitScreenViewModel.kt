package net.matsudamper.kmp.activitypub.frontend.screen.admin.deliverytargetlimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserDeliveryTargetHost
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserDeliveryTargetLimitResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class DeliveryTargetLimitScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                limit = LimitState.Loading,
            ),
        )
    private var loadJob: Job? = null

    private val listener =
        object : DeliveryTargetLimitScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch { events.send { it.navigateBack() } }
            }

            override fun onClickRetry() {
                load()
            }
        }

    val uiStateFlow: StateFlow<DeliveryTargetLimitScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { uiStateFlow.value = createUiState(it) }
            }
        }.asStateFlow()

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.update { it.copy(limit = LimitState.Loading) }
        loadJob = viewModelScope.launch {
            when (val result = api.deliveryTargetLimit()) {
                is UserDeliveryTargetLimitResult.Success -> viewModelStateFlow.update {
                    it.copy(limit = LimitState.Loaded(maxPerHost = result.maxPerHost, hosts = result.hosts))
                }

                UserDeliveryTargetLimitResult.NotLoggedIn -> showLoadFailure("ログインしていません")

                is UserDeliveryTargetLimitResult.Failure -> showLoadFailure(result.message)
            }
        }
    }

    private suspend fun showLoadFailure(message: String) {
        viewModelStateFlow.update { it.copy(limit = LimitState.Failed) }
        events.send { it.showMessage(message) }
    }

    private fun createUiState(viewModelState: ViewModelState): DeliveryTargetLimitScreenUiState {
        return DeliveryTargetLimitScreenUiState(
            content = when (val limit = viewModelState.limit) {
                LimitState.Loading -> DeliveryTargetLimitScreenUiState.Content.Loading

                LimitState.Failed -> DeliveryTargetLimitScreenUiState.Content.LoadFailed

                is LimitState.Loaded -> DeliveryTargetLimitScreenUiState.Content.Loaded(
                    limitText = limit.maxPerHost.toString(),
                    hosts = limit.hosts.map { createHost(host = it, maxPerHost = limit.maxPerHost) },
                    emptyMessageVisible = limit.hosts.isEmpty(),
                )
            },
            listener = listener,
        )
    }

    private fun createHost(host: UserDeliveryTargetHost, maxPerHost: Int): DeliveryTargetLimitScreenUiState.Host {
        // 上限ちょうどでも、配る先が増えるフォローは断られる
        val limitReached = host.deliveryTargetCount >= maxPerHost
        return DeliveryTargetLimitScreenUiState.Host(
            host = host.host,
            countText = "${host.deliveryTargetCount} / $maxPerHost",
            rowHighlighted = limitReached,
            warningText = if (limitReached) "上限に達しています。配る先が増えるフォローは断ります" else null,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val limit: LimitState,
    )

    private sealed interface LimitState {
        data object Loading : LimitState

        data object Failed : LimitState

        /**
         * @param hosts 配る先の数の多い順
         */
        data class Loaded(
            val maxPerHost: Int,
            val hosts: List<UserDeliveryTargetHost>,
        ) : LimitState
    }
}
