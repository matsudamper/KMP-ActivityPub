package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteImage
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.PostImage
import net.matsudamper.kmp.activitypub.frontend.ui.toPostImageOrNull
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class PostScreenViewModel(
    private val postNoteSubmitter: PostNoteSubmitter,
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
            ),
        )

    val uiStateFlow: StateFlow<PostScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener = object : PostScreenUiState.Listener {
                    override fun onBodyChanged(text: String) {
                        savedStateHandle[BODY_KEY] = text
                        renewRequestId()
                        viewModelStateFlow.update { it.copy(body = text, error = null) }
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

                    override fun onClickBack() {
                        // 送信中に閉じると、投稿できたかどうかを画面に出せない
                        if (viewModelStateFlow.value.submitting) return
                        viewModelScope.launch {
                            events.send { it.navigateBack() }
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
        viewModelStateFlow.update { state -> state.copy(images = state.images.filterNot { it.id == id }, error = null) }
    }

    /**
     * 読めなければメッセージを出して null
     */
    private suspend fun readImage(file: PickedImageFile): PostImage? =
        when (val result = file.read(maxBytes = MAX_SOURCE_IMAGE_BYTES)) {
            is PickedImageFile.ReadResult.Success -> {
                // 展開と JPEG への書き出しは画面を止めるほど重い
                val image = withContext(Dispatchers.Default) { result.bytes.toPostImageOrNull(maxSidePx = IMAGE_MAX_SIDE_PX) }
                if (image == null) events.send { it.showMessage("画像として読み込めませんでした") }
                image
            }

            PickedImageFile.ReadResult.TooLarge -> {
                events.send { it.showMessage("画像ファイルが大きすぎます。20MB までにしてください") }
                null
            }

            PickedImageFile.ReadResult.Error -> {
                events.send { it.showMessage("画像ファイルを読み込めませんでした") }
                null
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
                            images = state.images.map { UserPostNoteImage(bytes = it.image.jpeg, description = null) },
                        )
                    when (result) {
                        UserPostNoteResult.Success -> null
                        UserPostNoteResult.NotLoggedIn -> "ログインしていません"
                        UserPostNoteResult.EmptyBody -> "本文が空です"
                        UserPostNoteResult.TooLong -> "本文が長すぎます"
                        UserPostNoteResult.FederationDisabled -> "サーバーが投稿を配れる設定になっていません"
                        UserPostNoteResult.TooManyImages -> "画像は $MAX_IMAGES 枚までにしてください"
                        UserPostNoteResult.ImageTooLarge -> "画像が大きすぎます"
                        UserPostNoteResult.UnsupportedImage -> "送れない形式の画像があります"
                        UserPostNoteResult.DescriptionTooLong -> "画像の説明が長すぎます"
                        UserPostNoteResult.UnsupportedOperation -> "このサーバーでは画像を投稿できません"
                        UserPostNoteResult.InternalServerError -> "サーバーでエラーが起きました。時間をおいてもう一度試してください"
                        is UserPostNoteResult.Failure -> result.message
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
            events.send { it.navigateBack() }
        }
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
            inputHasError = viewModelState.error != null,
            inputEnabled = viewModelState.submitting.not(),
            backButtonEnabled = viewModelState.submitting.not(),
            postButtonEnabled = viewModelState.postable(),
            images =
            viewModelState.images.map { selected ->
                PostScreenUiState.Image(
                    bitmap = selected.image.bitmap,
                    removeButtonEnabled = viewModelState.submitting.not(),
                    listener = selected.removeListener,
                )
            },
            addImageButtonEnabled = viewModelState.imageAddable(),
            addImageLoadingVisible = viewModelState.loadingImage,
            listener = listener,
        )

    private fun ViewModelState.postable(): Boolean =
        submitting.not() && loadingImage.not() && (body.isNotBlank() || images.isNotEmpty())

    private fun ViewModelState.imageAddable(): Boolean = submitting.not() && loadingImage.not() && images.size < MAX_IMAGES

    data class ViewModelState(
        val body: String,
        val images: List<SelectedImage>,
        val loadingImage: Boolean,
        val submitting: Boolean,
        val error: String?,
    )

    inner class SelectedImage(
        val id: String,
        val image: PostImage,
    ) {
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

    interface Event {
        fun navigateBack()

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
 * 長い辺をこの大きさまで縮めて送る。サーバーの 1 枚 2MiB に収まる大きさにする
 */
private const val IMAGE_MAX_SIDE_PX = 1920

/**
 * 縮める前のファイルの上限。大きな写真を丸ごと読むとメモリが足りなくなる
 */
private const val MAX_SOURCE_IMAGE_BYTES = 20 * 1024 * 1024
