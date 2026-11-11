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
import net.matsudamper.kmp.activitypub.frontend.logic.user.withBoosted
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLiked
import net.matsudamper.kmp.activitypub.frontend.screen.AuthorTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.Boost
import net.matsudamper.kmp.activitypub.frontend.screen.NoteDeletionState
import net.matsudamper.kmp.activitypub.frontend.screen.PostTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.confirm
import net.matsudamper.kmp.activitypub.frontend.screen.createNoteMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.screen.dismiss
import net.matsudamper.kmp.activitypub.frontend.screen.toDialogUiState
import net.matsudamper.kmp.activitypub.frontend.screen.withBlurToggled
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
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
                noteDeletion = NoteDeletionState.None,
            ),
        )
    private var loadJob: Job? = null

    /**
     * 表示には使わないので ViewModelState に入れない。入れると失敗時の削除で投稿の listener が作り直され、即座に取り直しを繰り返す
     */
    private var linkPreviewRequested = false

    /**
     * 返事を待たずに表示を切り替えるので、表示には使わない。続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var likeChanging = false
    private var boostChanging = false

    private val listener = object : PostDetailScreenUiState.Listener {
        override fun onClickRetry() {
            load()
        }

        override fun onClickBack() {
            viewModelScope.launch { events.send { it.navigateBack() } }
        }
    }

    private val deleteNoteDialogListener = object : DeleteNoteDialogUiState.Listener {
        override fun onClickDelete() {
            deleteNote()
        }

        override fun onDismiss() {
            viewModelStateFlow.update { it.copy(noteDeletion = it.noteDeletion.dismiss()) }
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

    private fun toggleLike() {
        val loaded = viewModelStateFlow.value.post as? PostState.Loaded ?: return
        val liked = loaded.post.body.likedByMe ?: return
        if (likeChanging) return
        likeChanging = true
        viewModelScope.launch {
            try {
                updateLiked(!liked)
                val error = api.setLiked(postId = loaded.post.reactionPostId, liked = !liked)
                if (error != null) {
                    updateLiked(liked)
                    events.send { it.showMessage(error) }
                }
            } finally {
                likeChanging = false
            }
        }
    }

    private fun toggleBoost() {
        val loaded = viewModelStateFlow.value.post as? PostState.Loaded ?: return
        val boosted = loaded.post.body.boostedByMe ?: return
        if (boostChanging) return
        boostChanging = true
        viewModelScope.launch {
            try {
                updateBoosted(!boosted)
                val error = api.setBoosted(postId = loaded.post.reactionPostId, boosted = !boosted)
                if (error != null) {
                    updateBoosted(boosted)
                    events.send { it.showMessage(error) }
                }
            } finally {
                boostChanging = false
            }
        }
    }

    private fun confirmNoteDeletion(noteId: String) {
        viewModelStateFlow.update { it.copy(noteDeletion = it.noteDeletion.confirm(noteId)) }
    }

    /**
     * 消せたら開いている投稿が無くなるので、前の画面に戻る
     */
    private fun deleteNote() {
        val confirming = viewModelStateFlow.value.noteDeletion as? NoteDeletionState.Confirming ?: return
        val noteId = confirming.noteId
        viewModelStateFlow.update { it.copy(noteDeletion = NoteDeletionState.Deleting(noteId)) }
        viewModelScope.launch {
            try {
                val error = api.deleteNote(noteId)
                if (error == null) {
                    events.send { it.navigateBack() }
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                viewModelStateFlow.update { it.copy(noteDeletion = NoteDeletionState.None) }
            }
        }
    }

    private fun updateLiked(liked: Boolean) {
        viewModelStateFlow.update { state ->
            val post = state.post as? PostState.Loaded ?: return@update state
            state.copy(post = PostState.Loaded(post.post.copy(body = post.post.body.withLiked(liked))))
        }
    }

    private fun updateBoosted(boosted: Boolean) {
        viewModelStateFlow.update { state ->
            val post = state.post as? PostState.Loaded ?: return@update state
            state.copy(post = PostState.Loaded(post.post.copy(body = post.post.body.withBoosted(boosted))))
        }
    }

    private fun toggleImageBlur(imageUrls: List<String>, toggled: Boolean) {
        viewModelStateFlow.update {
            it.copy(blurToggledImageUrls = it.blurToggledImageUrls.withBlurToggled(imageUrls, toggled))
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
            deleteNoteDialog = viewModelState.noteDeletion.toDialogUiState(deleteNoteDialogListener),
            listener = listener,
        )
    }

    private fun createPostItem(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostItemUiState {
        val host = post.host
        return createPostItemUiState(
            boostedBy = post.boostedBy?.let { booster ->
                Boost(booster = booster) { username ->
                    viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
                }
            },
            iconUrl = post.iconUrl,
            displayName = post.displayName,
            displayNameEmojis = post.displayNameEmojis,
            acct = if (host == null) "@${post.username}" else "@${post.username}@$host",
            body = post.body,
            linkPreviews = viewModelState.linkPreviews,
            sensitiveImagesBlurred = true,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorTapAction = AuthorTapAction.OpenProfile {
                val username = if (host == null) post.username else "${post.username}@$host"
                viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
            },
            menuItems = createNoteMenuItems(post.deletableNoteId, ::confirmNoteDeletion),
            postTapAction = PostTapAction.AlreadyOnPostDetail,
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
            onToggleLike = { toggleLike() },
            onToggleBoost = { toggleBoost() },
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

        fun shareUrl(url: String)

        fun navigateToImage(urls: List<String>, index: Int)
    }

    private data class ViewModelState(
        val post: PostState,
        val linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
        val blurToggledImageUrls: Set<String>,
        val noteDeletion: NoteDeletionState,
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
