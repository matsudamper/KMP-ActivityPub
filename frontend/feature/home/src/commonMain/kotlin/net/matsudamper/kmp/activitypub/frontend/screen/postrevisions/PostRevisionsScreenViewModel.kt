package net.matsudamper.kmp.activitypub.frontend.screen.postrevisions

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
import net.matsudamper.kmp.activitypub.frontend.graphql.PostRevisionsQuery
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostRevisionsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserPostRevisionsResult
import net.matsudamper.kmp.activitypub.frontend.screen.createFullPostedAtText
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.screen.createPostImage
import net.matsudamper.kmp.activitypub.frontend.screen.withBlurToggled
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PostRevisionsScreenViewModel(
    @InjectedParam private val postId: String,
    userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                list = ListState.Loading,
                blurToggledImageUrls = setOf(),
            ),
        )
    private var loadJob: Job? = null
    private val paging = userApi.postRevisionsPaging(postId)

    private val listener = object : PostRevisionsScreenUiState.Listener {
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

    val uiStateFlow: StateFlow<PostRevisionsScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.value = createUiState(viewModelState)
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            paging.watch().collect { data -> applyResult(data.toUserPostRevisionsResult()) }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        updateList { ListState.Loading }
        loadJob = viewModelScope.launch {
            when (val outcome = paging.refresh().toOutcome()) {
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
            when (val outcome = paging.fetchNext().toOutcome()) {
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
    private fun applyResult(result: UserPostRevisionsResult) {
        updateList { current ->
            when (result) {
                is UserPostRevisionsResult.Success -> ListState.Loaded(
                    revisions = result.revisions,
                    nextCursor = result.nextCursor,
                    loadMoreErrorMessage = (current as? ListState.Loaded)?.loadMoreErrorMessage,
                )

                UserPostRevisionsResult.NotLoggedIn -> ListState.Failed(NOT_LOGGED_IN_MESSAGE)

                UserPostRevisionsResult.NotFound -> ListState.Failed(NOT_FOUND_MESSAGE)
            }
        }
    }

    private fun updateList(transform: (ListState) -> ListState) {
        viewModelStateFlow.update { it.copy(list = transform(it.list)) }
    }

    private fun toggleImageBlur(imageUrls: List<String>, toggled: Boolean) {
        viewModelStateFlow.update {
            it.copy(blurToggledImageUrls = it.blurToggledImageUrls.withBlurToggled(imageUrls, toggled))
        }
    }

    private fun createUiState(viewModelState: ViewModelState): PostRevisionsScreenUiState {
        return PostRevisionsScreenUiState(
            content = when (val list = viewModelState.list) {
                ListState.Loading -> PostRevisionsScreenUiState.Content.Loading

                is ListState.Failed -> PostRevisionsScreenUiState.Content.Error(list.message)

                is ListState.Loaded -> PostRevisionsScreenUiState.Content.Loaded(
                    revisions = list.revisions.mapIndexed { index, revision ->
                        createRevision(revision = revision, current = index == 0, blurToggledImageUrls = viewModelState.blurToggledImageUrls)
                    },
                    footer = createFooter(list),
                )
            },
            listener = listener,
        )
    }

    private fun createFooter(loaded: ListState.Loaded): PostRevisionsScreenUiState.Footer {
        val loadMoreErrorMessage = loaded.loadMoreErrorMessage
        return when {
            loadMoreErrorMessage != null -> PostRevisionsScreenUiState.Footer.Error(loadMoreErrorMessage)
            loaded.nextCursor != null -> PostRevisionsScreenUiState.Footer.Loading
            else -> PostRevisionsScreenUiState.Footer.None
        }
    }

    /**
     * @param current 今の版か
     */
    private fun createRevision(
        revision: UserPostRevisionsResult.Revision,
        current: Boolean,
        blurToggledImageUrls: Set<String>,
    ): PostRevisionsScreenUiState.Revision {
        val imageUrls = revision.images.map { it.url }
        return PostRevisionsScreenUiState.Revision(
            listKey = revision.id,
            revisedAt = createFullPostedAtText(publishedAt = revision.revisedAt, timeZone = TimeZone.currentSystemDefault()),
            currentLabelVisible = current,
            body = createPostBodyText(
                content = revision.content,
                mentions = revision.mentions,
                emojis = revision.emojis,
                onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            ),
            images = revision.images.mapIndexed { index, image ->
                createPostImage(
                    image = image,
                    sensitive = revision.sensitive,
                    sensitiveImagesBlurred = true,
                    blurToggled = image.url in blurToggledImageUrls,
                    onOpen = { viewModelScope.launch { events.send { it.navigateToImage(imageUrls, index) } } },
                    onToggleBlur = { toggleImageBlur(imageUrls, image.url !in blurToggledImageUrls) },
                )
            },
        )
    }

    private fun UpdateOperationResponseResult<PostRevisionsQuery.Data>.toOutcome(): PagingOutcome {
        return when (this) {
            is UpdateOperationResponseResult.Success -> {
                val data = result.data ?: return PagingOutcome.Failed(NETWORK_ERROR_MESSAGE)
                PagingOutcome.Fetched(data.toUserPostRevisionsResult())
            }

            is UpdateOperationResponseResult.Error -> PagingOutcome.Failed(e?.message ?: NETWORK_ERROR_MESSAGE)

            is UpdateOperationResponseResult.NoHasMore -> PagingOutcome.NoHasMore
        }
    }

    internal interface Event {
        fun navigateBack()

        fun navigateToUserProfile(username: String)

        fun navigateToImage(urls: List<String>, index: Int)

        fun openUrl(url: String)
    }

    private companion object {
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
        const val NOT_FOUND_MESSAGE = "投稿が見つかりません"
        const val NETWORK_ERROR_MESSAGE = "ネットワークエラー"
    }

    private sealed interface PagingOutcome {
        data class Fetched(
            val result: UserPostRevisionsResult,
        ) : PagingOutcome

        data class Failed(
            val message: String,
        ) : PagingOutcome

        data object NoHasMore : PagingOutcome
    }

    private data class ViewModelState(
        val list: ListState,
        val blurToggledImageUrls: Set<String>,
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
            val revisions: List<UserPostRevisionsResult.Revision>,
            val nextCursor: String?,
            val loadMoreErrorMessage: String?,
        ) : ListState
    }
}
