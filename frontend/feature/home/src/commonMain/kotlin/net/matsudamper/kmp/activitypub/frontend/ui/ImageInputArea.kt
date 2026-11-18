package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [content] へのドロップや貼り付けの画像や動画を [ImageInputReceiver] に渡す。受け取り手が無い間と、画像や動画でないものは邪魔しない。
 * 投稿画面を開いている間はどこに落としても添付できるよう、画面の全体を覆うところに置く
 */
@Composable
internal expect fun ImageInputArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
