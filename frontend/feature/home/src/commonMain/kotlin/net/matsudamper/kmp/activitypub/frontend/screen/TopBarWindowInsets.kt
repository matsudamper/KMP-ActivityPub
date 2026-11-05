package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * 上のバーはステータスバーの裏まで背景を伸ばし、中身だけを避けさせる。横向きでは左右のカットアウトも避ける
 */
internal val topBarWindowInsets: WindowInsets
    @Composable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
