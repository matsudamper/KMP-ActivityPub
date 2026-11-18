package net.matsudamper.kmp.activitypub.frontend.screen.admin.faileddelivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun FailedDeliveryScreen(
    id: String,
    navigator: Navigator,
) {
    val viewModel = koinViewModel<FailedDeliveryScreenViewModel>(key = id) { parametersOf(id) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : FailedDeliveryScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }
            },
        )
    }

    FailedDeliveryContent(uiState = uiState)
}

@Composable
internal fun FailedDeliveryContent(
    uiState: FailedDeliveryScreenUiState,
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
                    text = "諦めた配信",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = uiState.content) {
                FailedDeliveryScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is FailedDeliveryScreenUiState.Content.Error -> {
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

                is FailedDeliveryScreenUiState.Content.Loaded -> {
                    // 理由や inbox を調べるのに写せるようにする
                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            content.fields.forEach { field ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = field.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = field.value,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
