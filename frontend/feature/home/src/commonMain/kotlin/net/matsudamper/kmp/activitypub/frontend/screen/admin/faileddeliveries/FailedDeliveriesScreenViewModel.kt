package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddeliveries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.graphql.FailedDeliveriesQuery
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFailedDeliveriesResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFailedDelivery
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserFailedDeliveriesResult
import net.matsudamper.kmp.activitypub.frontend.screen.admin.createFailedDeliveryKindText
import net.matsudamper.kmp.activitypub.frontend.screen.createFullPostedAtText
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class FailedDeliveriesScreenViewModel(
    userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ListState> = MutableStateFlow(ListState.Loading)
    private var loadJob: Job? = null
    private val paging = userApi.failedDeliveriesPaging()

    private val listener = object : FailedDeliveriesScreenUiState.Listener {
        override fun onClickBack() {
            viewModelScope.launch {
                events.send { it.navigateBack() }
            }
        }

        override fun onClickRetry() {
            load()
        }

        override fun onScrolledToEnd() {
            // 失敗した後に自動で読み直すと、末尾にいる間は失敗を繰り返すので、ボタンを押したときだけ読み直す
            val loaded = viewModelStateFlow.value as? ListState.Loaded ?: return
            if (loaded.loadMoreFailed) return
            loadMore()
        }

        override fun onClickRetryLoadMore() {
            loadMore()
        }
    }

    val uiStateFlow: StateFlow<FailedDeliveriesScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { listState ->
                    uiStateFlow.value = createUiState(listState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            paging.watch().collect { data -> applyResult(data.toUserFailedDeliveriesResult()) }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = ListState.Loading
        loadJob = viewModelScope.launch {
            when (val outcome = paging.refresh().toOutcome()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)
                is PagingOutcome.Failed -> showLoadFailure(outcome.message)
                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    private fun loadMore() {
        if (loadJob?.isActive == true) return
        val loaded = viewModelStateFlow.value as? ListState.Loaded ?: return
        if (loaded.nextCursor == null) return
        viewModelStateFlow.value = loaded.copy(loadMoreFailed = false)
        loadJob = viewModelScope.launch {
            when (val outcome = paging.fetchNext().toOutcome()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)

                is PagingOutcome.Failed -> {
                    viewModelStateFlow.update { current ->
                        if (current is ListState.Loaded) current.copy(loadMoreFailed = true) else current
                    }
                    events.send { it.showMessage(outcome.message) }
                }

                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applyResult(result: UserFailedDeliveriesResult) {
        when (result) {
            is UserFailedDeliveriesResult.Success -> viewModelStateFlow.update { current ->
                ListState.Loaded(
                    deliveries = result.deliveries,
                    nextCursor = result.nextCursor,
                    loadMoreFailed = (current as? ListState.Loaded)?.loadMoreFailed ?: false,
                )
            }

            UserFailedDeliveriesResult.NotLoggedIn -> showLoadFailure(NOT_LOGGED_IN_MESSAGE)
        }
    }

    private fun showLoadFailure(message: String) {
        viewModelStateFlow.value = ListState.Failed
        viewModelScope.launch {
            events.send { it.showMessage(message) }
        }
    }

    private fun createUiState(listState: ListState): FailedDeliveriesScreenUiState {
        return FailedDeliveriesScreenUiState(
            content = when (listState) {
                ListState.Loading -> FailedDeliveriesScreenUiState.Content.Loading

                ListState.Failed -> FailedDeliveriesScreenUiState.Content.LoadFailed

                is ListState.Loaded -> if (listState.deliveries.isEmpty()) {
                    FailedDeliveriesScreenUiState.Content.Empty
                } else {
                    FailedDeliveriesScreenUiState.Content.Loaded(
                        deliveries = listState.deliveries.map { createDelivery(it) },
                        footer = createFooter(listState),
                    )
                }
            },
            listener = listener,
        )
    }

    private fun createFooter(loaded: ListState.Loaded): FailedDeliveriesScreenUiState.Footer {
        return when {
            loaded.loadMoreFailed -> FailedDeliveriesScreenUiState.Footer.LoadFailed
            loaded.nextCursor != null -> FailedDeliveriesScreenUiState.Footer.Loading
            else -> FailedDeliveriesScreenUiState.Footer.None
        }
    }

    private fun createDelivery(delivery: UserFailedDelivery): FailedDeliveriesScreenUiState.Delivery {
        val failedAt = delivery.failedAt
        return FailedDeliveriesScreenUiState.Delivery(
            listKey = delivery.id,
            kindText = createFailedDeliveryKindText(delivery.kind),
            inbox = delivery.inbox,
            failedAtText = if (failedAt == null) {
                "諦めた時刻は不明"
            } else {
                "${createFullPostedAtText(publishedAt = failedAt, timeZone = TimeZone.currentSystemDefault())} に諦めた"
            },
            errorText = delivery.lastError,
            listener = object : FailedDeliveriesScreenUiState.DeliveryListener {
                override fun onClick() {
                    viewModelScope.launch {
                        events.send { it.navigateToDelivery(delivery.id) }
                    }
                }
            },
        )
    }

    private fun UpdateOperationResponseResult<FailedDeliveriesQuery.Data>.toOutcome(): PagingOutcome {
        return when (this) {
            is UpdateOperationResponseResult.Success -> {
                val data = result.data ?: return PagingOutcome.Failed(NETWORK_ERROR_MESSAGE)
                PagingOutcome.Fetched(data.toUserFailedDeliveriesResult())
            }

            is UpdateOperationResponseResult.Error -> PagingOutcome.Failed(e?.message ?: NETWORK_ERROR_MESSAGE)

            is UpdateOperationResponseResult.NoHasMore -> PagingOutcome.NoHasMore
        }
    }

    internal interface Event {
        fun navigateBack()

        fun navigateToDelivery(id: String)

        fun showMessage(message: String)
    }

    private companion object {
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
        const val NETWORK_ERROR_MESSAGE = "ネットワークエラー"
    }

    private sealed interface PagingOutcome {
        data class Fetched(
            val result: UserFailedDeliveriesResult,
        ) : PagingOutcome

        data class Failed(
            val message: String,
        ) : PagingOutcome

        data object NoHasMore : PagingOutcome
    }

    private sealed interface ListState {
        data object Loading : ListState

        data object Failed : ListState

        /**
         * @param loadMoreFailed 続きを読めなかった。読み直すまで末尾まで送っても自動では読まない
         */
        data class Loaded(
            val deliveries: List<UserFailedDelivery>,
            val nextCursor: String?,
            val loadMoreFailed: Boolean,
        ) : ListState
    }
}
