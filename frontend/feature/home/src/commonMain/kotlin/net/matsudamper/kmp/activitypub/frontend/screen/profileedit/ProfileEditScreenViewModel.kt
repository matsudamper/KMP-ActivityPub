package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileLink
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUpdateProfileHeaderResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUpdateProfileIconResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUpdateProfileResult
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileHeaderImage
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIconImage
import net.matsudamper.kmp.activitypub.frontend.ui.toProfileHeaderOrNull
import net.matsudamper.kmp.activitypub.frontend.ui.toProfileHeaderSourceOrNull
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

                    override fun onClickSelectHeader() {
                        if (viewModelStateFlow.value.saving()) return
                        viewModelScope.launch {
                            events.send { it.openHeaderImagePicker() }
                        }
                    }

                    override fun onHeaderPicked(file: PickedImageFile) {
                        pickHeader(file)
                    }

                    override fun onHeaderCropMoved(cropTopRatio: Float) {
                        viewModelStateFlow.update { state ->
                            val headerCrop = (state as? ViewModelState.Loaded)?.headerCrop
                            if (state is ViewModelState.Loaded && headerCrop != null) {
                                state.copy(headerCrop = headerCrop.copy(topRatio = cropTopRatio.coerceIn(0f, 1f)))
                            } else {
                                state
                            }
                        }
                    }

                    override fun onClickHeaderCropConfirm() {
                        confirmHeaderCrop()
                    }

                    override fun onDisplayNameChanged(text: String) {
                        updateEditing { it.copy(displayName = text) }
                    }

                    override fun onSummaryChanged(text: String) {
                        updateEditing { it.copy(summary = text) }
                    }

                    override fun onLinkNameChanged(
                        index: Int,
                        text: String,
                    ) {
                        updateEditing { state ->
                            state.copy(links = state.links.mapIndexed { i, link -> if (i == index) link.copy(name = text) else link })
                        }
                    }

                    override fun onLinkUrlChanged(
                        index: Int,
                        text: String,
                    ) {
                        updateEditing { state ->
                            state.copy(links = state.links.mapIndexed { i, link -> if (i == index) link.copy(url = text) else link })
                        }
                    }

                    override fun onClickAddLink() {
                        updateEditing { state ->
                            if (state.links.size >= MAX_LINK_COUNT) {
                                state
                            } else {
                                state.copy(links = state.links + UserProfileLink(name = "", url = ""))
                            }
                        }
                    }

                    override fun onClickRemoveLink(index: Int) {
                        updateEditing { state -> state.copy(links = state.links.filterIndexed { i, _ -> i != index }) }
                    }

                    override fun onLikesPublicChanged(likesPublic: Boolean) {
                        updateEditing { it.copy(likesPublic = likesPublic) }
                    }

                    override fun onClickSave() {
                        save()
                    }

                    override fun onClickRetry() {
                        load()
                    }

                    override fun onClickBack() {
                        // 範囲を選んでいる間の戻るは、選ばずに編集に戻る
                        val state = viewModelStateFlow.value
                        if (state is ViewModelState.Loaded && state.headerCrop != null) {
                            viewModelStateFlow.value = state.copy(headerCrop = null)
                            return
                        }
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
                        headerPreview = null,
                        processingHeader = false,
                        currentHeaderUrl = result.headerUrl,
                        headerCrop = null,
                        displayName = result.displayName.orEmpty(),
                        summary = result.summary.orEmpty(),
                        links = result.links,
                        likesPublic = result.likesPublic,
                        saving = false,
                        errorField = null,
                    )

                    UserMyProfileResult.NotLoggedIn -> ViewModelState.Failed("ログインしていません")

                    is UserMyProfileResult.Failure -> ViewModelState.Failed(result.message)
                }
        }
    }

    /**
     * 保存中は送っている値と画面がずれないよう、書き換えを受け付けない
     */
    private fun updateEditing(transform: (ViewModelState.Loaded) -> ViewModelState.Loaded) {
        viewModelStateFlow.update { state ->
            if (state is ViewModelState.Loaded && state.saving.not()) {
                transform(state).copy(errorField = null)
            } else {
                state
            }
        }
    }

    /**
     * 選んだらそのまま送る。読み込みも viewModelScope で行う。画面のスコープだと回転などで止まり、processingIcon が下りなくなる
     */
    private fun pickIcon(file: PickedImageFile) {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        if (state.saving || state.processingImage()) return
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
        when (val result = file.read(maxBytes = MAX_SOURCE_IMAGE_BYTES)) {
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

    /**
     * 横幅を合わせて縦が収まればそのまま送る。縦がはみ出すなら、使う範囲を選ばせてから送る。
     * 読み込みを viewModelScope で行う理由は [pickIcon] と同じ
     */
    private fun pickHeader(file: PickedImageFile) {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        if (state.saving || state.processingImage()) return
        viewModelStateFlow.value = state.copy(processingHeader = true)

        viewModelScope.launch {
            val source = readHeaderSource(file)
            if (source == null) {
                viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) current.copy(processingHeader = false) else current
                }
                return@launch
            }
            // 丸めで 1px はみ出すだけのときは選ばせずに上から切る
            if (source.height - source.headerCropHeight() > 1) {
                viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) {
                        current.copy(processingHeader = false, headerCrop = HeaderCrop(source = source, topRatio = 0.5f))
                    } else {
                        current
                    }
                }
                return@launch
            }
            uploadHeader(source = source, top = 0)
        }
    }

    private fun confirmHeaderCrop() {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        val headerCrop = state.headerCrop ?: return
        if (state.saving || state.processingIcon || state.processingHeader) return
        viewModelStateFlow.value = state.copy(headerCrop = null, processingHeader = true)

        val source = headerCrop.source
        val top = ((source.height - source.headerCropHeight()) * headerCrop.topRatio).roundToInt()
        viewModelScope.launch {
            uploadHeader(source = source, top = top)
        }
    }

    /**
     * 呼ぶ前に processingHeader を立てておく。終わったら下ろす
     */
    private suspend fun uploadHeader(
        source: ImageBitmap,
        top: Int,
    ) {
        // JPEG への書き出しは画面を止めるほど重い
        val header = withContext(Dispatchers.Default) { source.toProfileHeaderOrNull(top = top, height = source.headerCropHeight()) }
        if (header == null) {
            viewModelStateFlow.update { current ->
                if (current is ViewModelState.Loaded) current.copy(processingHeader = false) else current
            }
            events.send { it.showMessage("画像として読み込めませんでした") }
            return
        }

        // 送っている間も選んだ画像を出す。失敗したら前の表示に戻す
        val previousPreview = (viewModelStateFlow.value as? ViewModelState.Loaded)?.headerPreview
        viewModelStateFlow.update { current ->
            if (current is ViewModelState.Loaded) current.copy(headerPreview = header.bitmap) else current
        }
        val result = api.updateProfileHeader(header = header.jpeg)
        viewModelStateFlow.update { current ->
            when {
                current !is ViewModelState.Loaded -> current

                result is UserUpdateProfileHeaderResult.Success -> current.copy(
                    currentHeaderUrl = result.headerUrl ?: current.currentHeaderUrl,
                    processingHeader = false,
                )

                else -> current.copy(headerPreview = previousPreview, processingHeader = false)
            }
        }
        val message =
            when (result) {
                is UserUpdateProfileHeaderResult.Success -> "ヘッダー画像を変更しました"
                UserUpdateProfileHeaderResult.NotLoggedIn -> "ログインしていません"
                UserUpdateProfileHeaderResult.TooLarge -> "画像が大きすぎます。2MB までにしてください"
                UserUpdateProfileHeaderResult.DimensionsTooLarge -> "画像が大きすぎます。横 ${HEADER_WIDTH_PX}px、縦 ${HEADER_HEIGHT_PX}px までにしてください"
                UserUpdateProfileHeaderResult.UnsupportedImage -> "PNG / JPEG / GIF / WebP の画像にしてください"
                UserUpdateProfileHeaderResult.UnsupportedOperation -> "このサーバーでは受け付けていない操作です"
                UserUpdateProfileHeaderResult.InternalServerError -> "サーバーでエラーが起きました。時間をおいてもう一度試してください"
                UserUpdateProfileHeaderResult.FederationDisabled -> "サーバーがプロフィールの更新を配れる設定になっていません"
                is UserUpdateProfileHeaderResult.Failure -> result.message
            }
        events.send { it.showMessage(message) }
    }

    /**
     * 読めなければメッセージを出して null
     */
    private suspend fun readHeaderSource(file: PickedImageFile): ImageBitmap? =
        when (val result = file.read(maxBytes = MAX_SOURCE_IMAGE_BYTES)) {
            is PickedImageFile.ReadResult.Success -> {
                // 展開と縮小は画面を止めるほど重い
                val source = withContext(Dispatchers.Default) {
                    result.bytes.toProfileHeaderSourceOrNull(widthPx = HEADER_WIDTH_PX, heightPx = HEADER_HEIGHT_PX)
                }
                if (source == null) events.send { it.showMessage("画像として読み込めませんでした") }
                source
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

    /**
     * 横幅に合わせたときのヘッダーの高さ。元が低ければ元の高さ
     */
    private fun ImageBitmap.headerCropHeight(): Int = minOf(height, (width.toLong() * HEADER_HEIGHT_PX / HEADER_WIDTH_PX).toInt().coerceAtLeast(1))

    private fun save() {
        val state = viewModelStateFlow.value as? ViewModelState.Loaded ?: return
        // 保存できたら画面を閉じるので、画像を送っている途中だと送信が止まる
        if (state.saving || state.processingImage()) return

        viewModelStateFlow.value = state.copy(saving = true, errorField = null)
        viewModelScope.launch {
            val error =
                try {
                    val result = api.updateProfile(
                        displayName = state.displayName,
                        summary = state.summary,
                        links = state.links,
                        likesPublic = state.likesPublic,
                    )
                    when (result) {
                        UserUpdateProfileResult.Success -> null
                        UserUpdateProfileResult.NotLoggedIn -> SaveError(message = "ログインしていません", field = null)
                        UserUpdateProfileResult.DisplayNameTooLong -> SaveError(message = "表示名が長すぎます", field = ErrorField.DisplayName)
                        UserUpdateProfileResult.SummaryTooLong -> SaveError(message = "自己紹介が長すぎます。500 文字までにしてください", field = ErrorField.Summary)
                        UserUpdateProfileResult.TooManyLinks -> SaveError(message = "リンクは ${MAX_LINK_COUNT} 件までにしてください", field = ErrorField.Links)
                        UserUpdateProfileResult.LinkTooLong -> SaveError(message = "リンクの名前と URL は 255 文字までにしてください", field = ErrorField.Links)
                        UserUpdateProfileResult.InvalidLink -> SaveError(message = "リンクには名前と http / https の URL を入れてください", field = ErrorField.Links)
                        UserUpdateProfileResult.FederationDisabled -> SaveError(message = "サーバーがプロフィールの更新を配れる設定になっていません", field = null)
                        is UserUpdateProfileResult.Failure -> SaveError(message = result.message, field = null)
                    }
                } finally {
                    viewModelStateFlow.update { current ->
                        if (current is ViewModelState.Loaded) current.copy(saving = false) else current
                    }
                }
            if (error != null) {
                viewModelStateFlow.update { current ->
                    if (current is ViewModelState.Loaded) current.copy(errorField = error.field) else current
                }
                events.send { it.showMessage(error.message) }
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
            title = if (viewModelState.headerCrop() == null) "プロフィールを編集" else "ヘッダーの範囲を選ぶ",
            content = when (viewModelState) {
                ViewModelState.Loading -> ProfileEditScreenUiState.Content.Loading
                is ViewModelState.Failed -> ProfileEditScreenUiState.Content.Error(viewModelState.message)
                is ViewModelState.Loaded -> createLoadedContent(viewModelState)
            },
            // 画像を送っている途中で閉じると送信が止まる。範囲を選んでいる間は、戻ると編集に戻る
            backButtonEnabled = viewModelState.saving().not() && viewModelState.sendingImage().not(),
            listener = listener,
        )

    private fun createLoadedContent(viewModelState: ViewModelState.Loaded): ProfileEditScreenUiState.Content {
        val headerCrop = viewModelState.headerCrop
        if (headerCrop != null) {
            return ProfileEditScreenUiState.Content.HeaderCrop(
                image = headerCrop.source,
                cropTopRatio = headerCrop.topRatio,
                cropHeightRatio = headerCrop.source.headerCropHeight().toFloat() / headerCrop.source.height,
            )
        }
        return ProfileEditScreenUiState.Content.Loaded(
            iconPreview = viewModelState.iconPreview,
            iconLoadingVisible = viewModelState.processingIcon,
            currentIconUrl = viewModelState.currentIconUrl,
            iconSelectButtonEnabled = viewModelState.saving.not() && viewModelState.processingImage().not(),
            headerPreview = viewModelState.headerPreview,
            headerLoadingVisible = viewModelState.processingHeader,
            currentHeaderUrl = viewModelState.currentHeaderUrl,
            headerSelectButtonEnabled = viewModelState.saving.not() && viewModelState.processingImage().not(),
            displayName = viewModelState.displayName,
            displayNameInputEnabled = viewModelState.saving.not(),
            displayNameHasError = viewModelState.errorField == ErrorField.DisplayName,
            summary = viewModelState.summary,
            summaryInputEnabled = viewModelState.saving.not(),
            summaryHasError = viewModelState.errorField == ErrorField.Summary,
            links = viewModelState.links.mapIndexed { index, link ->
                ProfileEditScreenUiState.Link(
                    title = "リンク ${index + 1}",
                    name = link.name,
                    url = link.url,
                    inputEnabled = viewModelState.saving.not(),
                    hasError = viewModelState.errorField == ErrorField.Links,
                    removeButtonEnabled = viewModelState.saving.not(),
                )
            },
            addLinkButtonEnabled = viewModelState.saving.not() && viewModelState.links.size < MAX_LINK_COUNT,
            likesPublic = viewModelState.likesPublic,
            likesPublicSwitchEnabled = viewModelState.saving.not(),
            saveButtonEnabled = viewModelState.saving.not() && viewModelState.processingImage().not(),
        )
    }

    private fun ViewModelState.saving(): Boolean = this is ViewModelState.Loaded && saving

    private fun ViewModelState.headerCrop(): HeaderCrop? = (this as? ViewModelState.Loaded)?.headerCrop

    private fun ViewModelState.sendingImage(): Boolean = this is ViewModelState.Loaded && (processingIcon || processingHeader)

    /**
     * プロフィール画像とヘッダーは同時に送らない。どちらの Update も Actor の全体を載せるので、後から積んだ方が先の画像を古い値に戻す。
     * ヘッダーの範囲を選んでいる間も、決めればすぐ送るので同じに扱う
     */
    private fun ViewModelState.processingImage(): Boolean = this is ViewModelState.Loaded && (processingIcon || processingHeader || headerCrop != null)

    sealed interface ViewModelState {
        data object Loading : ViewModelState

        data class Failed(
            val message: String,
        ) : ViewModelState

        /**
         * @param iconPreview 送った、または送っている画像。取り直さずにすぐ出す
         * @param processingIcon 選んだ画像を読んで、縮めて、送っている途中
         * @param headerPreview 送った、または送っている画像。取り直さずにすぐ出す
         * @param processingHeader 選んだ画像を読んで、縮めて、送っている途中
         * @param headerCrop 縦に長い画像を選び、使う範囲を選んでいる途中
         */
        data class Loaded(
            val iconPreview: ImageBitmap?,
            val processingIcon: Boolean,
            val currentIconUrl: String?,
            val headerPreview: ImageBitmap?,
            val processingHeader: Boolean,
            val currentHeaderUrl: String?,
            val headerCrop: HeaderCrop?,
            val displayName: String,
            val summary: String,
            val links: List<UserProfileLink>,
            val likesPublic: Boolean,
            val saving: Boolean,
            val errorField: ErrorField?,
        ) : ViewModelState
    }

    /**
     * @param source 横幅をヘッダーに合わせた画像
     * @param topRatio 使う範囲の位置。0 で一番上、1 で一番下
     */
    data class HeaderCrop(
        val source: ImageBitmap,
        val topRatio: Float,
    )

    enum class ErrorField {
        DisplayName,
        Summary,
        Links,
    }

    /**
     * @param field 入力のせいではない失敗なら null
     */
    private class SaveError(
        val message: String,
        val field: ErrorField?,
    )

    internal interface Event {
        fun openImagePicker()

        fun openHeaderImagePicker()

        fun navigateBack()

        fun showMessage(message: String)
    }

    companion object {
        /**
         * 選んだファイルを読む上限。縮めて送るので、スマートフォンの写真がそのまま収まる大きさにする
         */
        private const val MAX_SOURCE_IMAGE_BYTES = 20 * 1024 * 1024

        /**
         * 送る前に縮める大きさ。サーバーはこれを超える縦横を受け付けない
         */
        private const val ICON_SIDE_PX = 400

        /**
         * 送る前に縮める大きさ。サーバーはこれを超える縦横を受け付けない
         */
        private const val HEADER_WIDTH_PX = 1500
        private const val HEADER_HEIGHT_PX = 500

        /**
         * サーバーが受け付けるリンクの数
         */
        private const val MAX_LINK_COUNT = 4
    }
}
