package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator

/**
 * ログインが確かめられるまで [content] を出さない。ログインしていないときの扱いはプラットフォームごとに違う
 */
@Composable
internal expect fun LoginGate(
    navigator: Navigator,
    content: @Composable () -> Unit,
)
