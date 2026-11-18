package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 動画を全画面で再生する。プレイヤーを各プラットフォームに橋渡しせず、HTML の video 要素で再生する
 */
@Composable
internal expect fun VideoPlayerDialog(
    videoUrl: String,
    onDismissRequest: () -> Unit,
)
