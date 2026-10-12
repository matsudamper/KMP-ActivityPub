package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.PasswordField
import net.matsudamper.kmp.activitypub.frontend.ui.UsernameField

@Composable
internal fun LoginScreen(
    navigator: Navigator,
    api: UserApi?,
    serverSettingUiState: ServerSettingUiState?,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = rememberSaveable(saver = LoginScreenViewModel.saver(viewModelScope, api)) {
        LoginScreenViewModel(viewModelScope = viewModelScope, api = api, savedState = LoginScreenViewModel.InitialState)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : LoginScreenViewModel.Event {
                override fun navigateToHome() {
                    navigator.replaceAll(Screen.Home)
                }
            },
        )
    }

    LoginContent(uiState = uiState, serverSettingUiState = serverSettingUiState)
}

@Composable
internal fun LoginContent(
    uiState: LoginScreenUiState,
    serverSettingUiState: ServerSettingUiState?,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "ログイン",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                UsernameField(
                    value = uiState.username,
                    onValueChange = uiState.listener::onUsernameChanged,
                    label = "ユーザー名",
                    formId = LOGIN_FORM_ID,
                    inputId = LOGIN_USERNAME_INPUT_ID,
                    inputName = "username",
                    enabled = uiState.inputEnabled,
                    hasError = uiState.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = uiState.password,
                    onValueChange = uiState.listener::onPasswordChanged,
                    onSubmit = uiState.listener::onClickLogin,
                    label = "パスワード",
                    formId = LOGIN_FORM_ID,
                    inputId = LOGIN_PASSWORD_INPUT_ID,
                    inputName = "password",
                    enabled = uiState.inputEnabled,
                    hasError = uiState.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = uiState.listener::onClickLogin,
                    enabled = uiState.loginButtonEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("ログイン")
                }
                if (serverSettingUiState != null) {
                    ServerSettingContent(uiState = serverSettingUiState)
                }
            }
        }
    }
}

private const val LOGIN_FORM_ID = "user-login-form"
private const val LOGIN_USERNAME_INPUT_ID = "user-login-username"
private const val LOGIN_PASSWORD_INPUT_ID = "user-login-password"
