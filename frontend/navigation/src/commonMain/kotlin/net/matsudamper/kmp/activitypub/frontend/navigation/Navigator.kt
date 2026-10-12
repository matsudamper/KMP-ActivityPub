package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Stable

@Stable
interface Navigator {
    suspend fun navigate(screen: Screen)

    suspend fun back()
}
