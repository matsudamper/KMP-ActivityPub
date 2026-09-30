package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.AdminScaffold
import net.matsudamper.kmp.activitypub.frontend.ui.ContentMaxWidth
import net.matsudamper.kmp.activitypub.frontend.ui.PasswordField
import net.matsudamper.kmp.activitypub.frontend.ui.RetainedScreenState
import net.matsudamper.kmp.activitypub.frontend.ui.SectionCard
import net.matsudamper.kmp.activitypub.frontend.ui.rememberRetained

@Composable
fun AdminAccountNewScreen(
    retainedScreenState: RetainedScreenState,
    navController: Navigator,
) {
    val viewModel = rememberRetained(retainedScreenState) { viewModelScope ->
        AdminAccountNewScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminAccountNewScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminAccountNewContent(uiState = uiState)
}

@Composable
internal fun AdminAccountNewContent(
    uiState: AdminAccountNewScreenUiState,
) {
    AdminScaffold("ユーザーの登録", listener = uiState.listener) { wide ->
        Column(
            Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ユーザーの登録", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            when (val content = uiState.content) {
                AdminAccountNewScreenUiState.Content.Loading -> SectionCard("確認中") { Text("状態を確かめている。") }

                AdminAccountNewScreenUiState.Content.RequireLogin -> RequireLoginCard(onClickAdmin = uiState.listener::onClickAdmin)

                is AdminAccountNewScreenUiState.Content.Error -> SectionCard("状態が分からない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                }

                is AdminAccountNewScreenUiState.Content.Input -> InputCard(content, uiState.listener)
            }
        }
    }
}

@Composable
private fun InputCard(content: AdminAccountNewScreenUiState.Content.Input, listener: AdminAccountNewScreenUiState.Listener) {
    SectionCard("ユーザー") {
        Text("名前は後から変えられない。Mastodon は一度取得したアカウントを持ち続けるので、別の名前にしたい場合は作り直すことになる。")
        OutlinedTextField(
            value = content.username,
            onValueChange = listener::onUsernameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ユーザー名") },
            singleLine = true,
            enabled = content.inputEnabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = content.error != null,
        )
        PasswordField(
            value = content.password,
            onValueChange = listener::onPasswordChanged,
            onSubmit = listener::onClickAdd,
            label = "パスワード",
            formId = NEW_USER_FORM_ID,
            inputId = NEW_USER_PASSWORD_INPUT_ID,
            inputName = "password",
            enabled = content.inputEnabled,
            hasError = content.error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        content.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = listener::onClickAdd, enabled = content.addButtonEnabled) {
            Text(if (content.submitting) "登録中..." else "登録")
        }
    }
}

private const val NEW_USER_FORM_ID = "admin-new-user-form"
private const val NEW_USER_PASSWORD_INPUT_ID = "admin-new-user-password"
