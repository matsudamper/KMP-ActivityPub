package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
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
                body = savedStateHandle.get<String>(BODY_KEY).orEmpty(),
                images = listOf(),
                loadingImage = false,
                submitting = false,
                error = null,
                emojiPickerRequestKey = null,
                bodyInsertion = null,
                quote = when (target) {
                    PostScreenTarget.NewNote -> null
                    is PostScreenTarget.Quote -> QuoteLoad.Loading
                },
            ),
        )

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
                    UserPostDetailResult.NotLoggedIn,
                    is UserPostDetailResult.Failure,
                    -> QuoteLoad.Unavailable
                }
                viewModelStateFlow.update { it.copy(quote = quote) }
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

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val error =
                try {
                    val result =
                        postNoteSubmitter.submit(
                            body = state.body,
                            requestId = requestId(),
                            images = state.images.map { it.image },
                            replyToPostId = null,
                            // 開いた投稿が保存していない他のサーバーの投稿だと、読み込んだときに保存した投稿の id でないと引用できない
                            quotePostId = (state.quote as? QuoteLoad.Loaded)?.post?.id,
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
            renewRequestId()
            viewModelStateFlow.update { it.copy(body = "", images = listOf()) }
            state.images.forEach { it.image.release() }
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
            UserPostNoteResult.FederationDisabled -> "サーバーが投稿を配れる設定になっていません"
            UserPostNoteResult.TooManyImages -> "画像は $MAX_IMAGES 枚までにしてください"
            UserPostNoteResult.ImageNotFound -> "画像を送れませんでした。もう一度投稿してください"
            UserPostNoteResult.ReplyTargetNotFound -> "返信先の投稿が見つかりません"
            UserPostNoteResult.QuoteTargetNotFound -> "引用する投稿が見つかりません"
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
            body = viewModelState.body,
            bodyInsertion = viewModelState.bodyInsertion,
            inputHasError = viewModelState.error != null,
            inputEnabled = viewModelState.submitting.not(),
            closeButtonEnabled = viewModelState.submitting.not(),
            postButtonEnabled = viewModelState.postable(),
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
            addImageButtonEnabled = viewModelState.imageAddable(),
            addImageLoadingVisible = viewModelState.loadingImage,
            addEmojiButtonEnabled = viewModelState.submitting.not(),
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

    private fun ViewModelState.postable(): Boolean =
        submitting.not() && loadingImage.not() && quotePostable() && (body.isNotBlank() || images.isNotEmpty() || quote != null)

    /**
     * 引用先を読み込めるまでは、どの投稿を引用するか決まらない
     */
    private fun ViewModelState.quotePostable(): Boolean = quote == null || quote is QuoteLoad.Loaded

    private fun ViewModelState.imageAddable(): Boolean = submitting.not() && loadingImage.not() && images.size < MAX_IMAGES

    private data class ViewModelState(
        val body: String,
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
private const val REQUEST_ID_KEY = "requestId"

/**
 * サーバーの 1 投稿あたりの上限
 */
private const val MAX_IMAGES = 4

/**
 * サーバーの 1 枚あたりの上限
 */
private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
