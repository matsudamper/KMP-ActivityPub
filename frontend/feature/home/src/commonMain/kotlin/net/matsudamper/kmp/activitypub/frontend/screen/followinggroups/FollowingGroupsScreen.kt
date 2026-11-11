package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(
                title = "メイン",
                description = "どのグループにも入れていない購読が入ります",
            ) {
                GroupRow(item = uiState.home, icon = AppIcons.Home)
            }
            Section(
                title = "グループ",
                description = "購読ボタンのメニューから追加できます",
            ) {
                if (uiState.groupsEmptyMessageVisible) {
                    Text(
                        text = "グループはまだありません",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.groups.forEachIndexed { index, group ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                    GroupRow(item = group, icon = null)
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(content = { content() })
        }
    }
}

/**
 * @param icon null ならグループ名の頭文字を出す
 */
@Composable
private fun GroupRow(
    item: FollowingGroupsScreenUiState.Item,
    icon: ImageVector?,
) {
    when (val state = item.state) {
        is FollowingGroupsScreenUiState.ItemState.Display -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = state.listener::onClickEdit)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupBadge(name = item.name, icon = icon)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = state.listener::onClickEdit) {
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = "名前を変更",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is FollowingGroupsScreenUiState.ItemState.Editing -> {
            EditingGroupRow(initialName = item.name, icon = icon, state = state)
        }
    }
}

@Composable
private fun EditingGroupRow(
    initialName: String,
    icon: ImageVector?,
    state: FollowingGroupsScreenUiState.ItemState.Editing,
) {
    var name by remember { mutableStateOf(initialName) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupBadge(name = name, icon = icon)
        Spacer(modifier = Modifier.width(16.dp))
        SingleLineTextField(
            value = name,
            onValueChange = { name = it },
            onImeAction = { state.listener.onClickSave(name) },
            label = "名前",
            kind = SingleLineTextFieldKind.Plain,
            autofill = null,
            formId = null,
            required = true,
            imeAction = ImeAction.Done,
            inputId = "rename-following-group-name",
            inputName = "rename-following-group-name",
            enabled = state.inputEnabled,
            hasError = false,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = state.listener::onClickCancel,
            enabled = state.inputEnabled,
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "キャンセル",
            )
        }
        IconButton(
            onClick = { state.listener.onClickSave(name) },
            enabled = state.inputEnabled,
        ) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = "保存",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GroupBadge(
    name: String,
    icon: ImageVector?,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon == null) {
                Text(
                    text = name.trim().take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(imageVector = icon, contentDescription = null)
            }
        }
    }
}
