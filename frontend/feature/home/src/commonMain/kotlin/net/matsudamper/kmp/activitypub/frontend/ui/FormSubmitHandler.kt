package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * [SingleLineTextField] の formId が同じ入力欄を一つのフォームとして送信できるようにする
 */
@Composable
internal expect fun FormSubmitHandler(
    formId: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
)
