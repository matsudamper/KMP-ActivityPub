package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.MultilineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberImagePicker
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ProfileEditScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<ProfileEditScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pickIcon = rememberImagePicker(onPick = uiState.listener::onIconPicked)

    LaunchedEffect(viewModel.eventHandler, navigator, pickIcon) {
        viewModel.eventHandler.collect(
            object : ProfileEditScreenViewModel.Event {
                override fun openImagePicker() {
                    pickIcon()
                }

                override fun navigateBack() {
                    navigator.back()
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    ProfileEditContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun ProfileEditContent(
    uiState: ProfileEditScreenUiState,
    snackbarHostState: SnackbarHostState,
) {
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
                    onClick = uiState.listener::onClickBack,
                    enabled = uiState.backButtonEnabled,
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "戻る",
                    )
                }
                Text(
                    text = "プロフィールを編集",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = uiState.content) {
                ProfileEditScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is ProfileEditScreenUiState.Content.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = content.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = uiState.listener::onClickRetry) {
                            Text("再読み込み")
                        }
                    }
                }

                is ProfileEditScreenUiState.Content.Loaded -> {
                    LoadedContent(
                        content = content,
                        listener = uiState.listener,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    content: ProfileEditScreenUiState.Content.Loaded,
    listener: ProfileEditScreenUiState.Listener,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Section(title = "基本情報") {
            IconRow(content = content, listener = listener)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SingleLineTextField(
                    value = content.displayName,
                    onValueChange = listener::onDisplayNameChanged,
                    onImeAction = listener::onClickSave,
                    label = "表示名",
                    kind = SingleLineTextFieldKind.Plain,
                    autofill = null,
                    formId = null,
                    required = false,
                    imeAction = ImeAction.Done,
                    inputId = DISPLAY_NAME_INPUT_ID,
                    inputName = "displayName",
                    enabled = content.displayNameInputEnabled,
                    hasError = content.displayNameHasError,
                    modifier = Modifier.fillMaxWidth(),
                )
                SupportingText(text = "空にするとユーザー名で表示されます")
            }
            MultilineTextField(
                value = content.summary,
                onValueChange = listener::onSummaryChanged,
                label = "自己紹介",
                inputId = SUMMARY_INPUT_ID,
                inputName = "summary",
                enabled = content.summaryInputEnabled,
                hasError = content.summaryHasError,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Section(title = "リンク") {
            content.links.forEachIndexed { index, link ->
                LinkCard(
                    index = index,
                    link = link,
                    listener = listener,
                )
            }
            OutlinedButton(
                onClick = listener::onClickAddLink,
                enabled = content.addLinkButtonEnabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text("リンクを追加")
            }
        }
        Button(
            onClick = listener::onClickSave,
            enabled = content.saveButtonEnabled,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IconRow(
    content: ProfileEditScreenUiState.Content.Loaded,
    listener: ProfileEditScreenUiState.Listener,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            val iconPreview = content.iconPreview
            if (iconPreview != null) {
                Image(
                    bitmap = iconPreview,
                    contentDescription = "プロフィール画像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                ProfileIcon(
                    iconUrl = content.currentIconUrl,
                    contentDescription = "プロフィール画像",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (content.iconLoadingVisible) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
            }
        }
        OutlinedButton(
            onClick = listener::onClickSelectIcon,
            enabled = content.iconSelectButtonEnabled,
        ) {
            Text("プロフィール画像を選ぶ")
        }
    }
}

@Composable
private fun LinkCard(
    index: Int,
    link: ProfileEditScreenUiState.Link,
    listener: ProfileEditScreenUiState.Listener,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = link.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { listener.onClickRemoveLink(index) },
                    enabled = link.removeButtonEnabled,
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = "${link.title}を削除",
                    )
                }
            }
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleLineTextField(
                    value = link.name,
                    onValueChange = { listener.onLinkNameChanged(index = index, text = it) },
                    onImeAction = {},
                    label = "名前",
                    kind = SingleLineTextFieldKind.Plain,
                    autofill = null,
                    formId = null,
                    required = false,
                    imeAction = ImeAction.Next,
                    inputId = "$LINK_NAME_INPUT_ID_PREFIX$index",
                    inputName = "linkName$index",
                    enabled = link.inputEnabled,
                    hasError = link.hasError,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleLineTextField(
                    value = link.url,
                    onValueChange = { listener.onLinkUrlChanged(index = index, text = it) },
                    onImeAction = {},
                    label = "URL",
                    kind = SingleLineTextFieldKind.Plain,
                    autofill = null,
                    formId = null,
                    required = false,
                    imeAction = ImeAction.Next,
                    inputId = "$LINK_URL_INPUT_ID_PREFIX$index",
                    inputName = "linkUrl$index",
                    enabled = link.inputEnabled,
                    hasError = link.hasError,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val DISPLAY_NAME_INPUT_ID = "profile-edit-display-name"
private const val SUMMARY_INPUT_ID = "profile-edit-summary"
private const val LINK_NAME_INPUT_ID_PREFIX = "profile-edit-link-name-"
private const val LINK_URL_INPUT_ID_PREFIX = "profile-edit-link-url-"
