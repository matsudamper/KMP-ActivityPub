package net.matsudamper.kmp.activitypub.frontend.screen.admin.deliverytargetlimit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
internal fun DeliveryTargetLimitScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<DeliveryTargetLimitScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : DeliveryTargetLimitScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    DeliveryTargetLimitContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun DeliveryTargetLimitContent(
    uiState: DeliveryTargetLimitScreenUiState,
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
                    text = "配る先の上限",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = uiState.content) {
                DeliveryTargetLimitScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                DeliveryTargetLimitScreenUiState.Content.LoadFailed -> {
                    TextButton(
                        onClick = uiState.listener::onClickRetry,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    ) {
                        Text("再読み込み")
                    }
                }

                is DeliveryTargetLimitScreenUiState.Content.Loaded -> {
                    LoadedContent(content = content, listener = uiState.listener)
                }
            }
        }
    }
}

@Composable
private fun LoadedContent(
    content: DeliveryTargetLimitScreenUiState.Content.Loaded,
    listener: DeliveryTargetLimitScreenUiState.Listener,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Section(
            title = "上限",
            description = "同じサーバーからのフォローで配る先の数の上限です。sharedInbox を持たないサーバーは、フォローしてきた人ごとに配る先が増えます。" +
                "正当なサーバーが上限に達したら引き上げてください。",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = content.currentLimitText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SingleLineTextField(
                        value = content.limitInput,
                        onValueChange = listener::onChangeLimitInput,
                        onImeAction = listener::onClickSave,
                        label = "1〜1000",
                        kind = SingleLineTextFieldKind.Plain,
                        autofill = null,
                        formId = null,
                        required = true,
                        imeAction = ImeAction.Done,
                        inputId = "delivery-target-limit",
                        inputName = "delivery-target-limit",
                        enabled = content.limitInputEnabled,
                        hasError = false,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = listener::onClickSave,
                        enabled = content.saveButtonEnabled,
                    ) {
                        Text("保存")
                    }
                }
            }
        }
        Section(
            title = "サーバーごとの配る先",
            description = "配る先の多い順。どのアカウントへのフォローかは問わず、まだ成立していないフォローも数えます。",
        ) {
            if (content.emptyMessageVisible) {
                Text(
                    text = "フォローしてきたサーバーはありません",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content.hosts.forEachIndexed { index, host ->
                if (index > 0) {
                    HorizontalDivider()
                }
                HostRow(host = host)
            }
        }
    }
}

@Composable
private fun HostRow(
    host: DeliveryTargetLimitScreenUiState.Host,
) {
    val contentColor = if (host.rowHighlighted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (host.rowHighlighted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = host.host,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val warningText = host.warningText
            if (warningText != null) {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
        Text(
            text = host.countText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (host.rowHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
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
