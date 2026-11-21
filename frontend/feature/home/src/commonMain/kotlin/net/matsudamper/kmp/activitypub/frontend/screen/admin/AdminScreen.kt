package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AdminScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<AdminScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : AdminScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun navigateToFailedDeliveries() {
                    navigator.navigate(Screen.FailedDeliveries)
                }

                override fun navigateToUnknownObjectTypes() {
                    navigator.navigate(Screen.UnknownObjectTypes)
                }

                override fun navigateToDeliveryTargetLimit() {
                    navigator.navigate(Screen.DeliveryTargetLimit)
                }

                override fun navigateToDeletedRemoteNotes() {
                    navigator.navigate(Screen.DeletedRemoteNotes)
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    AdminContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun AdminContent(
    uiState: AdminScreenUiState,
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
                IconButton(onClick = uiState.listener::onClickBack) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "戻る",
                    )
                }
                Text(
                    text = "サーバー管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val content = uiState.content) {
                AdminScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is AdminScreenUiState.Content.Loaded -> {
                    AdminMenu(loaded = content, listener = uiState.listener)
                }

                is AdminScreenUiState.Content.Message -> {
                    Text(
                        text = content.message,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminMenu(
    loaded: AdminScreenUiState.Content.Loaded,
    listener: AdminScreenUiState.Listener,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = MENU_MAX_WIDTH).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AdminSection(title = "投稿") {
                AdminMenuItem(
                    icon = AppIcons.Delete,
                    title = "削除済みの投稿",
                    description = "投稿者が消した他のサーバーの投稿。タイムラインに削除済みと出すために残しているものを消せます",
                    onClick = listener::onClickDeletedRemoteNotes,
                )
            }
            AdminSection(title = "配信") {
                AdminMenuItem(
                    icon = AppIcons.CloudOff,
                    title = "諦めた配信",
                    description = "送り直しても届かず、送るのを諦めた配信",
                    onClick = listener::onClickFailedDeliveries,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AdminMenuItem(
                    icon = AppIcons.CloudDone,
                    title = "配る先の上限",
                    description = "同じサーバーからのフォローで配る先の数と上限。上限を超えるフォローは断ります",
                    onClick = listener::onClickDeliveryTargetLimit,
                )
            }
            AdminSection(title = "受信") {
                AdminMenuItem(
                    icon = AppIcons.Search,
                    title = "知らない型",
                    description = "届いたもののうち、このサーバーが読み方を知らない型と、その出どころ",
                    onClick = listener::onClickUnknownObjectTypes,
                )
            }
            AdminSection(title = "他のサーバーのユーザー") {
                RemoteProfileRefreshItem(uiState = loaded.remoteProfileRefresh)
            }
        }
    }
}

@Composable
private fun AdminSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            content()
        }
    }
}

@Composable
private fun AdminMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = AppIcons.ArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoteProfileRefreshItem(
    uiState: AdminScreenUiState.RemoteProfileRefresh,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = AppIcons.Person,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "プロフィールの取り直し",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "保存している他のサーバーのユーザー全員のプロフィールを、相手のサーバーから取り直します。" +
                    "相手のサーバーに負荷を掛けないよう 1 人ずつ取りに行くので、時間が掛かります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                onClick = uiState.listener::onClickStart,
                enabled = uiState.startButtonEnabled,
            ) {
                Icon(
                    imageVector = AppIcons.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "全員を取り直す",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private val MENU_MAX_WIDTH = 720.dp
