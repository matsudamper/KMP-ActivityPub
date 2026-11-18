package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 動画の 1 コマ目をサムネイルとして出す。出せないプラットフォームでは何も出さない
 */
@Composable
internal expect fun VideoFirstFrame(
    videoUrl: String,
    modifier: Modifier,
)
