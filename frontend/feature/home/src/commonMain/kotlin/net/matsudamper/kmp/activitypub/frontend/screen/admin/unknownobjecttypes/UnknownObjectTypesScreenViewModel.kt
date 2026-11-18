package net.matsudamper.kmp.activitypub.frontend.screen.admin.unknownobjecttypes

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
import net.matsudamper.kmp.activitypub.frontend.graphql.UnknownObjectTypesQuery
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUnknownObjectType
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUnknownObjectTypesResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserUnknownObjectTypesResult
import net.matsudamper.kmp.activitypub.frontend.screen.createFullPostedAtText
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class UnknownObjectTypesScreenViewModel(
    userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ListState> = MutableStateFlow(ListState.Loading)
    private var loadJob: Job? = null
    private val paging = userApi.unknownObjectTypesPaging()

    private val listener = object : UnknownObjectTypesScreenUiState.Listener {
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

    val uiStateFlow: StateFlow<UnknownObjectTypesScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { listState ->
                    uiStateFlow.value = createUiState(listState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            paging.watch().collect { data -> applyResult(data.toUserUnknownObjectTypesResult()) }
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
    private fun applyResult(result: UserUnknownObjectTypesResult) {
        when (result) {
            is UserUnknownObjectTypesResult.Success -> viewModelStateFlow.update { current ->
                ListState.Loaded(
                    types = result.types,
                    nextCursor = result.nextCursor,
                    loadMoreFailed = (current as? ListState.Loaded)?.loadMoreFailed ?: false,
                )
            }

            UserUnknownObjectTypesResult.NotLoggedIn -> showLoadFailure(NOT_LOGGED_IN_MESSAGE)
        }
    }

    private fun showLoadFailure(message: String) {
        viewModelStateFlow.value = ListState.Failed
        viewModelScope.launch {
            events.send { it.showMessage(message) }
        }
    }

    private fun createUiState(listState: ListState): UnknownObjectTypesScreenUiState {
        return UnknownObjectTypesScreenUiState(
            content = when (listState) {
                ListState.Loading -> UnknownObjectTypesScreenUiState.Content.Loading

                ListState.Failed -> UnknownObjectTypesScreenUiState.Content.LoadFailed

                is ListState.Loaded -> if (listState.types.isEmpty()) {
                    UnknownObjectTypesScreenUiState.Content.Empty
                } else {
                    UnknownObjectTypesScreenUiState.Content.Loaded(
                        types = listState.types.map { createType(it) },
                        footer = createFooter(listState),
                    )
                }
            },
            listener = listener,
        )
    }

    private fun createFooter(loaded: ListState.Loaded): UnknownObjectTypesScreenUiState.Footer {
        return when {
            loaded.loadMoreFailed -> UnknownObjectTypesScreenUiState.Footer.LoadFailed
            loaded.nextCursor != null -> UnknownObjectTypesScreenUiState.Footer.Loading
            else -> UnknownObjectTypesScreenUiState.Footer.None
        }
    }

    private fun createType(type: UserUnknownObjectType): UnknownObjectTypesScreenUiState.UnknownObjectType {
        val timeZone = TimeZone.currentSystemDefault()
        val lastSeenText = createFullPostedAtText(publishedAt = type.lastSeenAt, timeZone = timeZone)
        val firstSeenText = createFullPostedAtText(publishedAt = type.firstSeenAt, timeZone = timeZone)
        return UnknownObjectTypesScreenUiState.UnknownObjectType(
            listKey = "${type.typeName} ${type.sourceUrl}",
            typeName = type.typeName,
            sourceUrl = type.sourceUrl,
            seenText = if (type.seenCount <= 1) {
                "$firstSeenText に受け取った"
            } else {
                "${type.seenCount} 回受け取った。最後は $lastSeenText、最初は $firstSeenText"
            },
            listener = object : UnknownObjectTypesScreenUiState.UnknownObjectTypeListener {
                override fun onClickSourceUrl() {
                    viewModelScope.launch {
                        events.send { it.openUrl(type.sourceUrl) }
                    }
                }
            },
        )
    }

    private fun UpdateOperationResponseResult<UnknownObjectTypesQuery.Data>.toOutcome(): PagingOutcome {
        return when (this) {
            is UpdateOperationResponseResult.Success -> {
                val data = result.data ?: return PagingOutcome.Failed(NETWORK_ERROR_MESSAGE)
                PagingOutcome.Fetched(data.toUserUnknownObjectTypesResult())
            }

            is UpdateOperationResponseResult.Error -> PagingOutcome.Failed(e?.message ?: NETWORK_ERROR_MESSAGE)

            is UpdateOperationResponseResult.NoHasMore -> PagingOutcome.NoHasMore
        }
    }

    internal interface Event {
        fun navigateBack()

        fun openUrl(url: String)

        fun showMessage(message: String)
    }

    private companion object {
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
        const val NETWORK_ERROR_MESSAGE = "ネットワークエラー"
    }

    private sealed interface PagingOutcome {
        data class Fetched(
            val result: UserUnknownObjectTypesResult,
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
            val types: List<UserUnknownObjectType>,
            val nextCursor: String?,
            val loadMoreFailed: Boolean,
        ) : ListState
    }
}
