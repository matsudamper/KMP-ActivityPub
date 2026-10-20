package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
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

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : SettingsScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun loggedOut() {
                    navigator.replaceAll(Screen.Login)
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
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
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "パスキー",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "パスワードの代わりに、この端末の生体認証や PIN でログインできるようにする",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PasskeyList(uiState = uiState)
                    if (uiState.passkeyNameInputVisible) {
                        SingleLineTextField(
                            value = uiState.passkeyName,
                            onValueChange = uiState.listener::onPasskeyNameChanged,
                            onImeAction = {},
                            label = "キーの名前",
                            kind = SingleLineTextFieldKind.Plain,
                            autofill = null,
                            formId = null,
                            required = false,
                            imeAction = ImeAction.Default,
                            inputId = PASSKEY_NAME_INPUT_ID,
                            inputName = "passkey-name",
                            enabled = uiState.passkeyNameInputEnabled,
                            hasError = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (uiState.addPasskeyButtonVisible) {
                        Button(
                            onClick = uiState.listener::onClickAddPasskey,
                            enabled = uiState.addPasskeyButtonEnabled,
                        ) {
                            Text("パスキーを追加")
                        }
                    }
                    if (uiState.passkeyUnavailableMessageVisible) {
                        Text(
                            text = "この端末ではパスキーを追加できない",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        val deleteDialog = uiState.deleteDialog
        if (deleteDialog != null) {
            DeletePasskeyDialog(dialog = deleteDialog, listener = uiState.listener)
        }
    }
}

@Composable
private fun PasskeyList(
    uiState: SettingsScreenUiState,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (val passkeyList = uiState.passkeyList) {
            SettingsScreenUiState.PasskeyList.Loading -> {
                PasskeyListText(text = "読み込み中")
            }

            SettingsScreenUiState.PasskeyList.Empty -> {
                PasskeyListText(text = "登録したパスキーは無い")
            }

            is SettingsScreenUiState.PasskeyList.Error -> {
                PasskeyListText(text = passkeyList.message)
            }

            is SettingsScreenUiState.PasskeyList.Items -> {
                passkeyList.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(
                            onClick = item.listener::onClickDelete,
                            enabled = item.deleteButtonEnabled,
                        ) {
                            Text("削除")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PasskeyListText(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeletePasskeyDialog(
    dialog: SettingsScreenUiState.DeleteDialog,
    listener: SettingsScreenUiState.Listener,
) {
    AlertDialog(
        onDismissRequest = listener::onDismissDeleteDialog,
        title = { Text("パスキーを削除") },
        text = { Text("「${dialog.passkeyName}」を削除する。削除したパスキーではログインできなくなる") },
        confirmButton = {
            TextButton(
                onClick = listener::onClickConfirmDelete,
                enabled = dialog.deleteButtonEnabled,
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = listener::onDismissDeleteDialog) {
                Text("キャンセル")
            }
        },
    )
}

private const val PASSKEY_NAME_INPUT_ID = "settings-passkey-name"
