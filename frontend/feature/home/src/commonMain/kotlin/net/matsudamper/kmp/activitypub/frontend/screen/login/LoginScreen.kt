package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen

@Composable
internal fun LoginScreen(
    navigator: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = rememberSaveable(saver = LoginScreenViewModel.saver(viewModelScope)) {
        LoginScreenViewModel(viewModelScope = viewModelScope, savedState = LoginScreenViewModel.InitialState)
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

    LoginContent(uiState = uiState)
}

@Composable
internal fun LoginContent(
    uiState: LoginScreenUiState,
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
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = uiState.listener::onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ユーザー名") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = uiState.listener::onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("パスワード") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { uiState.listener.onClickLogin() }),
                )
                Button(
                    onClick = uiState.listener::onClickLogin,
                    enabled = uiState.loginButtonEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("ログイン")
                }
            }
        }
    }
}
