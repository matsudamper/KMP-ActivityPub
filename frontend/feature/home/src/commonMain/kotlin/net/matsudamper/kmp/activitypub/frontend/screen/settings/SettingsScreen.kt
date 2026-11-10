package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<SettingsScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.eventHandler, navigator, uriHandler) {
        viewModel.eventHandler.collect(
            object : SettingsScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun navigateToSecurity() {
                    navigator.navigate(Screen.Security)
                }

                override fun loggedOut() {
                    navigator.replaceAll(Screen.Login)
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }

                override fun openUrl(url: String) {
                    uriHandler.openUri(url)
                }
            },
        )
    }

    SettingsContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun SettingsContent(
    uiState: SettingsScreenUiState,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = uiState.listener::onClickBack) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "戻る",
                    )
                }
                Text(
                    text = "設定",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeSection(themeOptions = uiState.themeOptions)
            HorizontalDivider()
            FollowingGroupsSection(followingGroups = uiState.followingGroups)
            HorizontalDivider()
            SecurityItem(listener = uiState.listener)
            HorizontalDivider()
            GitHubReleaseItem(listener = uiState.listener)
            HorizontalDivider()
            OutlinedButton(
                onClick = uiState.listener::onClickLogout,
                enabled = uiState.logoutButtonEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("ログアウト")
            }
        }
    }
    val renameFollowingGroupDialog = uiState.renameFollowingGroupDialog
    if (renameFollowingGroupDialog != null) {
        RenameFollowingGroupDialog(uiState = renameFollowingGroupDialog)
    }
}

@Composable
private fun ThemeSection(
    themeOptions: List<SettingsScreenUiState.ThemeOption>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "テーマ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            themeOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option.selected,
                            onClick = option.listener::onClick,
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option.selected,
                        onClick = null,
                    )
                    Text(
                        text = option.label,
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowingGroupsSection(
    followingGroups: List<SettingsScreenUiState.FollowingGroupItem>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "購読先",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        followingGroups.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = group.listener::onClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "名前を変更",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RenameFollowingGroupDialog(uiState: SettingsScreenUiState.RenameFollowingGroupDialog) {
    var name by remember { mutableStateOf(uiState.initialName) }
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("購読先の名前を変更") },
        text = {
            SingleLineTextField(
                value = name,
                onValueChange = { name = it },
                onImeAction = { uiState.listener.onClickSave(name) },
                label = "名前",
                kind = SingleLineTextFieldKind.Plain,
                autofill = null,
                formId = null,
                required = true,
                imeAction = ImeAction.Done,
                inputId = "rename-following-group-name",
                inputName = "rename-following-group-name",
                enabled = uiState.inputEnabled,
                hasError = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { uiState.listener.onClickSave(name) },
                enabled = uiState.inputEnabled,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = uiState.listener::onDismiss,
                enabled = uiState.inputEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun SecurityItem(
    listener: SettingsScreenUiState.Listener,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = listener::onClickSecurity)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "セキュリティ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "パスキー・パスワード",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GitHubReleaseItem(
    listener: SettingsScreenUiState.Listener,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = listener::onClickGitHubRelease)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "リリース",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "GitHub で最新のリリースを見る",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = AppIcons.OpenInNew,
            contentDescription = "外部リンク",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
