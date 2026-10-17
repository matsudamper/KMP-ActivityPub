package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<SettingsScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : SettingsScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }
            },
        )
    }

    SettingsContent(uiState = uiState)
}

@Composable
internal fun SettingsContent(
    uiState: SettingsScreenUiState,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
            OutlinedButton(
                onClick = uiState.listener::onClickLogout,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("ログアウト")
            }
        }
    }
}
