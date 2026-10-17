package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.matsudamper.kmp.activitypub.frontend.core.PathPattern

@Serializable
sealed interface Screen : NavKey {
    val path: String

    /**
     * path で直接開いたとき、アプリの中で戻ると出す画面
     */
    val parentScreen: Screen?

    val loginRequired: Boolean

    @Serializable
    data object Login : Screen {
        override val path: String get() = "/"
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = false
    }

    @Serializable
    data object Home : Screen {
        override val path: String get() = "/home"
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Post : Screen {
        override val path: String get() = "/post"
        override val parentScreen: Screen get() = Home
        override val loginRequired: Boolean get() = true
    }

    /**
     * Mastodon に合わせたパス。
     */
    @Serializable
    data class UserProfile(
        val username: String,
    ) : Screen {
        override val path: String get() = USER_PROFILE_PATH.pathOf(mapOf(USERNAME_PARAMETER to username))
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = false
    }

    companion object {
        private const val USERNAME_PARAMETER: String = "username"
        private val USER_PROFILE_PATH = PathPattern("/@{$USERNAME_PARAMETER}")

        private val routes: List<Pair<PathPattern, (Map<String, String>) -> Screen>> =
            listOf(
                PathPattern(Login.path) to { _ -> Login },
                PathPattern(Home.path) to { _ -> Home },
                PathPattern(Post.path) to { _ -> Post },
                USER_PROFILE_PATH to { parameters -> UserProfile(username = parameters.getValue(USERNAME_PARAMETER)) },
            )

        fun fromPath(path: String): Screen? {
            return routes.firstNotNullOfOrNull { (pattern, screenOf) ->
                pattern.match(path)?.let(screenOf)
            }
        }
    }
}
