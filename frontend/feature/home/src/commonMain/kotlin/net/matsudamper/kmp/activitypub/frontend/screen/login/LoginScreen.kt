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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.FormSubmitHandler
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldAutofill
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun LoginScreen(
    navigator: Navigator,
    serverSettingUiState: ServerSettingUiState?,
) {
    val viewModel = koinViewModel<LoginScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : LoginScreenViewModel.Event {
                override fun loggedIn() {
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
                LoginForm(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (serverSettingUiState != null) {
                    ServerSettingContent(uiState = serverSettingUiState)
                }
            }
        }
    }
}

@Composable
internal fun LoginForm(
    uiState: LoginScreenUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ログイン",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        SingleLineTextField(
            value = uiState.username,
            onValueChange = uiState.listener::onUsernameChanged,
            onImeAction = {},
            label = "ユーザー名",
            kind = SingleLineTextFieldKind.Plain,
            autofill = SingleLineTextFieldAutofill.Username,
            formId = LOGIN_FORM_ID,
            required = true,
            imeAction = ImeAction.Next,
            inputId = LOGIN_USERNAME_INPUT_ID,
            inputName = "username",
            enabled = uiState.inputEnabled,
            hasError = uiState.inputHasError,
            modifier = Modifier.fillMaxWidth(),
        )
        FormSubmitHandler(
            formId = LOGIN_FORM_ID,
            onSubmit = uiState.listener::onClickLogin,
            enabled = uiState.inputEnabled,
        )
        SingleLineTextField(
            value = uiState.password,
            onValueChange = uiState.listener::onPasswordChanged,
            onImeAction = uiState.listener::onClickLogin,
            label = "パスワード",
            kind = SingleLineTextFieldKind.Secret,
            autofill = SingleLineTextFieldAutofill.CurrentPassword,
            formId = LOGIN_FORM_ID,
            required = true,
            imeAction = ImeAction.Done,
            inputId = LOGIN_PASSWORD_INPUT_ID,
            inputName = "password",
            enabled = uiState.inputEnabled,
            hasError = uiState.inputHasError,
            modifier = Modifier.fillMaxWidth(),
        )
        val error = uiState.error
        if (error != null) {
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
    }
}

private const val LOGIN_FORM_ID = "user-login-form"
private const val LOGIN_USERNAME_INPUT_ID = "user-login-username"
private const val LOGIN_PASSWORD_INPUT_ID = "user-login-password"
