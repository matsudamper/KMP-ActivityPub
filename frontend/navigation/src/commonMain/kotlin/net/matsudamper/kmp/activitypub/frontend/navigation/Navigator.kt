package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {
    suspend fun navigate(screen: Screen)

    /**
     * 1 つ前の画面に戻る。
     */
    suspend fun back()
}
