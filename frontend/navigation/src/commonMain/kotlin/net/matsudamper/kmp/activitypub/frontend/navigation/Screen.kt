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
     * Mastodon に合わせたパス。ActivityPub の Actor は /users/{username} に置くので分けている
     */
    @Serializable
    data class UserProfile(
        val username: String,
    ) : Screen {
        override val path: String get() = "$USER_PROFILE_PATH_PREFIX$username"
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = false
    }

    companion object {
        private const val USER_PROFILE_PATH_PREFIX: String = "/@"

        fun fromPath(path: String): Screen? {
            return listOf(Login, Home, Post).firstOrNull { it.path == path }
                ?: userProfileOf(path)
        }

        private fun userProfileOf(path: String): UserProfile? {
            val username = path.removePrefix(USER_PROFILE_PATH_PREFIX)
            if (username == path || username.isEmpty() || username.contains('/')) return null
            return UserProfile(username = username)
        }
    }
}
