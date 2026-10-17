package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.matsudamper.kmp.activitypub.frontend.core.PathPattern

@Serializable
sealed interface Screen : NavKey {
    val pathPattern: PathPattern

    val pathParameters: Map<String, String>

    val path: String get() = pathPattern.pathOf(pathParameters)

    /**
     * path で直接開いたとき、アプリの中で戻ると出す画面
     */
    val parentScreen: Screen?

    val loginRequired: Boolean

    @Serializable
    data object Login : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = false
    }

    @Serializable
    data object Home : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/home")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Post : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/post")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Settings : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/settings")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home
        override val loginRequired: Boolean get() = true
    }

    /**
     * ログインと、管理画面の中の各画面への入口だけを置く。操作そのものは
     * 下の階層に分ける。1 つの画面に並べると、開いた時点で必要のない
     * 問い合わせまで走り、URL でその操作を指せなくなる。
     *
     * 管理画面のログインはユーザーのログインと別なので、画面の中で行う
     */
    @Serializable
    data object Admin : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/admin")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = false
    }

    @Serializable
    data object AdminAccounts : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/admin/accounts")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Admin
        override val loginRequired: Boolean get() = false
    }

    @Serializable
    data object AdminAccountNew : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/admin/accounts/new")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = AdminAccounts
        override val loginRequired: Boolean get() = false
    }

    /**
     * Mastodon に合わせたパス。
     */
    @Serializable
    data class UserProfile(
        val username: String,
    ) : Screen {
        override val pathPattern: PathPattern get() = PATH_PATTERN
        override val pathParameters: Map<String, String> get() = mapOf(USERNAME_PARAMETER to username)
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = false

        companion object {
            const val USERNAME_PARAMETER: String = "username"
            val PATH_PATTERN: PathPattern = PathPattern("/@{$USERNAME_PARAMETER}")
        }
    }

    companion object {
        private val routes: List<Pair<PathPattern, (Map<String, String>) -> Screen>> =
            listOf(
                Login.pathPattern to { _ -> Login },
                Home.pathPattern to { _ -> Home },
                Post.pathPattern to { _ -> Post },
                Settings.pathPattern to { _ -> Settings },
                Admin.pathPattern to { _ -> Admin },
                AdminAccounts.pathPattern to { _ -> AdminAccounts },
                AdminAccountNew.pathPattern to { _ -> AdminAccountNew },
                UserProfile.PATH_PATTERN to { parameters ->
                    UserProfile(username = parameters.getValue(UserProfile.USERNAME_PARAMETER))
                },
            )

        fun fromPath(path: String): Screen? {
            return routes.firstNotNullOfOrNull { (pattern, screenOf) ->
                pattern.match(path)?.let(screenOf)
            }
        }
    }
}
