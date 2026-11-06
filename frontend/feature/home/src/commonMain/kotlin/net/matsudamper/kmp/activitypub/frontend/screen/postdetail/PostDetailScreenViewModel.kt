package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostDetailResult
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PostDetailScreenViewModel(
    @InjectedParam private val postId: String,
    private val api: UserApi,
    private val postedAtRefreshTicker: PostedAtRefreshTicker,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                post = PostState.Loading,
                linkPreviews = listOf(),
                blurToggledImageUrls = setOf(),
            ),
        )
    private var loadJob: Job? = null

    /**
     * 表示には使わないので ViewModelState に入れない。入れると失敗時の削除で投稿の listener が作り直され、即座に取り直しを繰り返す
     */
    private var linkPreviewRequested = false

    private val listener = object : PostDetailScreenUiState.Listener {
        override fun onClickRetry() {
            load()
        }

        override fun onClickBack() {
            viewModelScope.launch { events.send { it.navigateBack() } }
        }
    }

    val uiStateFlow: StateFlow<PostDetailScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                combine(
                    viewModelStateFlow,
                    postedAtRefreshTicker.ticks.onStart { emit(Unit) },
                ) { viewModelState, _ ->
                    createUiState(viewModelState)
                }.collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.update { it.copy(post = PostState.Loading) }
        loadJob = viewModelScope.launch {
            val post =
                when (val result = api.postDetail(postId = postId)) {
                    is UserPostDetailResult.Found -> PostState.Loaded(result.post)

                    UserPostDetailResult.NotFound,
                    UserPostDetailResult.NotLoggedIn,
                    -> PostState.NotFound

                    is UserPostDetailResult.Failure -> {
                        events.send { it.showMessage(result.message) }
                        PostState.Failed
                    }
                }
            viewModelStateFlow.update { it.copy(post = post) }
        }
    }

    private fun toggleImageBlur(url: String) {
        viewModelStateFlow.update {
            val urls = it.blurToggledImageUrls
            it.copy(blurToggledImageUrls = if (url in urls) urls - url else urls + url)
        }
    }

    private fun loadLinkPreviews() {
        if (linkPreviewRequested) return
        linkPreviewRequested = true
        viewModelScope.launch {
            when (val result = api.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update { it.copy(linkPreviews = result.previews) }
                }

                // カードは URL だけでも出ているので、取れなくても知らせない。次に画面に入ったときに取り直す
                is UserLinkPreviewsResult.Failure,
                UserLinkPreviewsResult.NotLoggedIn,
                -> linkPreviewRequested = false
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): PostDetailScreenUiState {
        return PostDetailScreenUiState(
            content = when (val post = viewModelState.post) {
                PostState.Loading -> PostDetailScreenUiState.Content.Loading
                is PostState.Loaded -> PostDetailScreenUiState.Content.Loaded(post = createPostItem(post.post, viewModelState))
                PostState.NotFound -> PostDetailScreenUiState.Content.NotFound
                PostState.Failed -> PostDetailScreenUiState.Content.Error
            },
            listener = listener,
        )
    }

    private fun createPostItem(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostItemUiState {
        val host = post.host
        return createPostItemUiState(
            iconUrl = post.iconUrl,
            displayName = post.displayName,
            acct = if (host == null) "@${post.username}" else "@${post.username}@$host",
            body = post.body,
            linkPreviews = viewModelState.linkPreviews,
            sensitiveImagesBlurred = true,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorListener = object : PostItemUiState.AuthorListener {
                override fun onClick() {
                    val username = if (host == null) post.username else "${post.username}@$host"
                    viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
                }
            },
            // 開いているのがこの投稿の画面なので、押しても移る先が無い
            postListener = null,
            menuItems = listOf(),
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenImage = { url -> viewModelScope.launch { events.send { it.navigateToImage(url) } } },
            onToggleImageBlur = { url -> toggleImageBlur(url) },
            onVisible = {
                if (post.body.linkUrls.isNotEmpty()) {
                    loadLinkPreviews()
                }
            },
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)

        fun navigateToUserProfile(username: String)

        fun openUrl(url: String)

        fun navigateToImage(url: String)
    }

    private data class ViewModelState(
        val post: PostState,
        val linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
        val blurToggledImageUrls: Set<String>,
    )

    private sealed interface PostState {
        data object Loading : PostState

        data class Loaded(
            val post: UserHomeTimelineResult.Post,
        ) : PostState

        data object NotFound : PostState

        data object Failed : PostState
    }
}
