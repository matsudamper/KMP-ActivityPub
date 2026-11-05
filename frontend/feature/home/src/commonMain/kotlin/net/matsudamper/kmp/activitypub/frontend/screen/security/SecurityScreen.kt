package net.matsudamper.kmp.activitypub.frontend.screen.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SecurityScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<SecurityScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : SecurityScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    SecurityContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun SecurityContent(
    uiState: SecurityScreenUiState,
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
                    text = "セキュリティ",
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
            val password = uiState.password
            if (password != null) {
                HorizontalDivider()
                PasswordSection(password = password, listener = uiState.listener)
            }
        }
        val addDialog = uiState.addDialog
        if (addDialog != null) {
            AddPasskeyDialog(dialog = addDialog, listener = uiState.listener)
        }
        val deleteDialog = uiState.deleteDialog
        if (deleteDialog != null) {
            DeletePasskeyDialog(dialog = deleteDialog, listener = uiState.listener)
        }
        val passwordDeleteDialog = uiState.passwordDeleteDialog
        if (passwordDeleteDialog != null) {
            DeletePasswordDialog(dialog = passwordDeleteDialog, listener = uiState.listener)
        }
    }
}

@Composable
private fun PasskeyList(
    uiState: SecurityScreenUiState,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (val passkeyList = uiState.passkeyList) {
            SecurityScreenUiState.PasskeyList.Loading -> {
                PasskeyListText(text = "読み込み中")
            }

            SecurityScreenUiState.PasskeyList.Empty -> {
                PasskeyListText(text = "登録したパスキーは無い")
            }

            is SecurityScreenUiState.PasskeyList.Error -> {
                PasskeyListText(text = passkeyList.message)
            }

            is SecurityScreenUiState.PasskeyList.Items -> {
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
private fun PasswordSection(
    password: SecurityScreenUiState.Password,
    listener: SecurityScreenUiState.Listener,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "パスワード",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = password.description,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (password.deleteButtonVisible) {
            OutlinedButton(
                onClick = listener::onClickDeletePassword,
                enabled = password.deleteButtonEnabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("パスワードを削除")
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
private fun AddPasskeyDialog(
    dialog: SecurityScreenUiState.AddDialog,
    listener: SecurityScreenUiState.Listener,
) {
    AlertDialog(
        onDismissRequest = listener::onDismissAddDialog,
        properties = DialogProperties(
            dismissOnBackPress = dialog.closeEnabled,
            dismissOnClickOutside = dialog.closeEnabled,
        ),
        title = { Text("パスキーを追加") },
        text = {
            SingleLineTextField(
                value = dialog.passkeyName,
                onValueChange = listener::onPasskeyNameChanged,
                onImeAction = listener::onClickConfirmAdd,
                label = "キーの名前",
                kind = SingleLineTextFieldKind.Plain,
                autofill = null,
                formId = null,
                required = false,
                imeAction = ImeAction.Done,
                inputId = PASSKEY_NAME_INPUT_ID,
                inputName = "passkey-name",
                enabled = dialog.nameInputEnabled,
                hasError = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = listener::onClickConfirmAdd,
                enabled = dialog.addButtonEnabled,
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(
                onClick = listener::onDismissAddDialog,
                enabled = dialog.closeEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun DeletePasskeyDialog(
    dialog: SecurityScreenUiState.DeleteDialog,
    listener: SecurityScreenUiState.Listener,
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

@Composable
private fun DeletePasswordDialog(
    dialog: SecurityScreenUiState.PasswordDeleteDialog,
    listener: SecurityScreenUiState.Listener,
) {
    AlertDialog(
        onDismissRequest = listener::onDismissDeletePasswordDialog,
        title = { Text("パスワードを削除") },
        text = { Text("パスワードではログインできなくなる。パスキーを無くすとログインできなくなるので、複数の端末で登録しておく") },
        confirmButton = {
            TextButton(
                onClick = listener::onClickConfirmDeletePassword,
                enabled = dialog.deleteButtonEnabled,
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = listener::onDismissDeletePasswordDialog) {
                Text("キャンセル")
            }
        },
    )
}

private const val PASSKEY_NAME_INPUT_ID = "settings-passkey-name"
