package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 投稿の本文を選択してコピーできるようにする。
 * contentPadding は選択できる範囲の内側に取るので、余白から押し始めても選択できる
 */
@Composable
internal expect fun PostBodySelectionContainer(
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
)
