package net.matsudamper.kmp.activitypub.frontend.screen.followlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.apollographql.apollo.api.Query
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.following.FollowMenuController
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
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                list = ListState.Loading,
            ),
        )
    private var loadJob: Job? = null
    private val followMenuController = FollowMenuController(
        scope = viewModelScope,
        userApi = userApi,
        showMessage = { message -> events.send { it.showMessage(message) } },
    )
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
            val loaded = viewModelStateFlow.value.list as? ListState.Loaded ?: return
            if (loaded.loadMoreErrorMessage != null) return
            loadMore()
        }

        override fun onClickRetryLoadMore() {
            loadMore()
        }
    }

    val uiStateFlow: StateFlow<FollowListScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value, followMenuController.initialSnapshot)).also { uiStateFlow ->
            viewModelScope.launch {
                combine(viewModelStateFlow, followMenuController.snapshots) { viewModelState, followMenu ->
                    createUiState(viewModelState, followMenu)
                }.collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
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
        updateList { ListState.Loading }
        loadJob = viewModelScope.launch {
            when (val outcome = paging.refresh()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)
                is PagingOutcome.Failed -> updateList { ListState.Failed(outcome.message) }
                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    private fun loadMore() {
        if (loadJob?.isActive == true) return
        val loaded = viewModelStateFlow.value.list as? ListState.Loaded ?: return
        if (loaded.nextCursor == null) return
        updateList { loaded.copy(loadMoreErrorMessage = null) }
        loadJob = viewModelScope.launch {
            when (val outcome = paging.fetchNext()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)

                is PagingOutcome.Failed -> updateList { current ->
                    if (current is ListState.Loaded) current.copy(loadMoreErrorMessage = outcome.message) else current
                }

                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applyResult(result: UserFollowListResult) {
        updateList { current ->
            when (result) {
                is UserFollowListResult.Success -> ListState.Loaded(
                    users = result.users,
                    nextCursor = result.nextCursor,
                    loadMoreErrorMessage = (current as? ListState.Loaded)?.loadMoreErrorMessage,
                )

                UserFollowListResult.NotLoggedIn -> ListState.Failed(NOT_LOGGED_IN_MESSAGE)
            }
        }
    }

    private fun updateList(transform: (ListState) -> ListState) {
        viewModelStateFlow.update { it.copy(list = transform(it.list)) }
    }

    private fun createUiState(viewModelState: ViewModelState, followMenu: FollowMenuController.Snapshot): FollowListScreenUiState {
        return FollowListScreenUiState(
            title = when (kind) {
                FollowListKind.Following -> "購読"
                FollowListKind.Followers -> "フォロワー"
            },
            content = when (val list = viewModelState.list) {
                ListState.Loading -> FollowListScreenUiState.Content.Loading

                is ListState.Failed -> FollowListScreenUiState.Content.Error(list.message)

                is ListState.Loaded -> FollowListScreenUiState.Content.Loaded(
                    accounts = list.users.map { user ->
                        createAccount(user, followMenu)
                    },
                    emptyMessage = if (list.users.isEmpty()) {
                        when (kind) {
                            FollowListKind.Following -> "購読しているユーザーはいません"
                            FollowListKind.Followers -> "フォロワーはいません"
                        }
                    } else {
                        null
                    },
                    footer = createFooter(list),
                )
            },
            addFollowingGroupDialog = followMenuController.addGroupDialog(followMenu),
            listener = listener,
        )
    }

    private fun createFooter(loaded: ListState.Loaded): FollowListScreenUiState.Footer {
        val loadMoreErrorMessage = loaded.loadMoreErrorMessage
        return when {
            loadMoreErrorMessage != null -> FollowListScreenUiState.Footer.Error(loadMoreErrorMessage)
            loaded.nextCursor != null -> FollowListScreenUiState.Footer.Loading
            else -> FollowListScreenUiState.Footer.None
        }
    }

    private fun createAccount(
        user: UserFollowListResult.User,
        followMenu: FollowMenuController.Snapshot,
    ): FollowListScreenUiState.Account {
        val username = user.username
        return FollowListScreenUiState.Account(
            iconUrl = user.iconUrl,
            displayName = user.displayName ?: username ?: user.actorUri,
            acct = if (username == null) user.host else "@$username@${user.host}",
            // 解除しても行は残し、押し間違えたときにその場で購読し直せるようにする
            followMenu = followMenuController.menu(
                snapshot = followMenu,
                actorUri = user.actorUri,
                following = user.following,
                groupId = user.followingGroupId,
            ),
            // プロフィールは acct で引くので、ユーザー名を受け取っていない相手は開けない
            clickable = username != null,
            listener = object : FollowListScreenUiState.AccountListener {
                override fun onClick() {
                    if (username == null) return
                    viewModelScope.launch {
                        events.send { it.navigateToUserProfile("$username@${user.host}") }
                    }
                }
            },
        )
    }

    internal interface Event {
        fun navigateBack()

        fun navigateToUserProfile(username: String)

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

    private data class ViewModelState(
        val list: ListState,
    )

    private sealed interface ListState {
        data object Loading : ListState

        data class Failed(
            val message: String,
        ) : ListState

        /**
         * @param loadMoreErrorMessage 続きを読めなかったときの理由。読めていれば null
         */
        data class Loaded(
            val users: List<UserFollowListResult.User>,
            val nextCursor: String?,
            val loadMoreErrorMessage: String?,
        ) : ListState
    }
}
