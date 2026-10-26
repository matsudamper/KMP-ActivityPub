package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserMyProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUpdateProfileIconResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUpdateProfileResult
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIconImage
import net.matsudamper.kmp.activitypub.frontend.ui.toProfileIconOrNull
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ProfileEditScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState.Loading)
    private var loadJob: Job? = null

    val uiStateFlow: StateFlow<ProfileEditScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener = object : ProfileEditScreenUiState.Listener {
                    override fun onClickSelectIcon() {
                        if (viewModelStateFlow.value.saving()) return
                        viewModelScope.launch {
                            events.send { it.openImagePicker() }
                        }
                    }

                    override fun onIconPicked(file: PickedImageFile) {
                        pickIcon(file)
                    }

                    override fun onDisplayNameChanged(text: String) {
                        viewModelStateFlow.update { state ->
                            if (state is ViewModelState.Loaded && state.saving.not()) {
                                state.copy(displayName = text, hasError = false)
                            } else {
                                state
                            }
                        }
                    }

                    override fun onClickSave() {
                        save()
                    }

                    override fun onClickRetry() {
                        load()
                    }

                    override fun onClickBack() {
                        // 保存中に閉じると、保存できたかどうかを画面に出せない
                        if (viewModelStateFlow.value.saving()) return
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

    init {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        viewModelStateFlow.value = ViewModelState.Loading
        loadJob = viewModelScope.launch {
            viewModelStateFlow.value =
                when (val result = api.myProfile()) {
                    is UserMyProfileResult.Found -> ViewModelState.Loaded(
                        iconPreview = null,
                        processingIcon = false,
                        currentIconUrl = result.iconUrl,
                        displayName = result.displayName.orEmpty(),
                        saving = false,
                        hasError = false,
                    )

                    UserMyProfileResult.NotLoggedIn -> ViewModelState.Failed("ログインしていません")

                    is UserMyProfileResult.Failure -> ViewModelState.Failed(result.message)
                }
        }
    }

    /**
     * 選んだらそのまま送る。読み込みも viewModelScope で行う。画面のスコープだと回転などで止まり、processingIcon が下りなくなる
     */
    private fun pickIcon(file: PickedImageFile) {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        if (state.saving || state.processingIcon) return
        viewModelStateFlow.value = state.copy(processingIcon = true)

        viewModelScope.launch {
            val icon = readIcon(file)
            if (icon == null) {
                viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) current.copy(processingIcon = false) else current
                }
                return@launch
            }

            // 送っている間も選んだ画像を出す。失敗したら前の表示に戻す
            val previousPreview = (viewModelStateFlow.value as? ViewModelState.Loaded)?.iconPreview
            viewModelStateFlow.update { current ->
                if (current is ViewModelState.Loaded) current.copy(iconPreview = icon.bitmap) else current
            }
            val result = api.updateProfileIcon(icon = icon.png)
            viewModelStateFlow.update { current ->
                when {
                    current !is ViewModelState.Loaded -> current

                    result is UserUpdateProfileIconResult.Success -> current.copy(
                        currentIconUrl = result.iconUrl ?: current.currentIconUrl,
                        processingIcon = false,
                    )

                    else -> current.copy(iconPreview = previousPreview, processingIcon = false)
                }
            }
            val message =
                when (result) {
                    is UserUpdateProfileIconResult.Success -> "プロフィール画像を変更しました"
                    UserUpdateProfileIconResult.NotLoggedIn -> "ログインしていません"
                    UserUpdateProfileIconResult.TooLarge -> "画像が大きすぎます。2MB までにしてください"
                    UserUpdateProfileIconResult.DimensionsTooLarge -> "画像が大きすぎます。縦横 ${ICON_SIDE_PX}px までにしてください"
                    UserUpdateProfileIconResult.UnsupportedImage -> "PNG / JPEG / GIF / WebP の画像にしてください"
                    UserUpdateProfileIconResult.UnsupportedOperation -> "このサーバーでは受け付けていない操作です"
                    UserUpdateProfileIconResult.InternalServerError -> "サーバーでエラーが起きました。時間をおいてもう一度試してください"
                    UserUpdateProfileIconResult.FederationDisabled -> "サーバーがプロフィールの更新を配れる設定になっていません"
                    is UserUpdateProfileIconResult.Failure -> result.message
                }
            events.send { it.showMessage(message) }
        }
    }

    /**
     * 読めなければメッセージを出して null
     */
    private suspend fun readIcon(file: PickedImageFile): ProfileIconImage? =
        when (val result = file.read(maxBytes = MAX_SOURCE_ICON_BYTES)) {
            is PickedImageFile.ReadResult.Success -> {
                // 展開と PNG への書き出しは画面を止めるほど重い
                val icon = withContext(Dispatchers.Default) { result.bytes.toProfileIconOrNull(sidePx = ICON_SIDE_PX) }
                if (icon == null) events.send { it.showMessage("画像として読み込めませんでした") }
                icon
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

    private fun save() {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        // 保存できたら画面を閉じるので、画像を送っている途中だと送信が止まる
        if (state.saving || state.processingIcon) return

        viewModelStateFlow.value = state.copy(saving = true, hasError = false)
        viewModelScope.launch {
            val error =
                try {
                    when (val result = api.updateProfile(displayName = state.displayName)) {
                        UserUpdateProfileResult.Success -> null
                        UserUpdateProfileResult.NotLoggedIn -> "ログインしていません"
                        UserUpdateProfileResult.TooLong -> "表示名が長すぎます"
                        UserUpdateProfileResult.FederationDisabled -> "サーバーがプロフィールの更新を配れる設定になっていません"
                        is UserUpdateProfileResult.Failure -> result.message
                    }
                } finally {
                    viewModelStateFlow.update { current ->
                        if (current is ViewModelState.Loaded) current.copy(saving = false) else current
                    }
                }
            if (error != null) {
                viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) current.copy(hasError = true) else current
                }
                events.send { it.showMessage(error) }
                return@launch
            }
            events.send { it.navigateBack() }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: ProfileEditScreenUiState.Listener,
    ): ProfileEditScreenUiState =
        ProfileEditScreenUiState(
            content = when (viewModelState) {
                ViewModelState.Loading -> ProfileEditScreenUiState.Content.Loading

                is ViewModelState.Failed -> ProfileEditScreenUiState.Content.Error(viewModelState.message)

                is ViewModelState.Loaded -> ProfileEditScreenUiState.Content.Loaded(
                    iconPreview = viewModelState.iconPreview,
                    iconUploading = viewModelState.processingIcon,
                    currentIconUrl = viewModelState.currentIconUrl,
                    iconSelectButtonEnabled = viewModelState.saving.not() && viewModelState.processingIcon.not(),
                    displayName = viewModelState.displayName,
                    displayNameInputEnabled = viewModelState.saving.not(),
                    displayNameHasError = viewModelState.hasError,
                    saveButtonEnabled = viewModelState.saving.not() && viewModelState.processingIcon.not(),
                )
            },
            // 画像を送っている途中で閉じると送信が止まる
            backButtonEnabled = viewModelState.saving().not() && viewModelState.processingIcon().not(),
            listener = listener,
        )

    private fun ViewModelState.saving(): Boolean = this is ViewModelState.Loaded && saving

    private fun ViewModelState.processingIcon(): Boolean = this is ViewModelState.Loaded && processingIcon

    sealed interface ViewModelState {
        data object Loading : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState

        /**
         * @param iconPreview 送った、または送っている画像。取り直さずにすぐ出す
         * @param processingIcon 選んだ画像を読んで、縮めて、送っている途中
         */
        data class Loaded(
            val iconPreview: ImageBitmap?,
            val processingIcon: Boolean,
            val currentIconUrl: String?,
            val displayName: String,
            val saving: Boolean,
            val hasError: Boolean,
        ) : ViewModelState
    }

    interface Event {
        fun openImagePicker()

        fun navigateBack()

        fun showMessage(message: String)
    }

    companion object {
        /**
         * 選んだファイルを読む上限。縮めて送るので、スマートフォンの写真がそのまま収まる大きさにする
         */
        private const val MAX_SOURCE_ICON_BYTES = 20 * 1024 * 1024

        /**
         * 送る前に縮める大きさ。サーバーはこれを超える縦横を受け付けない
         */
        private const val ICON_SIDE_PX = 400
    }
}
