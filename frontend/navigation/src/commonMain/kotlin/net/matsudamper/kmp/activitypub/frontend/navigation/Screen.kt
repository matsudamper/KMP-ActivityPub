package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    val path: String

    /**
     * path で直接開いたとき、アプリの中で戻ると出す画面
     */
    val parentScreen: Screen?

    @Serializable
    data object Login : Screen {
        override val path: String get() = "/"
        override val parentScreen: Screen? get() = null
    }

    @Serializable
    data object Home : Screen {
        override val path: String get() = "/home"
        override val parentScreen: Screen? get() = null
    }

    @Serializable
    data object Post : Screen {
        override val path: String get() = "/post"
        override val parentScreen: Screen get() = Home
    }

    companion object {
        fun fromPath(path: String): Screen? = listOf(Login, Home, Post).firstOrNull { it.path == path }
    }
}
