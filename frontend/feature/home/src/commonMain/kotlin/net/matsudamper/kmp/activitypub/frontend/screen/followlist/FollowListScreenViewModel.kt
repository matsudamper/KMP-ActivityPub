package net.matsudamper.kmp.activitypub.frontend.screen.followlist

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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowListResult
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class FollowListScreenViewModel(
    @InjectedParam private val kind: FollowListKind,
    private val userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState.Loading)
    private var loadJob: Job? = null
    private var followingChangeInProgress = false

    private val listener = object : FollowListScreenUiState.Listener {
        override fun onClickBack() {
            viewModelScope.launch {
                events.send { it.navigateBack() }
            }
        }

        override fun onClickRetry() {
            load()
        }

        override fun onClickLoadMore() {
            loadMore()
        }
    }

    private val mutableUiStateFlow: MutableStateFlow<FollowListScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value))
    val uiStateFlow: StateFlow<FollowListScreenUiState> = mutableUiStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            viewModelStateFlow.collect { viewModelState ->
                mutableUiStateFlow.value = createUiState(viewModelState)
            }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Loading
        loadJob = viewModelScope.launch {
            when (val result = fetch(after = null)) {
                is UserFollowListResult.Success -> {
                    viewModelStateFlow.value = ViewModelState.Loaded(users = result.users, nextCursor = result.nextCursor)
                }

                UserFollowListResult.NotLoggedIn -> {
                    viewModelStateFlow.value = ViewModelState.NotLoaded
                    events.send { it.showMessage("ログインしていません") }
                }

                is UserFollowListResult.Failure -> {
                    viewModelStateFlow.value = ViewModelState.NotLoaded
                    events.send { it.showMessage(result.message) }
                }
            }
        }
    }

    private fun loadMore() {
        if (loadJob?.isActive == true) return
        val loaded = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        val nextCursor = loaded.nextCursor ?: return
        loadJob = viewModelScope.launch {
            when (val result = fetch(after = nextCursor)) {
                is UserFollowListResult.Success -> {
                    viewModelStateFlow.update { current ->
                        if (current is ViewModelState.Loaded) {
                            current.copy(users = current.users + result.users, nextCursor = result.nextCursor)
                        } else {
                            current
                        }
                    }
                }

                UserFollowListResult.NotLoggedIn -> {
                    events.send { it.showMessage("ログインしていません") }
                }

                is UserFollowListResult.Failure -> {
                    events.send { it.showMessage(result.message) }
                }
            }
        }
    }

    private suspend fun fetch(after: String?): UserFollowListResult {
        return when (kind) {
            FollowListKind.Following -> userApi.followingUsers(after = after)
            FollowListKind.Followers -> userApi.followerUsers(after = after)
        }
    }

    /**
     * 解除しても行は残し、押し間違えたときにその場でフォローし直せるようにする
     */
    private fun changeFollowing(user: UserFollowListResult.User) {
        if (followingChangeInProgress) return
        followingChangeInProgress = true
        refreshUiState()
        viewModelScope.launch {
            try {
                val followed = !user.following
                val error = userApi.setFollowing(user.actorUri, followed = followed)
                if (error == null) {
                    viewModelStateFlow.update { state ->
                        if (state is ViewModelState.Loaded) {
                            state.copy(users = state.users.map { if (it.actorUri == user.actorUri) it.copy(following = followed) else it })
                        } else {
                            state
                        }
                    }
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                followingChangeInProgress = false
                refreshUiState()
            }
        }
    }

    private fun refreshUiState() {
        mutableUiStateFlow.value = createUiState(viewModelStateFlow.value)
    }

    private fun createUiState(viewModelState: ViewModelState): FollowListScreenUiState {
        return FollowListScreenUiState(
            title = when (kind) {
                FollowListKind.Following -> "フォロー"
                FollowListKind.Followers -> "フォロワー"
            },
            content = when (viewModelState) {
                ViewModelState.Loading -> FollowListScreenUiState.Content.Loading

                ViewModelState.NotLoaded -> FollowListScreenUiState.Content.NotLoaded

                is ViewModelState.Loaded -> FollowListScreenUiState.Content.Loaded(
                    accounts = viewModelState.users.map { createAccount(it) },
                    emptyMessage = if (viewModelState.users.isEmpty()) {
                        when (kind) {
                            FollowListKind.Following -> "フォローしているユーザーはいません"
                            FollowListKind.Followers -> "フォロワーはいません"
                        }
                    } else {
                        null
                    },
                    loadMoreButtonVisible = viewModelState.nextCursor != null,
                )
            },
            listener = listener,
        )
    }

    private fun createAccount(user: UserFollowListResult.User): FollowListScreenUiState.Account {
        val username = user.username
        return FollowListScreenUiState.Account(
            iconUrl = user.iconUrl,
            displayName = user.displayName ?: username ?: user.actorUri,
            acct = if (username == null) user.host else "@$username@${user.host}",
            followButtonText = if (user.following) "フォロー中・解除" else "フォロー",
            followButtonEnabled = !followingChangeInProgress,
            listener = object : FollowListScreenUiState.AccountListener {
                override fun onClickFollow() {
                    changeFollowing(user)
                }
            },
        )
    }

    interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    private sealed interface ViewModelState {
        data object Loading : ViewModelState

        data object NotLoaded : ViewModelState

        data class Loaded(
            val users: List<UserFollowListResult.User>,
            val nextCursor: String?,
        ) : ViewModelState
    }
}
