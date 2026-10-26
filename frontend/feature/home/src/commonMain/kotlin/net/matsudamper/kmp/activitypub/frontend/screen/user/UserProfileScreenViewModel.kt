package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class UserProfileScreenViewModel(
    @InjectedParam private val username: String,
    private val api: UserApi,
) : ViewModel() {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState.Loading)
    private var loadJob: Job? = null

    val uiStateFlow: StateFlow<UserProfileScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener =
                object : UserProfileScreenUiState.Listener {
                    override fun onClickRetry() {
                        load()
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

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Loading
        loadJob = viewModelScope.launch {
            viewModelStateFlow.value =
                when (val result = api.userProfile(username = username)) {
                    is UserProfileResult.Found -> ViewModelState.Loaded(username = result.username, iconUrl = result.iconUrl)
                    UserProfileResult.NotFound -> ViewModelState.NotFound
                    is UserProfileResult.Failure -> ViewModelState.Failed(result.message)
                }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: UserProfileScreenUiState.Listener,
    ): UserProfileScreenUiState {
        return UserProfileScreenUiState(
            content = when (viewModelState) {
                ViewModelState.Loading -> UserProfileScreenUiState.Content.Loading

                is ViewModelState.Loaded -> UserProfileScreenUiState.Content.Loaded(
                    acct = "@${viewModelState.username}",
                    iconUrl = viewModelState.iconUrl,
                )

                ViewModelState.NotFound -> UserProfileScreenUiState.Content.NotFound

                is ViewModelState.Failed -> UserProfileScreenUiState.Content.Error(viewModelState.message)
            },
            listener = listener,
        )
    }

    sealed interface ViewModelState {
        data object Loading : ViewModelState

        data class Loaded(
            val username: String,
            val iconUrl: String?,
        ) : ViewModelState

        data object NotFound : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState
    }
}
