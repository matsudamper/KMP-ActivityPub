package net.matsudamper.kmp.activitypub.android.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenMode
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenSettingStore

/**
 * アプリ内のリンクはすべて LocalUriHandler から開くので、ここで設定の開き方にそろえる
 */
@Composable
internal fun rememberLinkOpenUriHandler(linkOpenSettingStore: LinkOpenSettingStore): UriHandler {
    val context = LocalContext.current
    val linkOpenModeState = linkOpenSettingStore.linkOpenModeFlow.collectAsState(initial = LinkOpenMode.CustomTab)
    return remember(context, linkOpenModeState) {
        LinkOpenUriHandler(context = context, linkOpenModeState = linkOpenModeState)
    }
}

private class LinkOpenUriHandler(
    private val context: Context,
    private val linkOpenModeState: State<LinkOpenMode>,
) : UriHandler {
    override fun openUri(uri: String) {
        when (linkOpenModeState.value) {
            LinkOpenMode.ExternalBrowser -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            }

            LinkOpenMode.CustomTab -> {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(uri))
            }
        }
    }
}
