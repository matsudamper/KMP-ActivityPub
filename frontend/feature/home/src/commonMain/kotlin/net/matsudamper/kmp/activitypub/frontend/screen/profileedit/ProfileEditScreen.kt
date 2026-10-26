package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
    val pickIcon =
        rememberImagePicker(
            maxBytes = ProfileEditScreenViewModel.MAX_ICON_BYTES,
            onPick = uiState.listener::onIconPicked,
            onTooLarge = uiState.listener::onIconTooLarge,
            onReadError = uiState.listener::onIconReadError,
        )

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
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
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val newIcon = content.newIcon
            if (newIcon != null) {
                Image(
                    bitmap = newIcon,
                    contentDescription = "新しいプロフィール画像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(CircleShape),
                )
            } else {
                ProfileIcon(
                    iconUrl = content.currentIconUrl,
                    contentDescription = "プロフィール画像",
                    modifier = Modifier.size(72.dp),
                )
            }
            OutlinedButton(
                onClick = listener::onClickSelectIcon,
                enabled = content.iconSelectButtonEnabled,
            ) {
                Text("プロフィール画像を選ぶ")
            }
        }
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
        Text(
            text = "空にするとユーザー名で表示されます",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = listener::onClickSave,
            enabled = content.saveButtonEnabled,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("保存")
        }
    }
}

private const val DISPLAY_NAME_INPUT_ID = "profile-edit-display-name"
