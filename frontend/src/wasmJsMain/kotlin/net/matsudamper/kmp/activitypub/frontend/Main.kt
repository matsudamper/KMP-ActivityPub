package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import kotlinx.browser.window
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.TransparentScreen
import net.matsudamper.kmp.activitypub.frontend.navigation.TransparentScreenSceneStrategy
import net.matsudamper.kmp.activitypub.frontend.navigation.WasmNavigator
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberNavController
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenStateStore
import net.matsudamper.kmp.activitypub.frontend.screen.NotFoundScreen
import net.matsudamper.kmp.activitypub.frontend.screen.ScreenPlatform
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminAccountNewScreen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminAccountsScreen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminScreen
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.ui.AppTheme
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
 * WebGL2 でコンテキストを作れるかを調べる。
 *
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

/**
 * 画面の入口。URL に対応する画面を 1 つ出す。
 *
 * 遷移は Navigation 3 の [NavDisplay] に任せ、バックスタックは
 * ブラウザの履歴に合わせたものを渡す。
 */
@Composable
fun App() {
    AppTheme {
        val platformNavController = rememberNavController()
        val screenStateStore = rememberScreenStateStore()
        val navController: Navigator = remember(platformNavController) {
            WasmNavigator(platformNavController)
        }

        NavDisplay(
            backStack = platformNavController.backStack,
            onBack = { platformNavController.back() },
            sceneStrategies = listOf(TransparentScreenSceneStrategy()),
            entryProvider = { historyEntry ->
                NavEntry(
                    key = historyEntry,
                    contentKey = historyEntry.id,
                    metadata = if (historyEntry.screen is Screen.Overlay) TransparentScreen.asMetadata() else mapOf(),
                ) {
                    screenStateStore.Provide(historyEntry.id) { _ ->
                        ScreenContent(
                            screen = historyEntry.screen,
                            navController = navController,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    navController: Navigator,
) {
    when (screen) {
        Screen.Home -> HomeScreen(
            api = remember { UserApi(GraphQlClient.apollo) },
        )

        Screen.Admin -> AdminScreen(
            platform = WasmScreenPlatform,
            navController = navController,
        )

        Screen.AdminAccounts -> AdminAccountsScreen(navController = navController)

        Screen.AdminAccountNew -> AdminAccountNewScreen(navController = navController)

        is Screen.NotFound -> NotFoundScreen(
            requestedPath = screen.path,
            navController = navController,
        )
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
