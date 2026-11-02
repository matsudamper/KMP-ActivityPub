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
    private var loadMoreJob: Job? = null
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

        override fun onScrolledToEnd() {
            // 失敗した後に自動で読み直すと、末尾にいる間は失敗を繰り返すので、ボタンを押したときだけ読み直す
            val loaded = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
            if (loaded.loadMoreErrorMessage != null) return
            loadMore()
        }

        override fun onClickRetryLoadMore() {
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
        viewModelScope.launch {
            userApi.followingChanges.collect { change ->
                viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) {
                        current.copy(
                            users = current.users.map {
                                if (it.actorUri == change.actorUri) it.copy(following = change.following) else it
                            },
                        )
                    } else {
                        current
                    }
                }
            }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Loading
        loadJob = viewModelScope.launch {
            val results = when (kind) {
                FollowListKind.Following -> userApi.watchFollowingUsers()
                FollowListKind.Followers -> userApi.watchFollowerUsers()
            }
            results.collect { result ->
                when (result) {
                    is UserFollowListResult.Success -> {
                        viewModelStateFlow.update { current ->
                            val loaded = current as? ViewModelState.Loaded
                            ViewModelState.Loaded(
                                users = result.users + (loaded?.users?.drop(loaded.firstPageSize) ?: listOf()),
                                firstPageSize = result.users.size,
                                nextCursor = loaded?.nextCursor ?: result.nextCursor,
                                loadMoreErrorMessage = loaded?.loadMoreErrorMessage,
                            )
                        }
                    }

                    UserFollowListResult.NotLoggedIn -> {
                        viewModelStateFlow.value = ViewModelState.Failed(NOT_LOGGED_IN_MESSAGE)
                    }

                    is UserFollowListResult.Failure -> {
                        viewModelStateFlow.value = ViewModelState.Failed(result.message)
                    }
                }
            }
        }
    }

    private fun loadMore() {
        if (loadMoreJob?.isActive == true) return
        val loaded = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        val nextCursor = loaded.nextCursor ?: return
        viewModelStateFlow.value = loaded.copy(loadMoreErrorMessage = null)
        loadMoreJob = viewModelScope.launch {
            val result = fetch(after = nextCursor)
            viewModelStateFlow.update { current ->
                if (current !is ViewModelState.Loaded) return@update current
                when (result) {
                    is UserFollowListResult.Success -> current.copy(
                        users = current.users + result.users,
                        nextCursor = result.nextCursor,
                    )
                    UserFollowListResult.NotLoggedIn -> current.copy(loadMoreErrorMessage = NOT_LOGGED_IN_MESSAGE)
                    is UserFollowListResult.Failure -> current.copy(loadMoreErrorMessage = result.message)
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

                is ViewModelState.Failed -> FollowListScreenUiState.Content.Error(viewModelState.message)

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
                    footer = createFooter(viewModelState),
                )
            },
            listener = listener,
        )
    }

    private fun createFooter(loaded: ViewModelState.Loaded): FollowListScreenUiState.Footer {
        val loadMoreErrorMessage = loaded.loadMoreErrorMessage
        return when {
            loadMoreErrorMessage != null -> FollowListScreenUiState.Footer.Error(loadMoreErrorMessage)
            loaded.nextCursor != null -> FollowListScreenUiState.Footer.Loading
            else -> FollowListScreenUiState.Footer.None
        }
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

    private companion object {
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
    }

    private sealed interface ViewModelState {
        data object Loading : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState

        /**
         * @param loadMoreErrorMessage 続きを読めなかったときの理由。読めていれば null
         */
        data class Loaded(
            val users: List<UserFollowListResult.User>,
            val firstPageSize: Int,
            val nextCursor: String?,
            val loadMoreErrorMessage: String?,
        ) : ViewModelState
    }
}
