package net.matsudamper.kmp.activitypub.frontend.screen.customemojis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmojisResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLocalCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUploadCustomEmojiResult
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CustomEmojisScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                emojis = EmojisState.Loading,
                shortcodeInput = "",
                pickedImage = null,
                readingImage = false,
                uploading = false,
                deletingIds = setOf(),
            ),
        )

    private var loadJob: Job? = null

    private val listener = object : CustomEmojisScreenUiState.Listener {
        override fun onClickBack() {
            viewModelScope.launch { events.send { it.navigateBack() } }
        }

        override fun onChangeShortcodeInput(value: String) {
            // 本文では :shortcode: と書くので、コロンごと貼られても名前だけにする
            viewModelStateFlow.update { it.copy(shortcodeInput = value.trim().trim(':')) }
        }

        override fun onClickPickImage() {
            viewModelScope.launch { events.send { it.openImagePicker() } }
        }

        override fun onImagePicked(file: PickedImageFile) {
            readImage(file)
        }

        override fun onClickAdd() {
            upload()
        }

        override fun onClickRetry() {
            load()
        }
    }

    val uiStateFlow: StateFlow<CustomEmojisScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { uiStateFlow.value = createUiState(it) }
            }
        }.asStateFlow()

    init {
        load()
    }

    /**
     * 前の読み込みは止める。追加より前に始まった読み込みの結果で、追加した後の一覧を上書きしないため
     */
    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.update { it.copy(emojis = EmojisState.Loading) }
        loadJob = viewModelScope.launch {
            val emojis = when (val result = api.customEmojis()) {
                is UserCustomEmojisResult.Success -> EmojisState.Loaded(result.emojis)
                is UserCustomEmojisResult.Failure -> EmojisState.Failed(result.message)
            }
            viewModelStateFlow.update { it.copy(emojis = emojis) }
        }
    }

    /**
     * 動く GIF などを描き直すと止まってしまうので、選んだファイルをそのまま送る
     */
    private fun readImage(file: PickedImageFile) {
        if (viewModelStateFlow.value.readingImage) return
        viewModelStateFlow.update { it.copy(readingImage = true) }
        viewModelScope.launch {
            try {
                when (val result = file.read(maxBytes = MAX_IMAGE_BYTES)) {
                    is PickedImageFile.ReadResult.Success -> {
                        viewModelStateFlow.update { it.copy(pickedImage = CustomEmojisScreenUiState.PickedImage(result.bytes)) }
                    }

                    PickedImageFile.ReadResult.TooLarge -> events.send { it.showMessage("画像は 1MB までにしてください") }

                    PickedImageFile.ReadResult.Error -> events.send { it.showMessage("画像ファイルを読み込めませんでした") }
                }
            } finally {
                viewModelStateFlow.update { it.copy(readingImage = false) }
            }
        }
    }

    private fun upload() {
        val state = viewModelStateFlow.value
        val image = state.pickedImage ?: return
        if (!state.uploadable()) return

        viewModelStateFlow.update { it.copy(uploading = true) }
        viewModelScope.launch {
            val result =
                try {
                    api.uploadCustomEmoji(shortcode = state.shortcodeInput, image = image.bytes)
                } finally {
                    viewModelStateFlow.update { it.copy(uploading = false) }
                }
            when (result) {
                is UserUploadCustomEmojiResult.Success -> {
                    viewModelStateFlow.update { current ->
                        val emojis = current.emojis
                        current.copy(
                            shortcodeInput = "",
                            pickedImage = null,
                            emojis = if (emojis is EmojisState.Loaded) {
                                EmojisState.Loaded((emojis.emojis + result.emoji).sortedBy { it.shortcode })
                            } else {
                                emojis
                            },
                        )
                    }
                    // 一覧を取れていないときは、足した絵文字が見えるよう取り直す
                    if (viewModelStateFlow.value.emojis !is EmojisState.Loaded) load()
                    events.send { it.showMessage(":${result.emoji.shortcode}: を追加しました") }
                }

                is UserUploadCustomEmojiResult.Failure -> events.send { it.showMessage(result.message) }
            }
        }
    }

    private fun delete(emoji: UserLocalCustomEmoji) {
        if (emoji.id in viewModelStateFlow.value.deletingIds) return

        viewModelStateFlow.update { it.copy(deletingIds = it.deletingIds + emoji.id) }
        viewModelScope.launch {
            val error =
                try {
                    api.deleteCustomEmoji(emoji.id)
                } finally {
                    viewModelStateFlow.update { it.copy(deletingIds = it.deletingIds - emoji.id) }
                }
            if (error == null) {
                viewModelStateFlow.update { current ->
                    val emojis = current.emojis
                    current.copy(emojis = if (emojis is EmojisState.Loaded) EmojisState.Loaded(emojis.emojis.filterNot { it.id == emoji.id }) else emojis)
                }
                events.send { it.showMessage(":${emoji.shortcode}: を削除しました") }
            } else {
                events.send { it.showMessage(error) }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): CustomEmojisScreenUiState {
        val editable = !viewModelState.uploading
        return CustomEmojisScreenUiState(
            shortcodeInput = viewModelState.shortcodeInput,
            shortcodeInputEnabled = editable,
            pickedImage = viewModelState.pickedImage,
            pickImageButtonEnabled = editable && !viewModelState.readingImage,
            addButtonEnabled = viewModelState.uploadable(),
            content = when (val emojis = viewModelState.emojis) {
                EmojisState.Loading -> CustomEmojisScreenUiState.Content.Loading

                is EmojisState.Failed -> CustomEmojisScreenUiState.Content.Error(emojis.message)

                is EmojisState.Loaded -> CustomEmojisScreenUiState.Content.Loaded(
                    emojis = emojis.emojis.map { emoji ->
                        CustomEmojisScreenUiState.Emoji(
                            shortcodeText = ":${emoji.shortcode}:",
                            imageUrl = emoji.imageUrl,
                            deleteButton = if (emoji.deletable) {
                                CustomEmojisScreenUiState.DeleteButton(
                                    enabled = emoji.id !in viewModelState.deletingIds,
                                    listener = object : CustomEmojisScreenUiState.DeleteButtonListener {
                                        override fun onClick() {
                                            delete(emoji)
                                        }
                                    },
                                )
                            } else {
                                null
                            },
                        )
                    },
                    emptyMessageVisible = emojis.emojis.isEmpty(),
                )
            },
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun openImagePicker()

        fun showMessage(message: String)
    }

    private data class ViewModelState(
        val emojis: EmojisState,
        val shortcodeInput: String,
        val pickedImage: CustomEmojisScreenUiState.PickedImage?,
        val readingImage: Boolean,
        val uploading: Boolean,
        val deletingIds: Set<String>,
    ) {
        fun uploadable(): Boolean = !uploading && !readingImage && pickedImage != null && shortcodeInput.isNotBlank()
    }

    private sealed interface EmojisState {
        data object Loading : EmojisState

        /**
         * @param emojis shortcode の順
         */
        data class Loaded(val emojis: List<UserLocalCustomEmoji>) : EmojisState

        data class Failed(val message: String) : EmojisState
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 1024 * 1024
    }
}
