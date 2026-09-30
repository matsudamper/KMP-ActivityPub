package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.AdminScaffold
import net.matsudamper.kmp.activitypub.frontend.ui.ContentMaxWidth
import net.matsudamper.kmp.activitypub.frontend.ui.LoadMoreOnScrollEnd
import net.matsudamper.kmp.activitypub.frontend.ui.RetainedScreenState
import net.matsudamper.kmp.activitypub.frontend.ui.SectionCard
import net.matsudamper.kmp.activitypub.frontend.ui.rememberRetained

@Composable
fun AdminAccountsScreen(
    retainedScreenState: RetainedScreenState,
    navController: Navigator,
) {
    val viewModel = rememberRetained(retainedScreenState) { viewModelScope ->
        AdminAccountsScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminAccountsScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminAccountsContent(uiState = uiState)
}

@Composable
internal fun AdminAccountsContent(
    uiState: AdminAccountsScreenUiState,
) {
    AdminScaffold("ユーザー", listener = uiState.listener) { wide ->
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ユーザー一覧", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = uiState.listener::onClickNewAccount) { Text("追加") }
            }
            when (val content = uiState.content) {
                AdminAccountsScreenUiState.Content.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                AdminAccountsScreenUiState.Content.RequireLogin -> RequireLoginCard(onClickAdmin = uiState.listener::onClickAdmin)

                is AdminAccountsScreenUiState.Content.Error -> SectionCard("一覧を出せない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = uiState.listener::onClickReload) { Text("もう一度試す") }
                }

                is AdminAccountsScreenUiState.Content.Loaded -> Accounts(
                    content = content,
                    wide = wide,
                    listener = uiState.listener,
                )
            }
        }
    }
}

@Composable
private fun Accounts(
    content: AdminAccountsScreenUiState.Content.Loaded,
    wide: Boolean,
    listener: AdminAccountsScreenUiState.Listener,
) {
    if (content.accounts.isEmpty()) {
        SectionCard("ユーザー") { Text("まだユーザーはいません。「追加」から登録できます。") }
        return
    }
    val columns = if (wide) 2 else 1
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxWidth().verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        content.accounts.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { account ->
                    AccountCard(
                        account = account,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (content.loadMoreVisible) {
            LoadMoreOnScrollEnd(
                scrollState = scrollState,
                loadMoreOnVisible = content.loadMoreOnVisible,
                itemCount = content.accounts.size,
                onLoadMore = listener::onLoadMore,
            )
            val errorMessage = content.loadMoreErrorMessage
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = listener::onLoadMore) {
                        Text("もう一度試す")
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AdminAccountsScreenUiState.Account,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = account.username,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SelectionContainer {
                Text(
                    text = account.acct,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text("登録: ${account.createdAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
