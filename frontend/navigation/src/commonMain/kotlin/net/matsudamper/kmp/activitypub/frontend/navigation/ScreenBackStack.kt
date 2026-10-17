package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * 画面回転などで Activity が作り直されても、積んである画面を復元する
 */
@Composable
fun rememberScreenBackStack(startScreens: List<Screen>): NavBackStack<NavKey> {
    return rememberNavBackStack(ScreenSavedStateConfiguration, *startScreens.toTypedArray())
}

/**
 * Android 以外ではリフレクションで NavKey の実装を探せないので、保存できる画面をここで登録する
 */
private val ScreenSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Screen.Login::class)
            subclass(Screen.Home::class)
            subclass(Screen.Post::class)
        }
    }
}
