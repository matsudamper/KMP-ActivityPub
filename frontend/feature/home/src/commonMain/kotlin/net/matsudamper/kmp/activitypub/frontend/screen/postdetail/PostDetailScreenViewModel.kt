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
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.TextTranslator
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLikeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostBody
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostDetailResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostRepliesResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostStamp
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserReaction
import net.matsudamper.kmp.activitypub.frontend.logic.user.likeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.withBoosted
import net.matsudamper.kmp.activitypub.frontend.logic.user.withCounts
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLikeState
import net.matsudamper.kmp.activitypub.frontend.logic.user.withLiked
import net.matsudamper.kmp.activitypub.frontend.screen.AuthorTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.CreateOpenOriginalMenuItemsUseCase
import net.matsudamper.kmp.activitypub.frontend.screen.NoteDeletionState
import net.matsudamper.kmp.activitypub.frontend.screen.POST_TRANSLATION_FAILED_MESSAGE
import net.matsudamper.kmp.activitypub.frontend.screen.PostActorLabel
import net.matsudamper.kmp.activitypub.frontend.screen.PostTapAction
import net.matsudamper.kmp.activitypub.frontend.screen.PostTranslationState
import net.matsudamper.kmp.activitypub.frontend.screen.PostTranslations
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.QuoteLoad
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionChange
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionConfirmation
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionDecision
import net.matsudamper.kmp.activitypub.frontend.screen.ReactionPickerTarget
import net.matsudamper.kmp.activitypub.frontend.screen.confirm
import net.matsudamper.kmp.activitypub.frontend.screen.createNoteMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPinMenuItems
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemQuote
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.screen.decideLikeTap
import net.matsudamper.kmp.activitypub.frontend.screen.decidePickedReaction
import net.matsudamper.kmp.activitypub.frontend.screen.decideStampTap
import net.matsudamper.kmp.activitypub.frontend.screen.dismiss
import net.matsudamper.kmp.activitypub.frontend.screen.loadQuote
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNoteSubmitResult
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNoteSubmitter
import net.matsudamper.kmp.activitypub.frontend.screen.toDialogUiState
import net.matsudamper.kmp.activitypub.frontend.screen.toLikeState
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
    private val languageSettingStore: LanguageSettingStore,
    private val textTranslator: TextTranslator,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val postTranslations = PostTranslations(
        translator = textTranslator,
        languageSettingStore = languageSettingStore,
        coroutineScope = viewModelScope,
        onFailed = { viewModelScope.launch { events.send { it.showMessage(POST_TRANSLATION_FAILED_MESSAGE) } } },
    )
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                translation = PostTranslationState(viewerLanguage = null, translatorSupported = false, progressBySource = mapOf()),
                post = PostState.Loading,
                linkPreviews = listOf(),
                blurToggledImageUrls = setOf(),
                noteDeletion = NoteDeletionState.None,
                reactionPicker = null,
                reactionConfirmation = null,
                mentionText = "",
                mentionSending = false,
                replyTargets = listOf(),
                replyTargetLoad = ReplyTargetLoad.Waiting,
                replies = listOf(),
                repliesLoad = RepliesLoad.Loading,
                quotes = mapOf(),
            ),
        )
    private var loadJob: Job? = null
    private var replyTargetJob: Job? = null
    private var repliesJob: Job? = null

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
    private var reactionPickerOpenCount = 0

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

        override fun onClickClose() {
            viewModelScope.launch { events.send { it.closeToHome() } }
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

    private val repliesErrorListener = object : PostDetailScreenUiState.Replies.Error.Listener {
        override fun onClickRetry() {
            val loaded = viewModelStateFlow.value.post as? PostState.Loaded ?: return
            loadReplies(loaded.post.id)
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
            postTranslations.stateFlow.collect { translation ->
                viewModelStateFlow.update { it.copy(translation = translation) }
            }
        }
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
            // 編集した投稿を書き換わった本文で出す
            api.editedNoteIds.collect { noteId ->
                val loaded = viewModelStateFlow.value.post as? PostState.Loaded
                if (loaded?.post?.deletableNoteId == noteId) load()
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
        repliesJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                post = PostState.Loading,
                replyTargets = listOf(),
                replyTargetLoad = ReplyTargetLoad.Waiting,
                replies = listOf(),
                repliesLoad = RepliesLoad.Loading,
            )
        }
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
            if (post is PostState.Loaded) {
                loadReplies(post.post.id)
                refreshCounts(post.post)
            }
        }
    }

    /**
     * 取り直しでは、取れるまで今の返信を出したままにする
     *
     * @param loadedPostId 保存していない投稿の id で開いたときも、開いたときに保存した投稿の id で引く
     */
    private fun loadReplies(loadedPostId: String) {
        repliesJob?.cancel()
        viewModelStateFlow.update { it.copy(repliesLoad = RepliesLoad.Loading) }
        repliesJob = viewModelScope.launch {
            val result = api.replies(postId = loadedPostId)
            viewModelStateFlow.update { state ->
                when (result) {
                    is UserPostRepliesResult.Success -> state.copy(replies = result.replies, repliesLoad = RepliesLoad.Loaded)

                    UserPostRepliesResult.NotLoggedIn,
                    is UserPostRepliesResult.Failure,
                    -> state.copy(repliesLoad = RepliesLoad.Failed)
                }
            }
        }
    }

    /**
     * 他のサーバーの投稿は、開くまでこのサーバーが知っている数しか出ていないので、開いてから相手のサーバーの数に取り直す
     */
    private suspend fun refreshCounts(post: UserHomeTimelineResult.Post) {
        val counts = api.postCounts(postId = post.id) ?: return
        updatePosts(post.reactionPostId) { current ->
            // 取っている間に押したいいねやブーストは、取った数に入っていないことがあるので、押した後の数のまま残す
            val unchangedCounts = counts.copy(
                favouriteCount = if (current.body.likeState() == post.body.likeState()) counts.favouriteCount else current.body.favouriteCount,
                boostCount = if (current.body.boostedByMe == post.body.boostedByMe) counts.boostCount else current.body.boostCount,
            )
            current.copy(body = current.body.withCounts(unchangedCounts))
        }
    }

    private fun toggleLike(post: UserHomeTimelineResult.Post) {
        val reactionPostId = post.reactionPostId
        val body = currentPost(reactionPostId)?.body ?: return
        if (body.likedByMe == null) return
        if (reactionPostId in likeChangingPostIds) return
        val confirmation = decideLikeTap(reactionPostId, body)
        if (confirmation != null) {
            viewModelStateFlow.update { it.copy(reactionConfirmation = confirmation) }
            return
        }
        val liked = body.plainLikedByMe
        changeLikeState(reactionPostId = reactionPostId, current = body.likeState(), next = body.withLiked(!liked).likeState()) {
            api.setLiked(postId = reactionPostId, liked = !liked)
        }
    }

    private fun openReactionPicker(post: UserHomeTimelineResult.Post) {
        val body = currentPost(post.reactionPostId)?.body ?: return
        // 送っている途中の状態を開いたときの状態として持つと、両方失敗したときに送れていない状態へ戻してしまう
        if (post.reactionPostId in likeChangingPostIds) return
        reactionPickerOpenCount++
        val target = ReactionPickerTarget(
            reactionPostId = post.reactionPostId,
            current = body.likeState(),
            requestKey = "reaction-picker-$reactionPickerOpenCount",
        )
        viewModelStateFlow.update { it.copy(reactionPicker = target) }
    }

    /**
     * ピッカーで選んだとき
     */
    private fun pickReaction(target: ReactionPickerTarget, reaction: UserReaction) {
        viewModelStateFlow.update { it.copy(reactionPicker = null) }
        handleReactionDecision(decidePickedReaction(target.reactionPostId, target.current, reaction))
    }

    /**
     * ピッカーの「リアクションを外す」を押したとき
     */
    private fun requestReactionRemoval(target: ReactionPickerTarget) {
        val current = target.current as? UserLikeState.Reacted
        viewModelStateFlow.update {
            it.copy(
                reactionPicker = null,
                reactionConfirmation = current?.let { reacted -> ReactionConfirmation(target.reactionPostId, reacted, ReactionChange.Remove) },
            )
        }
    }

    /**
     * @param body 押したときの状態
     */
    private fun clickStamp(reactionPostId: String, body: UserPostBody, stamp: UserPostStamp) {
        if (reactionPostId in likeChangingPostIds) return
        handleReactionDecision(decideStampTap(reactionPostId, body, stamp))
    }

    private fun handleReactionDecision(decision: ReactionDecision) {
        when (decision) {
            is ReactionDecision.Apply -> sendReaction(decision.reactionPostId, decision.current, decision.reaction)
            is ReactionDecision.Confirm -> viewModelStateFlow.update { it.copy(reactionConfirmation = decision.confirmation) }
            ReactionDecision.None -> Unit
        }
    }

    private fun confirmReaction(confirmation: ReactionConfirmation) {
        viewModelStateFlow.update { it.copy(reactionConfirmation = null) }
        val reactionPostId = confirmation.reactionPostId
        when (val next = confirmation.next) {
            ReactionChange.Remove -> sendReaction(reactionPostId, confirmation.current, reaction = null)

            is ReactionChange.React -> sendReaction(reactionPostId, confirmation.current, next.reaction)

            ReactionChange.PlainLike -> changeLikeState(reactionPostId, confirmation.current, UserLikeState.Liked) {
                api.setLiked(postId = reactionPostId, liked = true)
            }
        }
    }

    /**
     * @param reaction null なら外す
     */
    private fun sendReaction(reactionPostId: String, current: UserLikeState, reaction: UserReaction?) {
        changeLikeState(reactionPostId, current, reaction.toLikeState()) {
            api.setReaction(postId = reactionPostId, reaction = reaction)
        }
    }

    /**
     * 返事を待たずに表示を切り替え、送れなかったら戻す
     *
     * @param send 失敗したときに見せる文言を返す
     */
    private fun changeLikeState(reactionPostId: String, current: UserLikeState, next: UserLikeState, send: suspend () -> String?) {
        if (reactionPostId in likeChangingPostIds) return
        likeChangingPostIds = likeChangingPostIds + reactionPostId
        viewModelScope.launch {
            try {
                updateLikeState(reactionPostId = reactionPostId, state = next)
                val error = send()
                if (error != null) {
                    updateLikeState(reactionPostId = reactionPostId, state = current)
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
        return (listOfNotNull(loaded?.post) + state.replyTargets + state.replies).firstOrNull { it.reactionPostId == reactionPostId }
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

    private fun navigateToEditPost(noteId: String) {
        viewModelScope.launch { events.send { it.navigateToEditPost(noteId) } }
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
        // 保存していない投稿の id で開いたときは、開いたときに保存した投稿の id でないと返信先を引けない
        val replyToPostId = (state.post as? PostState.Loaded)?.post?.id ?: return
        val body = state.mentionText
        viewModelStateFlow.update { it.copy(mentionSending = true) }
        viewModelScope.launch {
            val error =
                try {
                    val result =
                        postNoteSubmitter.submit(body = body, requestId = mentionRequestId, images = listOf(), replyToPostId = replyToPostId, quotePostId = null)
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
            loadReplies(replyToPostId)
            val loaded = viewModelStateFlow.value.post as? PostState.Loaded
            if (loaded != null) refreshCounts(loaded.post)
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

            // メンションでは引用しない
            UserPostNoteResult.QuoteTargetNotFound,
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

    private fun updateLikeState(reactionPostId: String, state: UserLikeState) {
        updatePosts(reactionPostId) { post -> post.copy(body = post.body.withLikeState(state)) }
    }

    private fun updateBoosted(reactionPostId: String, boosted: Boolean) {
        updatePosts(reactionPostId) { post -> post.copy(body = post.body.withBoosted(boosted)) }
    }

    /**
     * 同じ投稿が返信先や返信と開いている投稿の両方に出ることがあるので、合うものすべてを変える
     */
    private fun updatePosts(reactionPostId: String, transform: (UserHomeTimelineResult.Post) -> UserHomeTimelineResult.Post) {
        val update = { post: UserHomeTimelineResult.Post -> if (post.reactionPostId == reactionPostId) transform(post) else post }
        viewModelStateFlow.update { state ->
            val post = when (val current = state.post) {
                is PostState.Loaded -> PostState.Loaded(update(current.post))
                else -> current
            }
            state.copy(post = post, replyTargets = state.replyTargets.map(update), replies = state.replies.map(update))
        }
    }

    private fun toggleImageBlur(imageUrls: List<String>, toggled: Boolean) {
        viewModelStateFlow.update {
            it.copy(blurToggledImageUrls = it.blurToggledImageUrls.withBlurToggled(imageUrls, toggled))
        }
    }

    /**
     * 保存していない投稿の id で開いたときは OGP を引けないので、開いたときに保存した投稿の id で引く
     */
    private fun loadLinkPreviews(loadedPostId: String) {
        if (linkPreviewRequested) return
        linkPreviewRequested = true
        viewModelScope.launch {
            when (val result = api.linkPreviews(loadedPostId)) {
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

    /**
     * 返信先や返信の投稿にも引用はあるので、投稿の id ごとに持つ
     */
    private fun loadQuote(postId: String) {
        if (postId in viewModelStateFlow.value.quotes) return
        viewModelStateFlow.update { it.copy(quotes = it.quotes + (postId to QuoteLoad.Loading)) }
        viewModelScope.launch {
            val load = api.loadQuote(postId)
            viewModelStateFlow.update { state ->
                state.copy(quotes = if (load == null) state.quotes - postId else state.quotes + (postId to load))
            }
        }
    }

    private fun createQuote(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostItemUiState.Quote? =
        createPostItemQuote(
            hasQuoteTarget = post.hasQuoteTarget,
            load = viewModelState.quotes[post.id],
            onOpenPostDetail = { postId -> viewModelScope.launch { events.send { it.navigateToPostDetail(postId) } } },
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
        )

    private fun createUiState(viewModelState: ViewModelState): PostDetailScreenUiState {
        return PostDetailScreenUiState(
            content = when (val post = viewModelState.post) {
                PostState.Loading -> PostDetailScreenUiState.Content.Loading

                is PostState.Loaded -> PostDetailScreenUiState.Content.Loaded(
                    replyTargetLoader = createReplyTargetLoader(post.post, viewModelState),
                    replyTargets = viewModelState.replyTargets.reversed().map { replyTarget -> createThreadPostItem(replyTarget, viewModelState) },
                    post = createPostItem(post.post, viewModelState),
                    replies = createReplies(viewModelState),
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
            reactionPickerDialog = viewModelState.reactionPicker?.let { target ->
                target.toDialogUiState(
                    onPick = { reaction -> pickReaction(target, reaction) },
                    onRemove = { requestReactionRemoval(target) },
                    onDismiss = { viewModelStateFlow.update { it.copy(reactionPicker = null) } },
                )
            },
            reactionConfirmDialog = viewModelState.reactionConfirmation?.let { confirmation ->
                confirmation.toDialogUiState(
                    onConfirm = { confirmReaction(confirmation) },
                    onDismiss = { viewModelStateFlow.update { it.copy(reactionConfirmation = null) } },
                )
            },
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

    private fun createReplies(viewModelState: ViewModelState): PostDetailScreenUiState.Replies {
        val items = viewModelState.replies.map { reply ->
            PostDetailScreenUiState.Reply(listKey = "reply-${reply.id}", post = createThreadPostItem(reply, viewModelState))
        }
        return when (viewModelState.repliesLoad) {
            RepliesLoad.Loading -> PostDetailScreenUiState.Replies.Loading(items)
            RepliesLoad.Loaded -> PostDetailScreenUiState.Replies.Loaded(items)
            RepliesLoad.Failed -> PostDetailScreenUiState.Replies.Error(items, repliesErrorListener)
        }
    }

    /**
     * 開いている投稿の上下に並べる、返信先と返信。読み返すためのものなので、固定や削除は押して開いたその投稿の詳細で行う
     */
    private fun createThreadPostItem(
        post: UserHomeTimelineResult.Post,
        viewModelState: ViewModelState,
    ): PostItemUiState {
        val host = post.host
        return createPostItemUiState(
            replyLabel = null,
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
            quote = createQuote(post, viewModelState),
            sensitiveImagesBlurred = true,
            translation = viewModelState.translation,
            onToggleTranslation = postTranslations::toggle,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorTapAction = AuthorTapAction.OpenProfile {
                val username = if (host == null) post.username else "${post.username}@$host"
                viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
            },
            menuItems = createOpenOriginalMenuItems(post.body.shareUrl) { url ->
                viewModelScope.launch { events.send { it.openUrl(url) } }
            },
            postTapAction = PostTapAction.OpenPostDetail(postId = post.id) { postId ->
                viewModelScope.launch { events.send { it.navigateToPostDetail(postId) } }
            },
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
            onToggleLike = { toggleLike(post) },
            onToggleBoost = { toggleBoost(post) },
            onReply = { viewModelScope.launch { events.send { it.navigateToReplyPost(post.id) } } },
            onQuote = { viewModelScope.launch { events.send { it.navigateToQuotePost(post.id) } } },
            onOpenReactionPicker = { openReactionPicker(post) },
            onClickStamp = { stamp ->
                val body = currentPost(post.reactionPostId)?.body
                if (body != null) clickStamp(reactionPostId = post.reactionPostId, body = body, stamp = stamp)
            },
            onOpenRevisions = null,
            onVisible = {
                if (post.hasQuoteTarget) {
                    loadQuote(post.id)
                }
            },
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
            replyLabel = null,
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
            quote = createQuote(post, viewModelState),
            sensitiveImagesBlurred = true,
            translation = viewModelState.translation,
            onToggleTranslation = postTranslations::toggle,
            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
            authorTapAction = AuthorTapAction.OpenProfile {
                val username = if (host == null) post.username else "${post.username}@$host"
                viewModelScope.launch { events.send { it.navigateToUserProfile(username) } }
            },
            menuItems =
            createOpenOriginalMenuItems(post.body.shareUrl) { url ->
                viewModelScope.launch { events.send { it.openUrl(url) } }
            } + createPinMenuItems(post.pinnableNoteId, post.pinned, ::setPinned) +
                createNoteMenuItems(post.deletableNoteId, onClickEdit = ::navigateToEditPost, onClickDelete = ::confirmNoteDeletion),
            postTapAction = PostTapAction.AlreadyOnPostDetail,
            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
            onOpenProfile = { acct -> viewModelScope.launch { events.send { it.navigateToUserProfile(acct) } } },
            onOpenImage = { urls, index -> viewModelScope.launch { events.send { it.navigateToImage(urls, index) } } },
            onToggleImageBlur = { imageUrls, toggled -> toggleImageBlur(imageUrls, toggled) },
            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
            onToggleLike = { toggleLike(post) },
            onToggleBoost = { toggleBoost(post) },
            onReply = { viewModelScope.launch { events.send { it.navigateToReplyPost(post.id) } } },
            onQuote = { viewModelScope.launch { events.send { it.navigateToQuotePost(post.id) } } },
            onOpenReactionPicker = { openReactionPicker(post) },
            onClickStamp = { stamp ->
                val body = currentPost(post.reactionPostId)?.body
                if (body != null) clickStamp(reactionPostId = post.reactionPostId, body = body, stamp = stamp)
            },
            onOpenRevisions = { viewModelScope.launch { events.send { it.navigateToPostRevisions(post.id) } } },
            onVisible = {
                if (post.body.linkUrls.isNotEmpty()) {
                    loadLinkPreviews(post.id)
                }
                if (post.hasQuoteTarget) {
                    loadQuote(post.id)
                }
            },
        )
    }

    internal interface Event {
        fun navigateBack()

        fun closeToHome()

        fun showMessage(message: String)

        fun navigateToUserProfile(username: String)

        fun navigateToPostDetail(postId: String)

        fun navigateToQuotePost(postId: String)

        fun navigateToReplyPost(postId: String)

        /**
         * @param noteId 編集する投稿の deletableNoteId
         */
        fun navigateToEditPost(noteId: String)

        fun navigateToPostRevisions(postId: String)

        fun openUrl(url: String)

        fun shareUrl(url: String)

        fun navigateToImage(urls: List<String>, index: Int)
    }

    private data class ViewModelState(
        val translation: PostTranslationState,
        val post: PostState,
        val linkPreviews: List<UserLinkPreviewsResult.LinkPreview>,
        val blurToggledImageUrls: Set<String>,
        val noteDeletion: NoteDeletionState,
        val reactionPicker: ReactionPickerTarget?,
        val reactionConfirmation: ReactionConfirmation?,
        val mentionText: String,
        val mentionSending: Boolean,
        /**
         * 取れた返信先を、開いている投稿に近い順に並べたもの
         */
        val replyTargets: List<UserHomeTimelineResult.Post>,
        val replyTargetLoad: ReplyTargetLoad,
        /**
         * 開いている投稿に付いた返信を、古い順に並べたもの
         */
        val replies: List<UserHomeTimelineResult.Post>,
        val repliesLoad: RepliesLoad,
        /**
         * 投稿の id ごとの引用先
         */
        val quotes: Map<String, QuoteLoad>,
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

    private enum class RepliesLoad {
        Loading,
        Loaded,
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
