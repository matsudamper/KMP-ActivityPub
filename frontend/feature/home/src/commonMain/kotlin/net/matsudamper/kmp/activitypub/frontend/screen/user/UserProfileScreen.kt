package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun UserProfileScreen(
    username: String,
    navigator: Navigator,
) {
    val viewModel = koinViewModel<UserProfileScreenViewModel>(key = username) { parametersOf(username) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(object : UserProfileScreenViewModel.Event {
            override fun navigateBack() {
                navigator.back()
            }
        })
    }
    UserProfileContent(uiState = uiState)
}

@Composable
internal fun UserProfileContent(
    uiState: UserProfileScreenUiState,
) {
    Scaffold(
        topBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = uiState.listener::onClickBack) {
                    Icon(imageVector = AppIcons.ArrowBack, contentDescription = "戻る")
                }
                Text(text = "プロフィール", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val content = uiState.content) {
                UserProfileScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is UserProfileScreenUiState.Content.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item { ProfileHeader(profile = content) }
                        item {
                            Text(text = "投稿", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                        }
                        if (content.posts.isEmpty()) {
                            item { Text(text = "表示できる投稿はありません", modifier = Modifier.padding(16.dp)) }
                        }
                        items(content.posts) { post ->
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = post.content)
                                Text(text = post.publishedAt, style = MaterialTheme.typography.labelMedium)
                            }
                            HorizontalDivider()
                        }
                    }
                }

                UserProfileScreenUiState.Content.NotFound -> {
                    Text(text = "ユーザーが見つかりません", modifier = Modifier.align(Alignment.Center))
                }

                is UserProfileScreenUiState.Content.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = content.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = uiState.listener::onClickRetry) { Text("再読み込み") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfileScreenUiState.Content.Loaded) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileIcon(iconUrl = profile.iconUrl, contentDescription = "プロフィール画像", modifier = Modifier.size(64.dp))
            Column {
                Text(text = profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = profile.acct, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(text = profile.followingText, style = MaterialTheme.typography.labelLarge)
        if (profile.biography != null) {
            Text(text = profile.biography)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
