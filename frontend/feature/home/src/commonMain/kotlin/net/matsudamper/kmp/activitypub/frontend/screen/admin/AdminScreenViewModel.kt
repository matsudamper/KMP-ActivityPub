package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserAdminAccessResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AdminScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(ViewModelState(access = AccessState.Loading))

    private val listener =
        object : AdminScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
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
        }
    }

    private fun createUiState(viewModelState: ViewModelState): AdminScreenUiState {
        return AdminScreenUiState(
            content =
            when (val access = viewModelState.access) {
                AccessState.Loading -> AdminScreenUiState.Content.Loading
                AccessState.Granted -> AdminScreenUiState.Content.Empty
                AccessState.Denied -> AdminScreenUiState.Content.Message("サーバー管理画面に入る権限がない")
                is AccessState.Error -> AdminScreenUiState.Content.Message(access.message)
            },
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()
    }

    private data class ViewModelState(
        val access: AccessState,
    )

    private sealed interface AccessState {
        data object Loading : AccessState

        data object Granted : AccessState

        data object Denied : AccessState

        data class Error(
            val message: String,
        ) : AccessState
    }
}
