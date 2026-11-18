package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialog
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.ImageInputReceiver
import net.matsudamper.kmp.activitypub.frontend.ui.MultilineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.PostQuoteCard
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberImagePicker
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PostScreen(
    target: PostScreenTarget,
    navigationIcon: PostNavigationIcon,
    onNavigateBack: () -> Unit,
    onClose: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = rememberPostScreenUiState(
        target = target,
        onNavigateBack = onNavigateBack,
        onClose = onClose,
        snackbarHostState = snackbarHostState,
    )
    PostContentWithImageInput(uiState = uiState, navigationIcon = navigationIcon, snackbarHostState = snackbarHostState)
}

/**
 * 投稿の結果を受け取り続ける。カラムとして出すときは閉じている間も送信が続くので、
 * 投稿の中身を出していない間も呼び続けないと、完了の知らせが残って次に開いたカラムを閉じてしまう
 */
@Composable
internal fun rememberPostScreenUiState(
    target: PostScreenTarget,
    onNavigateBack: () -> Unit,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
): PostScreenUiState {
    val viewModel = koinViewModel<PostScreenViewModel> { parametersOf(target) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pickImage = rememberImagePicker(onPick = uiState.listener::onImagePicked)
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel.eventHandler, pickImage, snackbarHostState) {
        viewModel.eventHandler.collect(
            object : PostScreenViewModel.Event {
                override fun navigateBack() {
                    currentOnNavigateBack()
                }

                override fun closeToHome() {
                    currentOnClose()
                }

                override fun openImagePicker() {
                    pickImage()
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }
    return uiState
}

/**
 * 画像のドロップや貼り付けは画面全体で受けるので、投稿の中身を出している間だけ受け取り手になる
 */
@Composable
internal fun PostContentWithImageInput(
    uiState: PostScreenUiState,
    navigationIcon: PostNavigationIcon,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    ImageInputReceiver(onReceive = uiState.listener::onImagePicked)
    Box(modifier = modifier.fillMaxSize()) {
        PostContent(uiState = uiState, navigationIcon = navigationIcon, snackbarHostState = snackbarHostState)
    }
    val emojiPickerDialog = uiState.emojiPickerDialog
    if (emojiPickerDialog != null) {
        ReactionPickerDialog(dialogUiState = emojiPickerDialog)
    }
    val discardDraftDialog = uiState.discardDraftDialog
    if (discardDraftDialog != null) {
        DiscardDraftDialog(dialog = discardDraftDialog)
    }
}

/**
 * 投稿カラムで書きかけの投稿があるときに、編集や返信を始める前に確かめる
 */
@Composable
internal fun DiscardDraftDialog(dialog: PostScreenUiState.DiscardDraftDialog) {
    AlertDialog(
        onDismissRequest = dialog.listener::onDismiss,
        title = { Text("書きかけの投稿を破棄") },
        text = { Text(dialog.message) },
        confirmButton = {
            TextButton(onClick = dialog.listener::onClickDiscard) {
                Text(dialog.discardButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = dialog.listener::onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

/**
 * 押すと前の画面に戻るだけなら戻る、重ねた画面やカラムを閉じるなら閉じる
 */
internal enum class PostNavigationIcon {
    Back,
    Close,
}

@Composable
internal fun PostContent(
    uiState: PostScreenUiState,
    navigationIcon: PostNavigationIcon,
    snackbarHostState: SnackbarHostState,
) {
    // ダイアログで開いた投稿画面の下にホームの投稿カラムが残るので、入力欄ごとに id を分ける
    val bodyInputId = remember { "$POST_BODY_INPUT_ID_PREFIX-${Uuid.random()}" }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = uiState.listener::onClickClose,
                    enabled = uiState.closeButtonEnabled,
                ) {
                    when (navigationIcon) {
                        PostNavigationIcon.Back -> Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "戻る",
                        )

                        PostNavigationIcon.Close -> Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "投稿を閉じる",
                        )
                    }
                }
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val editTarget = uiState.editTarget
            if (editTarget != null) {
                EditTarget(editTarget = editTarget)
            }
            if (uiState.contentWarningInputVisible) {
                MultilineTextField(
                    value = uiState.contentWarning,
                    onValueChange = uiState.listener::onContentWarningChanged,
                    label = "注意書き",
                    inputId = POST_CONTENT_WARNING_INPUT_ID,
                    inputName = "contentWarning",
                    enabled = uiState.inputEnabled,
                    hasError = uiState.inputHasError,
                    onSubmitShortcut = uiState.listener::onPressPostShortcut,
                    insertion = null,
                    onHandleInsertion = {},
                    heightFitsContent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 読み終えたときに入力欄の位置が動かないよう、読んでいる間も入力欄を出して上に重ねる
            Box(contentAlignment = Alignment.Center) {
                MultilineTextField(
                    value = uiState.body,
                    onValueChange = uiState.listener::onBodyChanged,
                    label = "今なにしてる？",
                    inputId = bodyInputId,
                    inputName = "body",
                    enabled = uiState.inputEnabled,
                    hasError = uiState.inputHasError,
                    onSubmitShortcut = uiState.listener::onPressPostShortcut,
                    insertion = uiState.bodyInsertion,
                    onHandleInsertion = uiState.listener::onBodyInsertionHandled,
                    heightFitsContent = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.bodyLoadingVisible) {
                    CircularProgressIndicator()
                }
            }
            val quote = uiState.quote
            if (quote != null) {
                PostQuoteCard(quote = quote, unavailableText = "引用先の投稿を表示できません", textStyle = MaterialTheme.typography.bodyMedium)
            }
            if (uiState.images.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.images.forEach { image ->
                        SelectedImage(image = image)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = uiState.listener::onClickAddImage,
                        enabled = uiState.addImageButtonEnabled,
                    ) {
                        Icon(
                            imageVector = AppIcons.Image,
                            contentDescription = "画像を追加",
                        )
                    }
                    if (uiState.addImageLoadingVisible) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                }
                IconButton(
                    onClick = uiState.listener::onClickAddEmoji,
                    enabled = uiState.addEmojiButtonEnabled,
                ) {
                    Icon(
                        imageVector = AppIcons.Emoticon,
                        contentDescription = "絵文字を追加",
                    )
                }
                if (uiState.contentWarningButtonVisible) {
                    IconToggleButton(
                        checked = uiState.contentWarningButtonChecked,
                        onCheckedChange = { uiState.listener.onClickContentWarning() },
                        enabled = uiState.contentWarningButtonEnabled,
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(
                            text = "CW",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = uiState.listener::onClickPost,
                    enabled = uiState.postButtonEnabled,
                ) {
                    Text(uiState.postButtonText)
                }
            }
            val replyTarget = uiState.replyTarget
            if (replyTarget != null) {
                ReplyTarget(replyTarget = replyTarget)
            }
        }
    }
}

@Composable
private fun ReplyTarget(replyTarget: PostScreenUiState.ReplyTarget) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Reply,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "返信先",
                modifier = Modifier.padding(start = 4.dp).weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = replyTarget.listener::onClickRemove,
                enabled = replyTarget.removeButtonEnabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "返信をやめる",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        PostQuoteCard(quote = replyTarget.post, unavailableText = "返信先の投稿を表示できません", textStyle = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EditTarget(editTarget: PostScreenUiState.EditTarget) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "投稿を編集中",
            modifier = Modifier.padding(start = 4.dp).weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = editTarget.listener::onClickCancel,
            enabled = editTarget.cancelButtonEnabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "編集をやめる",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SelectedImage(image: PostScreenUiState.Image) {
    Box(
        modifier = Modifier.size(SELECTED_IMAGE_SIZE).clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = image.preview.imageRequest(LocalPlatformContext.current),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = image.listener::onClickRemove,
            enabled = image.removeButtonEnabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = Color.White,
            ),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "画像を外す",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private val SELECTED_IMAGE_SIZE = 96.dp

private const val POST_BODY_INPUT_ID_PREFIX = "post-body"

private const val POST_CONTENT_WARNING_INPUT_ID = "post-content-warning"
