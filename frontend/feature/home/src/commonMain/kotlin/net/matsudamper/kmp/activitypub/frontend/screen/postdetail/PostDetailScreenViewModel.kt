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
                replyTargets = listOf(),
                replyTargetLoad = ReplyTargetLoad.Waiting,
            ),
        )
    private var loadJob: Job? = null
    private var replyTargetJob: Job? = null

    /**
     * 表示には使わないので ViewModelState に入れない。入れると失敗時の削除で投稿の listener が作り直され、即座に取り直しを繰り返す
     */
    private var linkPreviewRequested = false

    /**
     * 返事を待たずに表示を切り替えるので、表示には使わない。続けて押されたときに送り違えないよう、返事までは受け付けない。
     * 返信先の投稿も押せるので、reactionPostId ごとに持つ
     */
    private var likeChangingPostIds: Set<String> = setOf()
    private var boostChangingPostIds: Set<String> = setOf()

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

    private val replyTargetLoadingListener = object : PostDetailScreenUiState.ReplyTargetLoader.Loading.Listener {
        override fun onVisible() {
            loadNextReplyTarget()
        }
    }

    private val replyTargetErrorListener = object : PostDetailScreenUiState.ReplyTargetLoader.Error.Listener {
        override fun onClickRetry() {
            viewModelStateFlow.update { it.copy(replyTargetLoad = ReplyTargetLoad.Waiting) }
            loadNextReplyTarget()
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
                updateBoosted(reactionPostId = change.reactionPostId, boosted = change.boosted)
            }
        }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        replyTargetJob?.cancel()
        viewModelStateFlow.update { it.copy(post = PostState.Loading, replyTargets = listOf(), replyTargetLoad = ReplyTargetLoad.Waiting) }
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

    private fun toggleLike(post: UserHomeTimelineResult.Post) {
        val reactionPostId = post.reactionPostId
        val liked = currentPost(reactionPostId)?.body?.likedByMe ?: return
        if (reactionPostId in likeChangingPostIds) return
        likeChangingPostIds = likeChangingPostIds + reactionPostId
        viewModelScope.launch {
            try {
                updateLiked(reactionPostId = reactionPostId, liked = !liked)
                val error = api.setLiked(postId = reactionPostId, liked = !liked)
                if (error != null) {
                    updateLiked(reactionPostId = reactionPostId, liked = liked)
                    events.send { it.showMessage(error) }
                }
            } finally {
                likeChangingPostIds = likeChangingPostIds - reactionPostId
            }
        }
    }

    private fun toggleBoost(post: UserHomeTimelineResult.Post) {
        val reactionPostId = post.reactionPostId
        val boosted = currentPost(reactionPostId)?.body?.boostedByMe ?: return
        if (reactionPostId in boostChangingPostIds) return
        boostChangingPostIds = boostChangingPostIds + reactionPostId
        viewModelScope.launch {
            try {
                updateBoosted(reactionPostId = reactionPostId, boosted = !boosted)
                val error = api.setBoosted(postId = reactionPostId, boosted = !boosted)
                if (error != null) {
                    updateBoosted(reactionPostId = reactionPostId, boosted = boosted)
                    events.send { it.showMessage(error) }
                }
            } finally {
                boostChangingPostIds = boostChangingPostIds - reactionPostId
            }
        }
    }

    /**
     * 押された後に表示が変わっているかもしれないので、押した時点の投稿ではなく今の投稿を見る
     */
    private fun currentPost(reactionPostId: String): UserHomeTimelineResult.Post? {
        val state = viewModelStateFlow.value
        val loaded = state.post as? PostState.Loaded
        return (listOfNotNull(loaded?.post) + state.replyTargets).firstOrNull { it.reactionPostId == reactionPostId }
    }

    /**
     * 表示されている一番上の投稿の返信先を取る。取れた投稿も返信なら、また表示されたときに続きを取る
     */
    private fun loadNextReplyTarget() {
        val state = viewModelStateFlow.value
        if (state.replyTargetLoad != ReplyTargetLoad.Waiting) return
        val loaded = state.post as? PostState.Loaded ?: return
        val topPost = state.replyTargets.lastOrNull() ?: loaded.post
        if (!topPost.hasReplyTarget) return
        viewModelStateFlow.update { it.copy(replyTargetLoad = ReplyTargetLoad.Loading) }
        replyTargetJob = viewModelScope.launch {
            val result = api.replyTarget(postId = topPost.id)
            viewModelStateFlow.update { current ->
                when (result) {
                    is UserPostDetailResult.Found -> {
                        val shownIds = (listOf(loaded.post) + current.replyTargets).map { it.id }
                        // 返信先が輪になっていると、たどり続けて終わらない
                        if (result.post.id in shownIds) {
                            current.copy(replyTargetLoad = ReplyTargetLoad.Unavailable)
                        } else {
                            current.copy(replyTargets = current.replyTargets + result.post, replyTargetLoad = ReplyTargetLoad.Waiting)
                        }
                    }

                    UserPostDetailResult.NotFound,
                    UserPostDetailResult.NotLoggedIn,
                    -> current.copy(replyTargetLoad = ReplyTargetLoad.Unavailable)

                    is UserPostDetailResult.Failure -> current.copy(replyTargetLoad = ReplyTargetLoad.Failed)
                }
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

    private fun updateLiked(reactionPostId: String, liked: Boolean) {
        updatePosts(reactionPostId) { post -> post.copy(body = post.body.withLiked(liked)) }
    }

    private fun updateBoosted(reactionPostId: String, boosted: Boolean) {
        updatePosts(reactionPostId) { post -> post.copy(body = post.body.withBoosted(boosted)) }
    }

    /**
     * 同じ投稿が返信先と開いている投稿の両方に出ることがあるので、合うものすべてを変える
     */
    private fun updatePosts(reactionPostId: String, transform: (UserHomeTimelineResult.Post) -> UserHomeTimelineResult.Post) {
        val update = { post: UserHomeTimelineResult.Post -> if (post.reactionPostId == reactionPostId) transform(post) else post }
        viewModelStateFlow.update { state ->
            val post = when (val current = state.post) {
                is PostState.Loaded -> PostState.Loaded(update(current.post))
                else -> current
            }
            state.copy(post = post, replyTargets = state.replyTargets.map(update))
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
                    replyTargetLoader = createReplyTargetLoader(post.post, viewModelState),
                    replyTargets = viewModelState.replyTargets.reversed().map { replyTarget -> createReplyTargetItem(replyTarget, viewModelState) },
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

    private fun createReplyTargetLoader(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostDetailScreenUiState.ReplyTargetLoader? {
        val topPost = viewModelState.replyTargets.lastOrNull() ?: post
        return when (viewModelState.replyTargetLoad) {
            ReplyTargetLoad.Waiting,
            ReplyTargetLoad.Loading,
            -> if (topPost.hasReplyTarget) PostDetailScreenUiState.ReplyTargetLoader.Loading(replyTargetLoadingListener) else null

            ReplyTargetLoad.Unavailable -> PostDetailScreenUiState.ReplyTargetLoader.Unavailable

            ReplyTargetLoad.Failed -> PostDetailScreenUiState.ReplyTargetLoader.Error(replyTargetErrorListener)
        }
    }

    /**
     * 返信先は読み返すためのものなので、固定や削除はその投稿の詳細で行う。
     * 返信先は詳細の画面で読めない投稿のこともあるので、押しても詳細を開かない
     */
    private fun createReplyTargetItem(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostItemUiState {
        val host = post.host
        return createPostItemUiState(
            pinned = false,
            deleted = post.deleted,
            boostUndone = false,
            actorLabel = null,
            iconUrl = post.iconUrl,
            displayName = post.displayName,
            displayNameEmojis = post.displayNameEmojis,
            acct = createAcct(post),
            body = post.body,
            linkPreviews = listOf(),
            sensitiveImagesBlurred = true,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorTapAction = AuthorTapAction.OpenProfile {
                val username = if (host == null) post.username else "${post.username}@$host"
                viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
            },
            menuItems = createOpenOriginalMenuItems(post.body.shareUrl) { url ->
                viewModelScope.launch { events.send { it.openUrl(url) } }
            },
            postTapAction = PostTapAction.OpenPostDetail(postId = null, navigate = {}),
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
            onToggleLike = { toggleLike(post) },
            onToggleBoost = { toggleBoost(post) },
            onVisible = {},
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
            onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
            onToggleLike = { toggleLike(post) },
            onToggleBoost = { toggleBoost(post) },
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
        /**
         * 取れた返信先を、開いている投稿に近い順に並べたもの
         */
        val replyTargets: List<UserHomeTimelineResult.Post>,
        val replyTargetLoad: ReplyTargetLoad,
    ) {
        fun mentionSendable(): Boolean = !mentionSending && mentionText.isNotBlank()
    }

    private enum class ReplyTargetLoad {
        /** 一番上の投稿が返信なら、表示されたときに取りに行く */
        Waiting,
        Loading,
        Unavailable,
        Failed,
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
