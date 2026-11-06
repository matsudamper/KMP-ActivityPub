package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialog
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTabRow
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileGridCells
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinks
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaGridItem
import net.matsudamper.kmp.activitypub.frontend.ui.URL_COPIED_MESSAGE
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberUrlSharer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun UserProfileScreen(
    username: String,
    navigator: Navigator,
) {
    val viewModel = koinViewModel<UserProfileScreenViewModel>(key = username) { parametersOf(username) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val urlSharer = rememberUrlSharer(
        onCopiedToClipboard = { coroutineScope.launch { snackbarHostState.showSnackbar(URL_COPIED_MESSAGE) } },
    )
    LaunchedEffect(viewModel.eventHandler, navigator, uriHandler) {
        viewModel.eventHandler.collect(object : UserProfileScreenViewModel.Event {
            override fun navigateBack() {
                navigator.back()
            }

            override fun showMessage(message: String) {
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            }

            override fun openUrl(url: String) {
                uriHandler.openUri(url)
            }

            override fun shareUrl(url: String) {
                urlSharer(url)
            }

            override fun navigateToImage(url: String) {
                navigator.navigate(Screen.Image(url = url))
            }

            override fun navigateToPostDetail(postId: String) {
                navigator.navigate(Screen.PostDetail(postId = postId))
            }
        })
    }
    UserProfileContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun UserProfileContent(
    uiState: UserProfileScreenUiState,
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
                    Icon(imageVector = AppIcons.ArrowBack, contentDescription = "戻る")
                }
                Text(text = "プロフィール", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val content = uiState.content) {
                UserProfileScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is UserProfileScreenUiState.Content.Loaded -> {
                    LazyVerticalGrid(
                        columns = ProfileGridCells,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        fullLineItem {
                            ProfileHeader(
                                profile = content,
                                onClickFollow = uiState.listener::onClickFollow,
                            )
                        }
                        fullLineItem {
                            ProfileContentTabRow(
                                selectedTab = content.selectedTab,
                                onClickTab = uiState.listener::onClickTab,
                            )
                        }
                        when (content.selectedTab) {
                            ProfileContentTab.Posts -> {
                                val postsEmptyMessage = content.postsEmptyMessage
                                if (postsEmptyMessage != null) {
                                    fullLineItem { EmptyMessage(text = postsEmptyMessage) }
                                }
                                items(
                                    items = content.posts,
                                    span = { GridItemSpan(maxLineSpan) },
                                ) { post ->
                                    Column {
                                        PostItem(uiState = post)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }

                            ProfileContentTab.Media -> {
                                val mediaEmptyMessage = content.mediaEmptyMessage
                                if (mediaEmptyMessage != null) {
                                    fullLineItem { EmptyMessage(text = mediaEmptyMessage) }
                                }
                                items(content.media) { media ->
                                    ProfileMediaGridItem(media = media, modifier = Modifier.padding(1.dp))
                                }
                            }
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
    val deleteNoteDialog = uiState.deleteNoteDialog
    if (deleteNoteDialog != null) {
        DeleteNoteDialog(uiState = deleteNoteDialog)
    }
}

private fun LazyGridScope.fullLineItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProfileHeader(
    profile: UserProfileScreenUiState.Content.Loaded,
    onClickFollow: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileIcon(iconUrl = profile.iconUrl, contentDescription = "プロフィール画像", modifier = Modifier.size(72.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = profile.acct,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val followButton = profile.followButton
        if (followButton != null) {
            OutlinedButton(
                onClick = onClickFollow,
                enabled = followButton.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(followButton.text)
            }
        }
        if (profile.biography != null) {
            Text(text = profile.biography, style = MaterialTheme.typography.bodyMedium)
        }
        if (profile.links.isNotEmpty()) {
            ProfileLinks(links = profile.links, modifier = Modifier.fillMaxWidth())
        }
    }
}
