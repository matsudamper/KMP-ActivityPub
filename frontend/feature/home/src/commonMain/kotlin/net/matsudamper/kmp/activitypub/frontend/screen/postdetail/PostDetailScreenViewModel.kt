package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.uuid.Uuid
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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.withBoosted
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLiked
import net.matsudamper.kmp.activitypub.frontend.screen.AuthorTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.CreateOpenOriginalMenuItemsUseCase
import net.matsudamper.kmp.activitypub.frontend.screen.NoteDeletionState
import net.matsudamper.kmp.activitypub.frontend.screen.PostActorLabel
import net.matsudamper.kmp.activitypub.frontend.screen.PostTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.confirm
import net.matsudamper.kmp.activitypub.frontend.screen.createNoteMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPinMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.screen.dismiss
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNoteSubmitResult
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNoteSubmitter
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
    private val createOpenOriginalMenuItems: CreateOpenOriginalMenuItemsUseCase,
    private val postNoteSubmitter: PostNoteSubmitter,
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
                mentionText = "",
                mentionSending = false,
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

    /**
     * 続けて押されたときに送り違えないよう、返事までは受け付けない
     */
    private var pinChanging = false

    /**
     * 応答が失われて送り直すときだけ同じ ID にし、書き直したメンションは別の投稿として扱う
     */
    private var mentionRequestId = newRequestId()

    private val listener = object : PostDetailScreenUiState.Listener {
        override fun onClickRetry() {
            load()
        }

        override fun onClickBack() {
            viewModelScope.launch { events.send { it.navigateBack() } }
        }
    }

    private val mentionBoxListener = object : PostDetailScreenUiState.MentionBox.Listener {
        override fun onTextChange(text: String) {
            if (viewModelStateFlow.value.mentionSending) return
            if (text == viewModelStateFlow.value.mentionText) return
            mentionRequestId = newRequestId()
            viewModelStateFlow.update { it.copy(mentionText = text) }
        }

        override fun onClickSend() {
            sendMention()
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
        viewModelScope.launch {
            // ほかの画面で変えた固定もここで反映する
            api.pinChanges.collect { change ->
                viewModelStateFlow.update { state ->
                    val loaded = state.post as? PostState.Loaded ?: return@update state
                    if (loaded.post.pinnableNoteId != change.noteId) return@update state
                    state.copy(post = PostState.Loaded(loaded.post.copy(pinned = change.pinned)))
                }
            }
        }
        viewModelScope.launch {
            // ほかの画面で変えたブーストもここで反映する
            api.boostChanges.collect { change ->
                val loaded = viewModelStateFlow.value.post as? PostState.Loaded ?: return@collect
                if (loaded.post.reactionPostId == change.reactionPostId) updateBoosted(change.boosted)
            }
        }
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
            viewModelStateFlow.update { state ->
                // 読み直しで書きかけの本文を消さない
                val mentionText = if (post is PostState.Loaded && state.mentionText.isEmpty()) initialMentionText(post.post) else state.mentionText
                state.copy(post = post, mentionText = mentionText)
            }
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

    private fun sendMention() {
        val state = viewModelStateFlow.value
        if (!state.mentionSendable()) return
        val body = state.mentionText
        viewModelStateFlow.update { it.copy(mentionSending = true) }
        viewModelScope.launch {
            val error =
                try {
                    val result =
                        postNoteSubmitter.submit(body = body, requestId = mentionRequestId, images = listOf(), replyToPostId = postId)
                    when (result) {
                        // 画像を付けないので置けないことは無い
                        is PostNoteSubmitResult.UploadFailed -> "メンションを送れませんでした"

                        is PostNoteSubmitResult.Submitted -> mentionErrorMessage(result.result)
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(mentionSending = false) }
                }
            if (error != null) {
                events.send { it.showMessage(error) }
                return@launch
            }
            mentionRequestId = newRequestId()
            viewModelStateFlow.update { state ->
                val loaded = state.post as? PostState.Loaded
                state.copy(mentionText = if (loaded == null) "" else initialMentionText(loaded.post))
            }
            events.send { it.showMessage("メンションを送りました") }
        }
    }

    /**
     * 送れたら null
     */
    private fun mentionErrorMessage(result: UserPostNoteResult): String? =
        when (result) {
            UserPostNoteResult.Success -> null

            UserPostNoteResult.NotLoggedIn -> "ログインしていません"

            UserPostNoteResult.EmptyBody -> "本文が空です"

            UserPostNoteResult.TooLong -> "本文が長すぎます"

            UserPostNoteResult.FederationDisabled -> "サーバーが投稿を配れる設定になっていません"

            UserPostNoteResult.ReplyTargetNotFound -> "この投稿にはメンションできません"

            UserPostNoteResult.TooManyImages,
            UserPostNoteResult.ImageNotFound,
            -> "メンションを送れませんでした"

            is UserPostNoteResult.Failure -> result.message
        }

    /**
     * 宛てる相手を本文に書いておく。消したり後ろに書き足したりできるよう、ただの文字として入れる。
     * 自分の投稿への返信はサーバーが自分に宛てないので、書かない
     */
    private fun initialMentionText(post: UserHomeTimelineResult.Post): String =
        if (post.deletableNoteId != null) "" else "${createAcct(post)} "

    private fun newRequestId(): String = Uuid.random().toString()

    private fun setPinned(pinnableNoteId: String, pinned: Boolean) {
        if (pinChanging) return
        pinChanging = true
        viewModelScope.launch {
            try {
                val error = api.setPinned(noteId = pinnableNoteId, pinned = pinned)
                if (error != null) events.send { it.showMessage(error) }
            } finally {
                pinChanging = false
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

                is PostState.Loaded -> PostDetailScreenUiState.Content.Loaded(
                    post = createPostItem(post.post, viewModelState),
                    mentionBox = PostDetailScreenUiState.MentionBox(
                        text = viewModelState.mentionText,
                        placeholder = "${createAcct(post.post)} へメンション",
                        textFieldEnabled = !viewModelState.mentionSending,
                        sendButtonEnabled = viewModelState.mentionSendable(),
                        listener = mentionBoxListener,
                    ),
                )

                PostState.NotFound -> PostDetailScreenUiState.Content.NotFound

                PostState.Failed -> PostDetailScreenUiState.Content.Error
            },
            deleteNoteDialog = viewModelState.noteDeletion.toDialogUiState(deleteNoteDialogListener),
            listener = listener,
        )
    }

    private fun createAcct(post: UserHomeTimelineResult.Post): String {
        val host = post.host
        return if (host == null) "@${post.username}" else "@${post.username}@$host"
    }

    private fun createPostItem(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostItemUiState {
        val host = post.host
        return createPostItemUiState(
            pinned = false,
            deleted = post.deleted,
            boostUndone = post.boostUndone,
            actorLabel = post.boostedBy?.let { booster ->
                // このサーバーのアカウントのブーストでタイムラインに載るのは、自分のブーストだけ
                PostActorLabel.Boost(actor = booster, byViewer = booster.host == null) { username ->
                    viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
                }
            },
            iconUrl = post.iconUrl,
            displayName = post.displayName,
            displayNameEmojis = post.displayNameEmojis,
            acct = createAcct(post),
            body = post.body,
            linkPreviews = viewModelState.linkPreviews,
            sensitiveImagesBlurred = true,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorTapAction = AuthorTapAction.OpenProfile {
                val username = if (host == null) post.username else "${post.username}@$host"
                viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
            },
            menuItems =
            createOpenOriginalMenuItems(post.body.shareUrl) { url ->
                viewModelScope.launch { events.send { it.openUrl(url) } }
            } + createPinMenuItems(post.pinnableNoteId, post.pinned, ::setPinned) +
                createNoteMenuItems(post.deletableNoteId, ::confirmNoteDeletion),
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
        val mentionText: String,
        val mentionSending: Boolean,
    ) {
        fun mentionSendable(): Boolean = !mentionSending && mentionText.isNotBlank()
    }

    private sealed interface PostState {
        data object Loading : PostState

        data class Loaded(
            val post: UserHomeTimelineResult.Post,
        ) : PostState

        data object NotFound : PostState

        data object Failed : PostState
    }
}
