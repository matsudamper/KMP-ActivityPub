package net.matsudamper.kmp.activitypub.frontend.screen.admin.deletedremotenotes

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
import net.matsudamper.kmp.activitypub.frontend.graphql.DeletedRemoteNotesQuery
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserDeletedRemoteNote
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserDeletedRemoteNotesResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPurgeAllDeletedRemoteNotesResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserDeletedRemoteNotesResult
import net.matsudamper.kmp.activitypub.frontend.screen.createFullPostedAtText
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class DeletedRemoteNotesScreenViewModel(
    private val userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(
        ViewModelState(
            list = ListState.Loading,
            purgedIds = setOf(),
            purgingIds = setOf(),
            purgeAllDialogVisible = false,
            purgingAll = false,
        ),
    )
    private var loadJob: Job? = null
    private val paging = userApi.deletedRemoteNotesPaging()

    private val listener = object : DeletedRemoteNotesScreenUiState.Listener {
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
            if (loaded.loadMoreFailed) return
            loadMore()
        }

        override fun onClickRetryLoadMore() {
            loadMore()
        }

        override fun onClickPurgeAll() {
            viewModelStateFlow.update { it.copy(purgeAllDialogVisible = true) }
        }
    }

    private val purgeAllDialogListener = object : DeletedRemoteNotesScreenUiState.PurgeAllDialogListener {
        override fun onClickPurge() {
            purgeAll()
        }

        override fun onDismiss() {
            if (viewModelStateFlow.value.purgingAll) return
            viewModelStateFlow.update { it.copy(purgeAllDialogVisible = false) }
        }
    }

    val uiStateFlow: StateFlow<DeletedRemoteNotesScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            paging.watch().collect { data -> applyResult(data.toUserDeletedRemoteNotesResult()) }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.update { it.copy(list = ListState.Loading) }
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
        val loaded = viewModelStateFlow.value.list as? ListState.Loaded ?: return
        if (loaded.nextCursor == null) return
        viewModelStateFlow.update { it.copy(list = loaded.copy(loadMoreFailed = false)) }
        loadJob = viewModelScope.launch {
            when (val outcome = paging.fetchNext().toOutcome()) {
                is PagingOutcome.Fetched -> applyResult(outcome.result)

                is PagingOutcome.Failed -> {
                    viewModelStateFlow.update { current ->
                        val list = current.list
                        if (list is ListState.Loaded) current.copy(list = list.copy(loadMoreFailed = true)) else current
                    }
                    events.send { it.showMessage(outcome.message) }
                }

                PagingOutcome.NoHasMore -> Unit
            }
        }
    }

    private fun purge(id: String) {
        val state = viewModelStateFlow.value
        if (id in state.purgingIds || state.purgingAll) return
        viewModelStateFlow.update { it.copy(purgingIds = it.purgingIds + id) }
        viewModelScope.launch {
            val failure = userApi.purgeDeletedRemoteNote(id)
            viewModelStateFlow.update {
                it.copy(
                    purgingIds = it.purgingIds - id,
                    purgedIds = if (failure == null) it.purgedIds + id else it.purgedIds,
                )
            }
            if (failure != null) events.send { it.showMessage(failure) }
        }
    }

    private fun purgeAll() {
        if (viewModelStateFlow.value.purgingAll) return
        viewModelStateFlow.update { it.copy(purgingAll = true) }
        viewModelScope.launch {
            val result = userApi.purgeAllDeletedRemoteNotes()
            viewModelStateFlow.update { it.copy(purgingAll = false, purgeAllDialogVisible = false) }
            when (result) {
                is UserPurgeAllDeletedRemoteNotesResult.Success -> {
                    events.send { it.showMessage("${result.purgedCount} 件を削除しました") }
                    load()
                }

                is UserPurgeAllDeletedRemoteNotesResult.Failure -> events.send { it.showMessage(result.message) }
            }
        }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applyResult(result: UserDeletedRemoteNotesResult) {
        when (result) {
            is UserDeletedRemoteNotesResult.Success -> viewModelStateFlow.update { current ->
                current.copy(
                    list = ListState.Loaded(
                        notes = result.notes,
                        nextCursor = result.nextCursor,
                        loadMoreFailed = (current.list as? ListState.Loaded)?.loadMoreFailed ?: false,
                    ),
                )
            }

            UserDeletedRemoteNotesResult.NotLoggedIn -> showLoadFailure(NOT_LOGGED_IN_MESSAGE)
        }
    }

    private fun showLoadFailure(message: String) {
        viewModelStateFlow.update { it.copy(list = ListState.Failed) }
        viewModelScope.launch {
            events.send { it.showMessage(message) }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): DeletedRemoteNotesScreenUiState {
        val list = viewModelState.list
        val visibleNotes = if (list is ListState.Loaded) list.notes.filterNot { it.id in viewModelState.purgedIds } else listOf()
        return DeletedRemoteNotesScreenUiState(
            content = when (list) {
                ListState.Loading -> DeletedRemoteNotesScreenUiState.Content.Loading

                ListState.Failed -> DeletedRemoteNotesScreenUiState.Content.LoadFailed

                is ListState.Loaded -> if (visibleNotes.isEmpty() && list.nextCursor == null) {
                    DeletedRemoteNotesScreenUiState.Content.Empty
                } else {
                    DeletedRemoteNotesScreenUiState.Content.Loaded(
                        notes = visibleNotes.map { createNote(it, viewModelState) },
                        footer = createFooter(list),
                    )
                }
            },
            purgeAllButtonEnabled = visibleNotes.isNotEmpty() && !viewModelState.purgingAll,
            purgeAllDialog = if (viewModelState.purgeAllDialogVisible) {
                DeletedRemoteNotesScreenUiState.PurgeAllDialog(
                    message = "投稿者が消した他のサーバーの投稿を全て、付いていたいいね・ブースト・通知ごと削除します。" +
                        "タイムラインの削除済みの表示も消えます。元に戻せません。",
                    buttonsEnabled = !viewModelState.purgingAll,
                    listener = purgeAllDialogListener,
                )
            } else {
                null
            },
            listener = listener,
        )
    }

    private fun createFooter(loaded: ListState.Loaded): DeletedRemoteNotesScreenUiState.Footer {
        return when {
            loaded.loadMoreFailed -> DeletedRemoteNotesScreenUiState.Footer.LoadFailed
            loaded.nextCursor != null -> DeletedRemoteNotesScreenUiState.Footer.Loading
            else -> DeletedRemoteNotesScreenUiState.Footer.None
        }
    }

    private fun createNote(note: UserDeletedRemoteNote, viewModelState: ViewModelState): DeletedRemoteNotesScreenUiState.DeletedNote {
        val timeZone = TimeZone.currentSystemDefault()
        val publishedText = createFullPostedAtText(publishedAt = note.publishedAt, timeZone = timeZone)
        val deletedText = createFullPostedAtText(publishedAt = note.deletedAt, timeZone = timeZone)
        return DeletedRemoteNotesScreenUiState.DeletedNote(
            listKey = note.id,
            actorText = note.actorName?.let { "$it（${note.actorUri}）" } ?: note.actorUri,
            contentText = note.contentText.ifBlank { "（本文なし）" },
            objectUri = note.objectUri,
            dateText = "$publishedText に投稿、$deletedText に削除",
            purgeButtonEnabled = note.id !in viewModelState.purgingIds && !viewModelState.purgingAll,
            listener = object : DeletedRemoteNotesScreenUiState.DeletedNoteListener {
                override fun onClickObjectUri() {
                    viewModelScope.launch {
                        events.send { it.openUrl(note.objectUri) }
                    }
                }

                override fun onClickPurge() {
                    purge(note.id)
                }
            },
        )
    }

    private fun UpdateOperationResponseResult<DeletedRemoteNotesQuery.Data>.toOutcome(): PagingOutcome {
        return when (this) {
            is UpdateOperationResponseResult.Success -> {
                val data = result.data ?: return PagingOutcome.Failed(NETWORK_ERROR_MESSAGE)
                PagingOutcome.Fetched(data.toUserDeletedRemoteNotesResult())
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

    /**
     * @param purgedIds この画面で削除した投稿。読み直すまで一覧から外す
     * @param purgingIds 削除している途中の投稿
     */
    private data class ViewModelState(
        val list: ListState,
        val purgedIds: Set<String>,
        val purgingIds: Set<String>,
        val purgeAllDialogVisible: Boolean,
        val purgingAll: Boolean,
    )

    private sealed interface PagingOutcome {
        data class Fetched(
            val result: UserDeletedRemoteNotesResult,
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
            val notes: List<UserDeletedRemoteNote>,
            val nextCursor: String?,
            val loadMoreFailed: Boolean,
        ) : ListState
    }
}
