package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserAdminAccessResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserRemoteProfileRefreshResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AdminScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                access = AccessState.Loading,
                refresh = RefreshState.Loading,
                starting = false,
            ),
        )

    private val listener =
        object : AdminScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }
        }

    private val remoteProfileRefreshListener =
        object : AdminScreenUiState.RemoteProfileRefreshListener {
            override fun onClickStart() {
                startRemoteProfileRefresh()
            }
        }

    val uiStateFlow: StateFlow<AdminScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            val access =
                when (val result = api.adminAccess()) {
                    UserAdminAccessResult.Granted -> AccessState.Granted
                    UserAdminAccessResult.Denied -> AccessState.Denied
                    UserAdminAccessResult.NotLoggedIn -> AccessState.Error("ログインしていない")
                    is UserAdminAccessResult.Failure -> AccessState.Error(result.message)
                }
            viewModelStateFlow.update { it.copy(access = access) }
            if (access == AccessState.Granted) collectRemoteProfileRefresh()
        }
    }

    private fun startRemoteProfileRefresh() {
        if (viewModelStateFlow.value.starting) return
        viewModelStateFlow.update { it.copy(starting = true) }
        viewModelScope.launch {
            try {
                val error = api.startRemoteProfileRefresh()
                events.send { it.showMessage(error ?: "取り直しを始めました") }
            } finally {
                viewModelStateFlow.update { it.copy(starting = false) }
            }
        }
    }

    /**
     * 画面を開いた時点で走っている取り直しがあれば、その進み具合から受け取る。
     * 切れても取り直しはサーバーで続いているので、直前の進み具合を出したまま張り直す
     */
    private suspend fun collectRemoteProfileRefresh() {
        var disconnectNotified = false
        while (true) {
            api.remoteProfileRefreshStream().collect { result ->
                when (result) {
                    is UserRemoteProfileRefreshResult.Success -> {
                        disconnectNotified = false
                        viewModelStateFlow.update { it.copy(refresh = RefreshState.Loaded(result.progress)) }
                    }

                    is UserRemoteProfileRefreshResult.Failure -> {
                        // 張り直しのたびに同じ知らせを重ねない
                        if (!disconnectNotified) {
                            disconnectNotified = true
                            events.send { it.showMessage("進み具合を受け取れませんでした。つなぎ直します: ${result.message}") }
                        }
                    }
                }
            }
            delay(RECONNECT_INTERVAL)
        }
    }

    private fun createUiState(viewModelState: ViewModelState): AdminScreenUiState {
        return AdminScreenUiState(
            content =
            when (val access = viewModelState.access) {
                AccessState.Loading -> AdminScreenUiState.Content.Loading
                AccessState.Granted -> AdminScreenUiState.Content.Loaded(remoteProfileRefresh = createRemoteProfileRefresh(viewModelState))
                AccessState.Denied -> AdminScreenUiState.Content.Message("サーバー管理画面に入る権限がない")
                is AccessState.Error -> AdminScreenUiState.Content.Message(access.message)
            },
            listener = listener,
        )
    }

    private fun createRemoteProfileRefresh(viewModelState: ViewModelState): AdminScreenUiState.RemoteProfileRefresh {
        val refresh = viewModelState.refresh
        val running = refresh is RefreshState.Loaded && refresh.progress?.running == true
        return AdminScreenUiState.RemoteProfileRefresh(
            statusText =
            when (refresh) {
                RefreshState.Loading -> "状態を読み込み中"
                is RefreshState.Loaded -> refresh.progress.toStatusText()
            },
            startButtonEnabled = refresh is RefreshState.Loaded && !running && !viewModelState.starting,
            listener = remoteProfileRefreshListener,
        )
    }

    private fun UserRemoteProfileRefreshResult.Progress?.toStatusText(): String {
        if (this == null) return "まだ取り直していません"
        val counts = "$total 人中 $refreshed 人を取り直し、失敗 $failed 人、スキップ $skipped 人"
        val finishedAt = finishedAt
        return if (finishedAt == null) {
            "取り直し中（$startedAt 開始）\n$counts"
        } else {
            "前回は $finishedAt に終了\n$counts"
        }
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val access: AccessState,
        val refresh: RefreshState,
        val starting: Boolean,
    )

    private sealed interface AccessState {
        data object Loading : AccessState

        data object Granted : AccessState

        data object Denied : AccessState

        data class Error(
            val message: String,
        ) : AccessState
    }

    private sealed interface RefreshState {
        data object Loading : RefreshState

        /**
         * @param progress 一度も始めていなければ null
         */
        data class Loaded(
            val progress: UserRemoteProfileRefreshResult.Progress?,
        ) : RefreshState
    }

    private companion object {
        val RECONNECT_INTERVAL = 5.seconds
    }
}
