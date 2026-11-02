package net.matsudamper.kmp.activitypub.frontend.screen.followlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.apollographql.apollo.api.Query
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.CursorPagingModel
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowListResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserFollowListResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.withFollowing
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
    private val paging: FollowListPaging<*> =
        when (kind) {
            FollowListKind.Following -> FollowListPaging(
                model = userApi.followingUsersPaging(),
                toResult = { it.toUserFollowListResult() },
                withFollowing = { data, change -> data.withFollowing(change) },
            )

            FollowListKind.Followers -> FollowListPaging(
                model = userApi.followerUsersPaging(),
                toResult = { it.toUserFollowListResult() },
                withFollowing = { data, change -> data.withFollowing(change) },
            )
        }

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
            paging.watch().collect { result -> applyResult(result) }
        }
        viewModelScope.launch {
            userApi.followingChanges.collect { change -> paging.applyFollowing(change) }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Loading
        loadJob = viewModelScope.launch {
            when (val outcome = paging.refresh()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)
                is PagingOutcome.Failed -> viewModelStateFlow.value = ViewModelState.Failed(outcome.message)
                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    private fun loadMore() {
        if (loadJob?.isActive == true) return
        val loaded = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        if (loaded.nextCursor == null) return
        viewModelStateFlow.value = loaded.copy(loadMoreErrorMessage = null)
        loadJob = viewModelScope.launch {
            when (val outcome = paging.fetchNext()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)

                is PagingOutcome.Failed -> viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) current.copy(loadMoreErrorMessage = outcome.message) else current
                }

                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applyResult(result: UserFollowListResult) {
        viewModelStateFlow.update { current ->
            when (result) {
                is UserFollowListResult.Success -> ViewModelState.Loaded(
                    users = result.users,
                    nextCursor = result.nextCursor,
                    loadMoreErrorMessage = (current as? ViewModelState.Loaded)?.loadMoreErrorMessage,
                )

                UserFollowListResult.NotLoggedIn -> ViewModelState.Failed(NOT_LOGGED_IN_MESSAGE)
            }
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
                val error = userApi.setFollowing(user.actorUri, followed = !user.following)
                if (error != null) {
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
        const val NETWORK_ERROR_MESSAGE = "ネットワークエラー"
    }

    /**
     * フォローとフォロワーでクエリの型が違うので、画面が使う形にそろえる
     */
    private class FollowListPaging<D : Query.Data>(
        private val model: CursorPagingModel<D>,
        private val toResult: (D) -> UserFollowListResult,
        private val withFollowing: (D, UserApi.FollowingChange) -> D,
    ) {
        fun watch(): Flow<UserFollowListResult> = model.watch().map(toResult)

        suspend fun refresh(): PagingOutcome = model.refresh().toOutcome()

        suspend fun fetchNext(): PagingOutcome = model.fetchNext().toOutcome()

        suspend fun applyFollowing(change: UserApi.FollowingChange) {
            model.updateCache { withFollowing(it, change) }
        }

        private fun UpdateOperationResponseResult<D>.toOutcome(): PagingOutcome {
            return when (this) {
                is UpdateOperationResponseResult.Success -> {
                    val data = result.data ?: return PagingOutcome.Failed(NETWORK_ERROR_MESSAGE)
                    PagingOutcome.Fetched(toResult(data))
                }

                is UpdateOperationResponseResult.Error -> PagingOutcome.Failed(e?.message ?: NETWORK_ERROR_MESSAGE)

                is UpdateOperationResponseResult.NoHasMore -> PagingOutcome.NoHasMore
            }
        }
    }

    private sealed interface PagingOutcome {
        data class Fetched(
            val result: UserFollowListResult,
        ) : PagingOutcome

        data class Failed(
            val message: String,
        ) : PagingOutcome

        data object NoHasMore : PagingOutcome
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
            val nextCursor: String?,
            val loadMoreErrorMessage: String?,
        ) : ViewModelState
    }
}
