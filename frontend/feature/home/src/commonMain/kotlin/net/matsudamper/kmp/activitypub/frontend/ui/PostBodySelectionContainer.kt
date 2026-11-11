package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 投稿の本文を選択してコピーできるようにする
 */
@Composable
internal expect fun PostBodySelectionContainer(content: @Composable () -> Unit)
