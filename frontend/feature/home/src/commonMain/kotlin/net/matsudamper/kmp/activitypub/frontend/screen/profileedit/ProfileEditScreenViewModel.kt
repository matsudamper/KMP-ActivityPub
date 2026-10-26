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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUpdateProfileResult
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

                    override fun onIconPicked(bytes: ByteArray) {
                        pickIcon(bytes)
                    }

                    override fun onIconTooLarge() {
                        showIconTooLarge()
                    }

                    override fun onIconReadError() {
                        viewModelScope.launch { events.send { it.showMessage("画像ファイルを読み込めませんでした") } }
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
                        newIcon = null,
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

    private fun pickIcon(bytes: ByteArray) {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        if (state.saving || state.processingIcon) return
        viewModelStateFlow.value = state.copy(processingIcon = true)

        viewModelScope.launch {
            // 展開と PNG への書き出しは画面を止めるほど重い
            val icon = withContext(Dispatchers.Default) { bytes.toProfileIconOrNull(sidePx = ICON_SIDE_PX) }
            viewModelStateFlow.update { current ->
                when {
                    current !is ViewModelState.Loaded -> current
                    icon == null -> current.copy(processingIcon = false)
                    else -> current.copy(newIcon = ViewModelState.NewIcon(bytes = icon.png, bitmap = icon.bitmap), processingIcon = false)
                }
            }
            if (icon == null) {
                events.send { it.showMessage("画像として読み込めませんでした") }
            }
        }
    }

    private fun showIconTooLarge() {
        viewModelScope.launch { events.send { it.showMessage("画像ファイルが大きすぎます。20MB までにしてください") } }
    }

    private fun save() {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        // 縮めている途中の画像は newIcon にまだ入っていないので、送ると選んだ画像が抜ける
        if (state.saving || state.processingIcon) return

        viewModelStateFlow.value = state.copy(saving = true, hasError = false)
        viewModelScope.launch {
            val error =
                try {
                    when (val result = api.updateProfile(displayName = state.displayName, icon = state.newIcon?.bytes)) {
                        UserUpdateProfileResult.Success -> null
                        UserUpdateProfileResult.NotLoggedIn -> "ログインしていません"
                        UserUpdateProfileResult.TooLong -> "表示名が長すぎます"
                        UserUpdateProfileResult.IconTooLarge -> "画像が大きすぎます。2MB までにしてください"
                        UserUpdateProfileResult.IconDimensionsTooLarge -> "画像が大きすぎます。縦横 ${ICON_SIDE_PX}px までにしてください"
                        UserUpdateProfileResult.IconUnsupported -> "PNG / JPEG / GIF / WebP の画像にしてください"
                        UserUpdateProfileResult.UnsupportedOperation -> "このサーバーでは受け付けていない操作です"
                        UserUpdateProfileResult.InternalServerError -> "サーバーでエラーが起きました。時間をおいてもう一度試してください"
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
                    newIcon = viewModelState.newIcon?.bitmap,
                    currentIconUrl = viewModelState.currentIconUrl,
                    iconSelectButtonEnabled = viewModelState.saving.not() && viewModelState.processingIcon.not(),
                    displayName = viewModelState.displayName,
                    displayNameInputEnabled = viewModelState.saving.not(),
                    displayNameHasError = viewModelState.hasError,
                    saveButtonEnabled = viewModelState.saving.not() && viewModelState.processingIcon.not(),
                )
            },
            backButtonEnabled = viewModelState.saving().not(),
            listener = listener,
        )

    private fun ViewModelState.saving(): Boolean = this is ViewModelState.Loaded && saving

    sealed interface ViewModelState {
        data object Loading : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState

        /**
         * @param processingIcon 選んだ画像を縮めている途中
         */
        data class Loaded(
            val newIcon: NewIcon?,
            val processingIcon: Boolean,
            val currentIconUrl: String?,
            val displayName: String,
            val saving: Boolean,
            val hasError: Boolean,
        ) : ViewModelState

        /**
         * @param bytes 選んだファイルの中身。そのまま送る
         */
        class NewIcon(
            val bytes: ByteArray,
            val bitmap: ImageBitmap,
        )
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
        const val MAX_SOURCE_ICON_BYTES = 20 * 1024 * 1024

        /**
         * 送る前に縮める大きさ。サーバーはこれを超える縦横を受け付けない
         */
        private const val ICON_SIDE_PX = 400
    }
}
