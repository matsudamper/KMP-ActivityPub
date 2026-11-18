package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddelivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFailedDelivery
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFailedDeliveryResult
import net.matsudamper.kmp.activitypub.frontend.screen.admin.createFailedDeliveryKindText
import net.matsudamper.kmp.activitypub.frontend.screen.createFullPostedAtText
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class FailedDeliveryScreenViewModel(
    @InjectedParam private val id: String,
    private val userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<LoadState> = MutableStateFlow(LoadState.Loading)
    private var loadJob: Job? = null

    private val listener = object : FailedDeliveryScreenUiState.Listener {
        override fun onClickBack() {
            viewModelScope.launch {
                events.send { it.navigateBack() }
            }
        }

        override fun onClickRetry() {
            load()
        }
    }

    val uiStateFlow: StateFlow<FailedDeliveryScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { loadState ->
                    uiStateFlow.value = createUiState(loadState)
                }
            }
        }.asStateFlow()

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = LoadState.Loading
        loadJob = viewModelScope.launch {
            val failureMessage = when (val result = userApi.failedDelivery(id)) {
                is UserFailedDeliveryResult.Found -> {
                    viewModelStateFlow.value = LoadState.Loaded(result.delivery)
                    return@launch
                }

                UserFailedDeliveryResult.NotFound -> "配信が見つかりません"

                UserFailedDeliveryResult.NotLoggedIn -> "ログインしていません"

                is UserFailedDeliveryResult.Failure -> result.message
            }
            viewModelStateFlow.value = LoadState.Failed
            events.send { it.showMessage(failureMessage) }
        }
    }

    private fun createUiState(loadState: LoadState): FailedDeliveryScreenUiState {
        return FailedDeliveryScreenUiState(
            content = when (loadState) {
                LoadState.Loading -> FailedDeliveryScreenUiState.Content.Loading
                LoadState.Failed -> FailedDeliveryScreenUiState.Content.LoadFailed
                is LoadState.Loaded -> FailedDeliveryScreenUiState.Content.Loaded(fields = createFields(loadState.delivery))
            },
            listener = listener,
        )
    }

    private fun createFields(delivery: UserFailedDelivery): List<FailedDeliveryScreenUiState.Field> {
        val timeZone = TimeZone.currentSystemDefault()
        val failedAt = delivery.failedAt
        return listOf(
            FailedDeliveryScreenUiState.Field(label = "種別", value = createFailedDeliveryKindText(delivery.kind)),
            FailedDeliveryScreenUiState.Field(label = "送り主", value = "@${delivery.username}"),
            FailedDeliveryScreenUiState.Field(label = "宛先の inbox", value = delivery.inbox),
            FailedDeliveryScreenUiState.Field(label = "送ろうとした回数", value = "${delivery.attempts} 回"),
            FailedDeliveryScreenUiState.Field(
                label = "積んだ時刻",
                value = createFullPostedAtText(publishedAt = delivery.enqueuedAt, timeZone = timeZone),
            ),
            FailedDeliveryScreenUiState.Field(
                label = "諦めた時刻",
                value = if (failedAt == null) "不明" else createFullPostedAtText(publishedAt = failedAt, timeZone = timeZone),
            ),
            FailedDeliveryScreenUiState.Field(label = "最後に送れなかった理由", value = delivery.lastError ?: "残っていません"),
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private sealed interface LoadState {
        data object Loading : LoadState

        data object Failed : LoadState

        data class Loaded(
            val delivery: UserFailedDelivery,
        ) : LoadState
    }
}
