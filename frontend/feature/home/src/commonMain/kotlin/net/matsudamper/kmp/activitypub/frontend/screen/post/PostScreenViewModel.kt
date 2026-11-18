package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserEditableNoteBodyResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostDetailResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUploadNoteImageResult
import net.matsudamper.kmp.activitypub.frontend.screen.QuoteLoad
import net.matsudamper.kmp.activitypub.frontend.screen.createQuote
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.PickedReaction
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.TextInsertion
import net.matsudamper.kmp.activitypub.frontend.ui.WebpImage
import net.matsudamper.kmp.activitypub.frontend.ui.WebpResult
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PostScreenViewModel(
    @InjectedParam private val target: PostScreenTarget,
    private val postNoteSubmitter: PostNoteSubmitter,
    private val api: UserApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                body = savedStateHandle.get<String>(BODY_KEY) ?: initialBody(),
                contentWarning = savedStateHandle.get<String>(CONTENT_WARNING_KEY),
                images = listOf(),
                loadingImage = false,
                submitting = false,
                error = null,
                emojiPickerRequestKey = null,
                bodyInsertion = null,
                quote = when (target) {
                    PostScreenTarget.NewNote, is PostScreenTarget.Reply, is PostScreenTarget.Edit, is PostScreenTarget.Mention -> null
                    is PostScreenTarget.Quote -> QuoteLoad.Loading
                },
                replyTarget = initialReplyToPostId()?.let { postId -> ReplyTarget(postId = postId, load = QuoteLoad.Loading) },
                editing = initialEditingNoteId()?.let { noteId ->
                    Editing(noteId = noteId, originalBody = savedStateHandle.get<String>(EDITING_ORIGINAL_BODY_KEY))
                },
                discardConfirmation = null,
            ),
        )
    private var replyTargetJob: Job? = null
    private var editableBodyJob: Job? = null

    private val editTargetListener =
        object : PostScreenUiState.EditTargetListener {
            override fun onClickCancel() {
                if (viewModelStateFlow.value.submitting) return
                finishEditing()
            }
        }

    private val discardDraftDialogListener =
        object : PostScreenUiState.DiscardDraftDialogListener {
            override fun onClickDiscard() {
                when (val confirmation = viewModelStateFlow.value.discardConfirmation ?: return) {
                    is DiscardConfirmation.Edit -> startEditing(confirmation.noteId)
                    is DiscardConfirmation.Reply -> startReplyAfterEditing(confirmation.postId)
                }
            }

            override fun onDismiss() {
                viewModelStateFlow.update { it.copy(discardConfirmation = null) }
            }
        }

    private val replyTargetListener =
        object : PostScreenUiState.ReplyTargetListener {
            override fun onClickRemove() {
                if (viewModelStateFlow.value.submitting) return
                changeReplyTarget(postId = null)
            }
        }

    private val emojiPickerListener =
        object : ReactionPickerDialogUiState.Listener {
            override fun onPick(reaction: PickedReaction) {
                viewModelStateFlow.update { it.copy(emojiPickerRequestKey = null) }
                if (viewModelStateFlow.value.submitting) return
                val insertion = when (reaction) {
                    is PickedReaction.Unicode -> TextInsertion(id = Uuid.random().toString(), text = reaction.emoji, separatedFromWords = false)
                    is PickedReaction.Custom -> TextInsertion(id = Uuid.random().toString(), text = ":${reaction.shortcode}:", separatedFromWords = true)
                }
                viewModelStateFlow.update { it.copy(bodyInsertion = insertion) }
            }

            override fun onClickRemove() = Unit

            override fun onDismiss() {
                viewModelStateFlow.update { it.copy(emojiPickerRequestKey = null) }
            }
        }

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        changeBody(text)
                    }

                    override fun onBodyInsertionHandled() {
                        viewModelStateFlow.update { it.copy(bodyInsertion = null) }
                    }

                    override fun onClickContentWarning() {
                        if (viewModelStateFlow.value.submitting) return
                        changeContentWarning(if (viewModelStateFlow.value.contentWarning == null) "" else null)
                    }

                    override fun onContentWarningChanged(text: String) {
                        if (viewModelStateFlow.value.contentWarning == text) return
                        changeContentWarning(text)
                    }

                    override fun onClickAddEmoji() {
                        if (viewModelStateFlow.value.submitting) return
                        viewModelStateFlow.update { it.copy(emojiPickerRequestKey = Uuid.random().toString()) }
                    }

                    override fun onClickAddImage() {
                        if (viewModelStateFlow.value.imageAddable().not()) return
                        viewModelScope.launch {
                            events.send { it.openImagePicker() }
                        }
                    }

                    override fun onImagePicked(file: PickedImageFile) {
                        addImage(file)
                    }

                    override fun onReplyRequested(postId: String) {
                        // 送信中に返信先を替えると、送り終えたときに新しい返信先まで消してしまう
                        if (viewModelStateFlow.value.submitting) return
                        requestReply(postId)
                    }

                    override fun onEditRequested(noteId: String) {
                        requestEditing(noteId)
                    }

                    override fun onClickPost() {
                        post()
                    }

                    override fun onPressPostShortcut() {
                        post()
                    }

                    override fun onClickClose() {
                        // 送信中に閉じると、投稿できたかどうかを画面に出せない
                        if (viewModelStateFlow.value.submitting) return
                        viewModelScope.launch {
                            events.send { it.closeToHome() }
                        }
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        createUiState(
                            viewModelState = viewModelState,
                            listener = uiState.listener,
                        )
                    }
                }
            }
        }.asStateFlow()

    init {
        if (target is PostScreenTarget.Quote) {
            viewModelScope.launch {
                val quote = when (val result = api.postDetail(target.postId)) {
                    is UserPostDetailResult.Found -> if (result.post.deleted) QuoteLoad.Unavailable else QuoteLoad.Loaded(result.post)

                    UserPostDetailResult.NotFound,
                    is UserPostDetailResult.Failure,
                    -> QuoteLoad.Unavailable
                }
                viewModelStateFlow.update { it.copy(quote = quote) }
            }
        }
        val replyTarget = viewModelStateFlow.value.replyTarget
        if (replyTarget != null) loadReplyTarget(replyTarget.postId)
        val editing = viewModelStateFlow.value.editing
        if (editing != null && editing.originalBody == null) loadEditableBody(editing.noteId)
    }

    /**
     * 編集をやめた後に画面が作り直されても、開いたときの編集に戻さない
     */
    private fun initialEditingNoteId(): String? =
        if (savedStateHandle.contains(EDITING_NOTE_ID_KEY)) {
            savedStateHandle.get<String>(EDITING_NOTE_ID_KEY)?.takeIf { it.isNotEmpty() }
        } else {
            (target as? PostScreenTarget.Edit)?.noteId
        }

    /**
     * 書きかけの投稿があれば、消してよいか確かめてから編集を始める
     */
    private fun requestEditing(noteId: String) {
        val state = viewModelStateFlow.value
        // 送信中に書き換えると、送り終えたときに編集を始めた本文まで消してしまう
        if (state.submitting) return
        if (state.editing?.noteId == noteId) return
        if (state.hasDraft()) {
            viewModelStateFlow.update { it.copy(discardConfirmation = DiscardConfirmation.Edit(noteId)) }
        } else {
            startEditing(noteId)
        }
    }

    /**
     * 編集中に返信を始めると、送信が返信ではなく編集になってしまうので、編集をやめてから返信先を入れる。
     * 編集中の本文を書き換えていれば、消してよいか確かめる
     */
    private fun requestReply(postId: String) {
        val state = viewModelStateFlow.value
        if (state.editing == null) {
            changeReplyTarget(postId = postId)
            return
        }
        if (state.hasDraft()) {
            viewModelStateFlow.update { it.copy(discardConfirmation = DiscardConfirmation.Reply(postId)) }
        } else {
            startReplyAfterEditing(postId)
        }
    }

    private fun startReplyAfterEditing(postId: String) {
        viewModelStateFlow.update { it.copy(discardConfirmation = null) }
        finishEditing()
        changeReplyTarget(postId = postId)
    }

    private fun startEditing(noteId: String) {
        val state = viewModelStateFlow.value
        savedStateHandle[BODY_KEY] = ""
        savedStateHandle[CONTENT_WARNING_KEY] = null
        savedStateHandle[REPLY_TO_POST_ID_KEY] = ""
        savedStateHandle[EDITING_NOTE_ID_KEY] = noteId
        savedStateHandle.remove<String>(EDITING_ORIGINAL_BODY_KEY)
        replyTargetJob?.cancel()
        editableBodyJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                body = "",
                contentWarning = null,
                images = listOf(),
                quote = null,
                replyTarget = null,
                editing = Editing(noteId = noteId, originalBody = null),
                discardConfirmation = null,
                error = null,
            )
        }
        state.images.forEach { it.image.release() }
        loadEditableBody(noteId)
    }

    private fun loadEditableBody(noteId: String) {
        editableBodyJob = viewModelScope.launch {
            val result = api.editableNoteBody(noteId)
            if (viewModelStateFlow.value.editing?.noteId != noteId) return@launch
            when (result) {
                is UserEditableNoteBodyResult.Success -> {
                    savedStateHandle[BODY_KEY] = result.body
                    savedStateHandle[EDITING_ORIGINAL_BODY_KEY] = result.body
                    viewModelStateFlow.update { it.copy(body = result.body, editing = Editing(noteId = noteId, originalBody = result.body)) }
                }

                is UserEditableNoteBodyResult.Failure -> {
                    events.send { it.showMessage(result.message) }
                    finishEditing()
                }
            }
        }
    }

    /**
     * 編集のために開いた画面は閉じ、投稿カラムは新しい投稿を書く状態に戻す
     */
    private fun finishEditing() {
        editableBodyJob?.cancel()
        if (target is PostScreenTarget.Edit) {
            viewModelScope.launch { events.send { it.closeToHome() } }
            return
        }
        savedStateHandle[BODY_KEY] = ""
        savedStateHandle[EDITING_NOTE_ID_KEY] = ""
        savedStateHandle.remove<String>(EDITING_ORIGINAL_BODY_KEY)
        viewModelStateFlow.update { it.copy(body = "", editing = null, error = null) }
    }

    private fun initialBody(): String =
        when (target) {
            is PostScreenTarget.Mention -> "@${target.acct} "
            PostScreenTarget.NewNote, is PostScreenTarget.Quote, is PostScreenTarget.Reply, is PostScreenTarget.Edit -> ""
        }

    /**
     * 外した後に画面が作り直されても、開いたときの返信先に戻さない
     */
    private fun initialReplyToPostId(): String? =
        if (savedStateHandle.contains(REPLY_TO_POST_ID_KEY)) {
            savedStateHandle.get<String>(REPLY_TO_POST_ID_KEY)?.takeIf { it.isNotEmpty() }
        } else {
            (target as? PostScreenTarget.Reply)?.postId
        }

    /**
     * @param postId 返信をやめるなら null
     */
    private fun changeReplyTarget(postId: String?) {
        if (postId == viewModelStateFlow.value.replyTarget?.postId) return
        savedStateHandle[REPLY_TO_POST_ID_KEY] = postId.orEmpty()
        renewRequestId()
        replyTargetJob?.cancel()
        viewModelStateFlow.update { state ->
            state.copy(replyTarget = postId?.let { ReplyTarget(postId = it, load = QuoteLoad.Loading) }, error = null)
        }
        if (postId != null) loadReplyTarget(postId)
    }

    private fun loadReplyTarget(postId: String) {
        replyTargetJob = viewModelScope.launch {
            val load = when (val result = api.postDetail(postId)) {
                is UserPostDetailResult.Found -> if (result.post.deleted) QuoteLoad.Unavailable else QuoteLoad.Loaded(result.post)

                UserPostDetailResult.NotFound,
                is UserPostDetailResult.Failure,
                -> QuoteLoad.Unavailable
            }
            viewModelStateFlow.update { state ->
                if (state.replyTarget?.postId == postId) state.copy(replyTarget = state.replyTarget.copy(load = load)) else state
            }
        }
    }

    private fun changeBody(text: String) {
        // Web の入力欄は blur でも同じ本文を通知する。送信直後の blur で再送用の ID を変えないため
        if (text == viewModelStateFlow.value.body) return
        savedStateHandle[BODY_KEY] = text
        renewRequestId()
        viewModelStateFlow.update { it.copy(body = text, error = null) }
    }

    /**
     * @param text 閲覧注意にしないなら null
     */
    private fun changeContentWarning(text: String?) {
        savedStateHandle[CONTENT_WARNING_KEY] = text
        renewRequestId()
        viewModelStateFlow.update { it.copy(contentWarning = text, error = null) }
    }

    /**
     * 読み込みも viewModelScope で行う。画面のスコープだと回転などで止まり、loadingImage が下りなくなる
     */
    private fun addImage(file: PickedImageFile) {
        if (viewModelStateFlow.value.imageAddable().not()) return
        viewModelStateFlow.update { it.copy(loadingImage = true) }
        viewModelScope.launch {
            val image =
                try {
                    readImage(file)
                } finally {
                    viewModelStateFlow.update { it.copy(loadingImage = false) }
                }
            if (image == null) return@launch
            // 読んでいる間に編集を始めたら、編集では画像を送らないので捨てる
            if (viewModelStateFlow.value.editing != null) {
                image.release()
                return@launch
            }
            renewRequestId()
            viewModelStateFlow.update { state ->
                state.copy(images = state.images + SelectedImage(id = Uuid.random().toString(), image = image), error = null)
            }
        }
    }

    private fun removeImage(id: String) {
        if (viewModelStateFlow.value.submitting) return
        renewRequestId()
        val removed = viewModelStateFlow.value.images.filter { it.id == id }
        viewModelStateFlow.update { state -> state.copy(images = state.images.filterNot { it.id == id }, error = null) }
        removed.forEach { it.image.release() }
    }

    /**
     * WebP に描き直して EXIF を落とす。読めないか送れなければメッセージを出して null
     */
    private suspend fun readImage(file: PickedImageFile): WebpImage? {
        return when (val webp = file.toWebp(maxBytes = MAX_IMAGE_BYTES)) {
            is WebpResult.Success -> webp.image

            WebpResult.TooLarge -> {
                events.send { it.showMessage("画像を WebP にすると 20MB を超えるため送れません") }
                null
            }

            WebpResult.TooManyPixels -> {
                events.send { it.showMessage("画像の画素数が多すぎます。6400 万画素までにしてください") }
                null
            }

            WebpResult.Unreadable -> {
                events.send { it.showMessage("画像として読み込めませんでした") }
                null
            }
        }
    }

    private fun post() {
        val state = viewModelStateFlow.value
        if (state.postable().not()) return
        val editing = state.editing
        if (editing != null) {
            update(noteId = editing.noteId, body = state.body)
            return
        }

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error =
                try {
                    val result =
                        postNoteSubmitter.submit(
                            body = state.body,
                            requestId = requestId(),
                            contentWarning = state.contentWarning,
                            images = state.images.map { it.image },
                            // 開いた投稿が保存していない他のサーバーの投稿だと、読み込んだときに保存した投稿の id でないと返信できない
                            replyToPostId = (state.replyTarget?.load as? QuoteLoad.Loaded)?.post?.id,
                            // 開いた投稿が保存していない他のサーバーの投稿だと、読み込んだときに保存した投稿の id でないと引用できない
                            quotePostId = (state.quote as? QuoteLoad.Loaded)?.post?.id,
                            mentionAcct = (target as? PostScreenTarget.Mention)?.acct,
                        )
                    when (result) {
                        is PostNoteSubmitResult.UploadFailed -> uploadErrorMessage(result.result)
                        is PostNoteSubmitResult.Submitted -> postErrorMessage(result.result)
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            if (error != null) {
                viewModelStateFlow.update { it.copy(error = error) }
                events.send { it.showMessage(error) }
                return@launch
            }
            savedStateHandle[BODY_KEY] = ""
            savedStateHandle[CONTENT_WARNING_KEY] = null
            savedStateHandle[REPLY_TO_POST_ID_KEY] = ""
            renewRequestId()
            viewModelStateFlow.update { it.copy(body = "", contentWarning = null, images = listOf(), replyTarget = null) }
            state.images.forEach { it.image.release() }
            events.send { it.navigateBack() }
        }
    }

    private fun update(noteId: String, body: String) {
        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error =
                try {
                    api.editNote(noteId = noteId, body = body)
                } finally {
                    viewModelStateFlow.update { it.copy(submitting = false) }
                }
            if (error != null) {
                viewModelStateFlow.update { it.copy(error = error) }
                events.send { it.showMessage(error) }
                return@launch
            }
            if (target !is PostScreenTarget.Edit) finishEditing()
            events.send { it.navigateBack() }
        }
    }

    /**
     * 置けたら null
     */
    private fun uploadErrorMessage(result: UserUploadNoteImageResult): String? =
        when (result) {
            is UserUploadNoteImageResult.Success -> null
            UserUploadNoteImageResult.NotLoggedIn -> "ログインしていません"
            UserUploadNoteImageResult.TooLarge -> "画像が大きすぎます"
            UserUploadNoteImageResult.UnsupportedImage -> "送れない形式の画像があります"
            UserUploadNoteImageResult.DescriptionTooLong -> "画像の説明が長すぎます"
            UserUploadNoteImageResult.UnsupportedOperation -> "このサーバーでは画像を投稿できません"
            UserUploadNoteImageResult.InternalServerError -> "サーバーでエラーが起きました。時間をおいてもう一度試してください"
            is UserUploadNoteImageResult.Failure -> result.message
        }

    /**
     * 投稿できたら null
     */
    private fun postErrorMessage(result: UserPostNoteResult): String? =
        when (result) {
            UserPostNoteResult.Success -> null
            UserPostNoteResult.NotLoggedIn -> "ログインしていません"
            UserPostNoteResult.EmptyBody -> "本文が空です"
            UserPostNoteResult.TooLong -> "本文が長すぎます"
            UserPostNoteResult.ContentWarningTooLong -> "注意書きが長すぎます"
            UserPostNoteResult.FederationDisabled -> "サーバーが投稿を配れる設定になっていません"
            UserPostNoteResult.TooManyImages -> "画像は $MAX_IMAGES 枚までにしてください"
            UserPostNoteResult.ImageNotFound -> "画像を送れませんでした。もう一度投稿してください"
            UserPostNoteResult.ReplyTargetNotFound -> "返信先の投稿が見つかりません"
            UserPostNoteResult.QuoteTargetNotFound -> "引用する投稿が見つかりません"
            UserPostNoteResult.MentionTargetNotFound -> "宛先のユーザーが見つかりません"
            is UserPostNoteResult.Failure -> result.message
        }

    private fun requestId(): String =
        savedStateHandle.get<String>(REQUEST_ID_KEY)
            ?: newRequestId().also { savedStateHandle[REQUEST_ID_KEY] = it }

    /**
     * 応答が失われて送り直すときだけ同じ ID にし、書き直した投稿は別の投稿として扱う
     */
    private fun renewRequestId() {
        savedStateHandle[REQUEST_ID_KEY] = newRequestId()
    }

    private fun newRequestId(): String = Uuid.random().toString()

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: PostScreenUiState.Listener,
    ): PostScreenUiState =
        PostScreenUiState(
            title = if (viewModelState.editing == null) "投稿" else "投稿を編集",
            body = viewModelState.body,
            bodyInsertion = viewModelState.bodyInsertion,
            contentWarningInputVisible = viewModelState.editing == null && viewModelState.contentWarning != null,
            contentWarning = viewModelState.contentWarning.orEmpty(),
            contentWarningButtonVisible = viewModelState.editing == null,
            contentWarningButtonChecked = viewModelState.contentWarning != null,
            contentWarningButtonEnabled = viewModelState.submitting.not(),
            bodyLoadingVisible = viewModelState.editableBodyLoading(),
            inputHasError = viewModelState.error != null,
            inputEnabled = viewModelState.submitting.not() && viewModelState.editableBodyLoading().not(),
            closeButtonEnabled = viewModelState.submitting.not(),
            postButtonEnabled = viewModelState.postable(),
            postButtonText = if (viewModelState.editing == null) "投稿" else "更新",
            images =
            viewModelState.images.map { selected ->
                PostScreenUiState.Image(
                    preview = selected.preview,
                    removeButtonEnabled = viewModelState.submitting.not(),
                    listener = selected.removeListener,
                )
            },
            quote = viewModelState.quote?.let { load ->
                // 書いている途中の本文を残すため、引用先の中身へは移らない
                createQuote(load = load, onOpenPostDetail = null, onOpenUrl = {}, onOpenProfile = {})
            },
            replyTarget = viewModelState.replyTarget?.let { replyTarget ->
                PostScreenUiState.ReplyTarget(
                    // 書いている途中の本文を残すため、返信先の中身へは移らない
                    post = createQuote(load = replyTarget.load, onOpenPostDetail = null, onOpenUrl = {}, onOpenProfile = {}),
                    removeButtonEnabled = viewModelState.submitting.not(),
                    listener = replyTargetListener,
                )
            },
            editTarget = if (viewModelState.editing == null || target is PostScreenTarget.Edit) {
                null
            } else {
                PostScreenUiState.EditTarget(cancelButtonEnabled = viewModelState.submitting.not(), listener = editTargetListener)
            },
            discardDraftDialog = when (viewModelState.discardConfirmation) {
                null -> null

                is DiscardConfirmation.Edit -> PostScreenUiState.DiscardDraftDialog(
                    message = "書きかけの投稿を破棄して、投稿の編集を始めますか",
                    discardButtonText = "破棄して編集",
                    listener = discardDraftDialogListener,
                )

                is DiscardConfirmation.Reply -> PostScreenUiState.DiscardDraftDialog(
                    message = "編集中の本文を破棄して、返信を始めますか",
                    discardButtonText = "破棄して返信",
                    listener = discardDraftDialogListener,
                )
            },
            addImageButtonEnabled = viewModelState.imageAddable(),
            addImageLoadingVisible = viewModelState.loadingImage,
            addEmojiButtonEnabled = viewModelState.submitting.not() && viewModelState.editableBodyLoading().not(),
            emojiPickerDialog = viewModelState.emojiPickerRequestKey?.let { requestKey ->
                ReactionPickerDialogUiState(
                    requestKey = requestKey,
                    title = "絵文字",
                    removeButtonVisible = false,
                    listener = emojiPickerListener,
                )
            },
            listener = listener,
        )

    private fun ViewModelState.postable(): Boolean {
        if (editing != null) return submitting.not() && editableBodyLoading().not()
        return submitting.not() && loadingImage.not() && quotePostable() && replyPostable() && (body.isNotBlank() || images.isNotEmpty() || quote != null)
    }

    private fun ViewModelState.editableBodyLoading(): Boolean = editing != null && editing.originalBody == null

    /**
     * 編集を始めると消える、書きかけの投稿。編集中なら書き換えた本文
     */
    private fun ViewModelState.hasDraft(): Boolean {
        if (editing != null) return editing.originalBody != null && body != editing.originalBody
        return body.isNotBlank() || contentWarning.isNullOrBlank().not() || images.isNotEmpty() || replyTarget != null
    }

    /**
     * 引用先を読み込めるまでは、どの投稿を引用するか決まらない
     */
    private fun ViewModelState.quotePostable(): Boolean = quote == null || quote is QuoteLoad.Loaded

    /**
     * 返信先を読み込めるまでは、どの投稿に返信するか決まらない
     */
    private fun ViewModelState.replyPostable(): Boolean = replyTarget == null || replyTarget.load is QuoteLoad.Loaded

    /**
     * 編集では本文だけを書き換えるので、画像は足せない
     */
    private fun ViewModelState.imageAddable(): Boolean = editing == null && submitting.not() && loadingImage.not() && images.size < MAX_IMAGES

    private data class ViewModelState(
        val body: String,
        /**
         * 閲覧注意にしなければ null
         */
        val contentWarning: String?,
        val images: List<SelectedImage>,
        val loadingImage: Boolean,
        val submitting: Boolean,
        val error: String?,
        /**
         * 絵文字のピッカーを開くたびに変える。開いていなければ null
         */
        val emojiPickerRequestKey: String?,
        /**
         * 入力欄が本文に入れ終えたら null に戻す
         */
        val bodyInsertion: TextInsertion?,
        /**
         * 引用でなければ null
         */
        val quote: QuoteLoad?,
        /**
         * 返信でなければ null
         */
        val replyTarget: ReplyTarget?,
        /**
         * 新しく投稿するなら null
         */
        val editing: Editing?,
        /**
         * 書きかけの投稿を消してよいか確かめている間。確かめていなければ null
         */
        val discardConfirmation: DiscardConfirmation?,
    )

    /**
     * 書きかけの投稿を消してから始めること
     */
    private sealed interface DiscardConfirmation {
        /**
         * @param noteId 編集する投稿の deletableNoteId
         */
        data class Edit(val noteId: String) : DiscardConfirmation

        /**
         * @param postId 返信するホームタイムラインの投稿の id
         */
        data class Reply(val postId: String) : DiscardConfirmation
    }

    /**
     * @param noteId 編集する投稿の deletableNoteId
     * @param originalBody 読み込んだ編集前の本文。読み込んでいる間は null
     */
    private data class Editing(
        val noteId: String,
        val originalBody: String?,
    )

    /**
     * @param postId 返信先に選んだホームタイムラインの投稿の id
     */
    private data class ReplyTarget(
        val postId: String,
        val load: QuoteLoad,
    )

    /**
     * 送り終えるまで送信は画面より長く生きるので、送っている途中に閉じられたら画像を消さない
     */
    override fun onCleared() {
        if (viewModelStateFlow.value.submitting) return
        viewModelStateFlow.value.images.forEach { it.image.release() }
    }

    inner class SelectedImage(
        val id: String,
        val image: WebpImage,
    ) {
        val preview: PostScreenUiState.ImagePreview = PostScreenUiState.ImagePreview(image::previewRequest)

        /**
         * 状態が変わるたびに作り直すと、画像の部品が毎回描き直される
         */
        val removeListener: PostScreenUiState.ImageListener =
            object : PostScreenUiState.ImageListener {
                override fun onClickRemove() {
                    removeImage(id)
                }
            }
    }

    internal interface Event {
        fun navigateBack()

        fun closeToHome()

        fun openImagePicker()

        fun showMessage(message: String)
    }
}

private const val BODY_KEY = "body"
private const val CONTENT_WARNING_KEY = "contentWarning"
private const val REQUEST_ID_KEY = "requestId"
private const val REPLY_TO_POST_ID_KEY = "replyToPostId"
private const val EDITING_NOTE_ID_KEY = "editingNoteId"
private const val EDITING_ORIGINAL_BODY_KEY = "editingOriginalBody"

/**
 * サーバーの 1 投稿あたりの上限
 */
private const val MAX_IMAGES = 4

/**
 * サーバーの 1 枚あたりの上限
 */
private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
