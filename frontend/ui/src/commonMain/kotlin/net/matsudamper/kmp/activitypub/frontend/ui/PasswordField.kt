package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    purpose: PasswordPurpose,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
)

/**
 * パスワードマネージャーに渡す用途。登録画面で [Current] にすると、
 * 保存済みの管理者のパスワードが候補に出てそのまま新しいユーザーに設定され得る
 */
enum class PasswordPurpose {
    Current,
    New,
}
