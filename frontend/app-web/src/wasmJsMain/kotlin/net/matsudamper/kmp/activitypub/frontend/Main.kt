package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.browser.window
import net.matsudamper.kmp.activitypub.frontend.history.BrowserHistory
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeNavigationHost
import net.matsudamper.kmp.activitypub.frontend.screen.ScreenPlatform
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminAccountNewScreen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminAccountsScreen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminScreen
import net.matsudamper.kmp.activitypub.frontend.ui.AppTheme
import net.matsudamper.kmp.activitypub.frontend.ui.RetainedScreenState
import org.koin.compose.KoinApplication
import org.koin.plugin.module.dsl.koinConfiguration
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body!!
    if (canUseWebGl2().not()) {
        body.appendChild(createWebGl2UnavailableNotice())
        return
    }

    ComposeViewport(body) {
        App()
    }
}

/**
 * 描画は Skia を WebGL2 の上で動かしていて、WebGL1 にもソフトウェア描画にも
 * 代替経路が無い。使えない状態で起動すると描画の初期化が例外になり、
 * 何も出ないまま終わる。
 *
 * ブラウザが WebGL2 に対応していても、GPU がブロックリストに掛かっていたり
 * リモートデスクトップ越しで仮想ディスプレイアダプターになっていると作れない。
 * 対応の有無ではなく実際に作って確かめる。
 *
 * 作れたコンテキストはその場で手放す。同時に持てる数をブラウザが制限していて、
 * 参照を捨てただけではいつ解放されるか決まらない。残り 1 つの環境では、この直後に
 * Compose が作るコンテキストが上限に掛かり、避けたかった白い画面になる。
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun canUseWebGl2(): Boolean = js(
    """{
        const canvas = document.createElement('canvas');
        const context = canvas.getContext('webgl2');
        if (context === null) return false;
        const loseContext = context.getExtension('WEBGL_lose_context');
        if (loseContext !== null) loseContext.loseContext();
        return true;
    }""",
)

private fun createWebGl2UnavailableNotice(): HTMLElement {
    val notice = document.createElement("div") as HTMLElement
    notice.setAttribute(
        "style",
        "display:flex; flex-direction:column; gap:8px; align-items:center; justify-content:center;" +
            " height:100%; padding:16px; box-sizing:border-box; text-align:center;" +
            " font-family:sans-serif; color:#1b1b1b; background:#fdfdfd;",
    )

    val title = document.createElement("div") as HTMLElement
    title.setAttribute("style", "font-size:18px; font-weight:bold;")
    title.textContent = "画面を表示できません"

    val description = document.createElement("div") as HTMLElement
    description.setAttribute("style", "font-size:14px; line-height:1.6; max-width:32em;")
    description.textContent = "このブラウザーで WebGL2 が使えないため、描画を開始できませんでした。" +
        "ブラウザーを再起動すると直ることがあります。" +
        "直らない場合は、ハードウェアアクセラレーションを有効にするか、別のブラウザーでお試しください。"

    notice.appendChild(title)
    notice.appendChild(description)
    return notice
}

@Composable
private fun App() {
    KoinApplication(configuration = koinConfiguration<WebKoinApp>()) {
        AppNavigation()
    }
}

@Composable
private fun AppNavigation() {
    val backStack = rememberScreenBackStack(startScreens = BrowserHistory.initialScreens(unknownPathScreen = UNKNOWN_PATH_SCREEN))
    BrowserHistory.SyncEffect(backStack = backStack, unknownPathScreen = UNKNOWN_PATH_SCREEN)
    AppTheme {
        HomeNavigationHost(
            backStack = backStack,
            serverSettingUiState = { null },
            otherScreenContent = { screen, navigator ->
                AdminScreenContent(screen = screen, navigator = navigator)
            },
        )
    }
}

@Composable
private fun AdminScreenContent(
    screen: Screen,
    navigator: Navigator,
) {
    val retainedScreenState = viewModel { RetainedScreenStateViewModel() }.retainedScreenState
    when (screen) {
        Screen.Admin -> AdminScreen(
            retainedScreenState = retainedScreenState,
            platform = WasmScreenPlatform,
            navController = navigator,
        )

        Screen.AdminAccounts -> AdminAccountsScreen(
            retainedScreenState = retainedScreenState,
            navController = navigator,
        )

        Screen.AdminAccountNew -> AdminAccountNewScreen(
            retainedScreenState = retainedScreenState,
            navController = navigator,
        )

        else -> Unit
    }
}

/**
 * 画面の ViewModel の置き場を履歴のエントリごとに持たせる。エントリが積まれている間は残る
 */
private class RetainedScreenStateViewModel : ViewModel() {
    val retainedScreenState: RetainedScreenState = RetainedScreenState(viewModelScope)

    override fun onCleared() {
        retainedScreenState.dispose()
    }
}

private object WasmScreenPlatform : ScreenPlatform {
    override val host: String
        get() = window.location.host

    override fun openExternalLink(url: String) {
        net.matsudamper.kmp.activitypub.frontend.ui.openExternalLink(url)
    }

    override fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) {
        net.matsudamper.kmp.activitypub.frontend.ui.copyToClipboard(text, onResult)
    }
}

private val UNKNOWN_PATH_SCREEN: Screen = Screen.Login
