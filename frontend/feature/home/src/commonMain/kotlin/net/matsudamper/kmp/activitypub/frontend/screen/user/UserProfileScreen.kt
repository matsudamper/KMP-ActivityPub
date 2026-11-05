package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
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

            override fun navigateToImage(url: String) {
                navigator.navigate(Screen.Image(url = url))
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
                        columns = GridCells.Fixed(MediaGridColumnCount),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        fullLineItem {
                            ProfileHeader(
                                profile = content,
                                onClickFollow = uiState.listener::onClickFollow,
                            )
                        }
                        fullLineItem {
                            ProfileTabRow(
                                selectedTab = content.selectedTab,
                                onClickTab = uiState.listener::onClickTab,
                            )
                        }
                        when (content.selectedTab) {
                            UserProfileScreenUiState.Tab.Posts -> {
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

                            UserProfileScreenUiState.Tab.Media -> {
                                val mediaEmptyMessage = content.mediaEmptyMessage
                                if (mediaEmptyMessage != null) {
                                    fullLineItem { EmptyMessage(text = mediaEmptyMessage) }
                                }
                                items(content.media) { media ->
                                    MediaGridItem(media = media, modifier = Modifier.padding(1.dp))
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
}

private fun LazyGridScope.fullLineItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun ProfileTabRow(
    selectedTab: UserProfileScreenUiState.Tab,
    onClickTab: (UserProfileScreenUiState.Tab) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        UserProfileScreenUiState.Tab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onClickTab(tab) },
                text = {
                    Text(
                        text = when (tab) {
                            UserProfileScreenUiState.Tab.Posts -> "投稿"
                            UserProfileScreenUiState.Tab.Media -> "メディア"
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MediaGridItem(
    media: UserProfileScreenUiState.Media,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (media.blurred) {
            // Modifier.blur は Android 11 以下で効かないので、小さく読み込んで引き伸ばしたぼやけでも隠す。
            // 通常の大きさの画像がキャッシュから使われると隠れないので、キャッシュを分けて大きさも固定する
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(media.url)
                    .memoryCacheKey("blurred:${media.url}")
                    .size(BlurredMediaLoadSizePx)
                    .precision(Precision.EXACT)
                    .build(),
                contentDescription = null,
                modifier = Modifier.matchParentSize().blur(32.dp),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = "閲覧注意",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        } else {
            AsyncImage(
                model = media.url,
                contentDescription = media.description,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
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
    }
}

private const val MediaGridColumnCount = 3
private const val BlurredMediaLoadSizePx = 24
