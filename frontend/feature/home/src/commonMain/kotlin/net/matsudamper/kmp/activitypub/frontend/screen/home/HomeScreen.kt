package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.ui.PasswordField
import net.matsudamper.kmp.activitypub.frontend.ui.PasswordPurpose
import net.matsudamper.kmp.activitypub.frontend.ui.RetainedScreenState
import net.matsudamper.kmp.activitypub.frontend.ui.ScaffoldSnackbarHost
import net.matsudamper.kmp.activitypub.frontend.ui.SnackbarHostState
import net.matsudamper.kmp.activitypub.frontend.ui.SnackbarMaxWidth
import net.matsudamper.kmp.activitypub.frontend.ui.rememberRetained
import net.matsudamper.kmp.activitypub.frontend.ui.rememberSnackbarHostState

@Composable
fun HomeScreen(
    retainedScreenState: RetainedScreenState,
    api: UserApi,
) {
    val viewModel = rememberRetained(retainedScreenState) { viewModelScope ->
        HomeScreenViewModel(viewModelScope = viewModelScope, api = api)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = rememberSnackbarHostState()

    LaunchedEffect(viewModel.eventHandler, snackbarHostState) {
        viewModel.eventHandler.collect(
            object : HomeScreenViewModel.Event {
                override fun showSnackbar(message: String) {
                    snackbarHostState.show(message)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun HomeContent(
    uiState: HomeScreenUiState,
    snackbarHostState: SnackbarHostState = rememberSnackbarHostState(),
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 1 カラムも並ばない幅では、カラムを画面いっぱいに広げる
            val columnWidth = if (maxWidth < ColumnWidth + ColumnSpacing * 2) maxWidth else ColumnWidth
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (columnWidth == maxWidth) 0.dp else ColumnSpacing),
                horizontalArrangement = Arrangement.spacedBy(ColumnSpacing),
            ) {
                ColumnFrame(
                    title = "投稿",
                    modifier = Modifier.width(columnWidth).fillMaxHeight(),
                ) {
                    ComposeColumnContent(
                        column = uiState.composeColumn,
                        listener = uiState.listener,
                    )
                }
            }

            ScaffoldSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .widthIn(max = SnackbarMaxWidth)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnFrame(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ComposeColumnContent(
    column: HomeScreenUiState.ComposeColumn,
    listener: HomeScreenUiState.Listener,
) {
    when (column) {
        HomeScreenUiState.ComposeColumn.Loading -> {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is HomeScreenUiState.ComposeColumn.Error -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(column.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = listener::onClickRetry) { Text("もう一度試す") }
            }
        }

        is HomeScreenUiState.ComposeColumn.Login -> LoginForm(column = column, listener = listener)

        is HomeScreenUiState.ComposeColumn.Compose -> ComposeForm(column = column, listener = listener)
    }
}

@Composable
private fun LoginForm(
    column: HomeScreenUiState.ComposeColumn.Login,
    listener: HomeScreenUiState.Listener,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ログインすると投稿できる。")
        OutlinedTextField(
            value = column.username,
            onValueChange = listener::onUsernameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ユーザー名") },
            singleLine = true,
            enabled = column.inputEnabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = column.error != null,
        )
        PasswordField(
            value = column.password,
            onValueChange = listener::onPasswordChanged,
            onSubmit = listener::onClickLogin,
            label = "パスワード",
            formId = LOGIN_FORM_ID,
            inputId = LOGIN_PASSWORD_INPUT_ID,
            inputName = "password",
            purpose = PasswordPurpose.Current,
            enabled = column.inputEnabled,
            hasError = column.error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        column.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = listener::onClickLogin,
            enabled = column.loginButtonEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (column.submitting) "確認中..." else "ログイン")
        }
    }
}

@Composable
private fun ComposeForm(
    column: HomeScreenUiState.ComposeColumn.Compose,
    listener: HomeScreenUiState.Listener,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = column.acct,
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = listener::onClickLogout) { Text("ログアウト") }
        }
        OutlinedTextField(
            value = column.body,
            onValueChange = listener::onBodyChanged,
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            placeholder = { Text("今なにしてる？") },
            enabled = column.bodyInputEnabled,
            isError = column.error != null,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = column.remainingLength.toString(),
                modifier = Modifier.weight(1f),
                color = if (column.remainingLengthOver) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Button(
                onClick = listener::onClickPost,
                enabled = column.postButtonEnabled,
            ) {
                Text(if (column.submitting) "投稿中..." else "投稿")
            }
        }
        column.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private val ColumnWidth = 360.dp
private val ColumnSpacing = 8.dp

private const val LOGIN_FORM_ID = "user-login-form"
private const val LOGIN_PASSWORD_INPUT_ID = "user-login-password"
