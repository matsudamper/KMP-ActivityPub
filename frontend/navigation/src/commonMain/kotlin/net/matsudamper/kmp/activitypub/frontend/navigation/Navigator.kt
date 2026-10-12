package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {
    fun navigate(screen: Screen)

    /**
     * 積んである画面を捨てて [screen] だけにする。ログイン後に戻るでログイン画面へ戻れないようにする
     */
    fun replaceAll(screen: Screen)

    fun back()
}
