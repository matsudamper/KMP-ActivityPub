package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun FollowingGroupsScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<FollowingGroupsScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : FollowingGroupsScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    FollowingGroupsContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun FollowingGroupsContent(
    uiState: FollowingGroupsScreenUiState,
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
                    text = "購読先",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            uiState.groups.forEachIndexed { index, group ->
                if (index > 0) {
                    HorizontalDivider()
                }
                GroupItem(group = group)
            }
        }
    }
    val renameDialog = uiState.renameDialog
    if (renameDialog != null) {
        RenameDialog(uiState = renameDialog)
    }
}

@Composable
private fun GroupItem(group: FollowingGroupsScreenUiState.Group) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = group.listener::onClick)
            .padding(vertical = 16.dp),
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

@Composable
private fun RenameDialog(uiState: FollowingGroupsScreenUiState.RenameDialog) {
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
