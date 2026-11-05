package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.io.encoding.Base64
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
    data class Home(
        val tab: Tab,
    ) : Screen {
        override val pathPattern: PathPattern get() = tab.pathPattern
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen? get() = null
        override val loginRequired: Boolean get() = true

        enum class Tab(
            val pathPattern: PathPattern,
        ) {
            Timeline(PathPattern("/home")),
            Search(PathPattern("/search")),
            User(PathPattern("/profile")),
        }

        companion object {
            val Timeline: Home = Home(tab = Tab.Timeline)
        }
    }

    @Serializable
    data object Post : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/post")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home.Timeline
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Settings : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/settings")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home.Timeline
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Security : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/settings/security")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Settings
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object ProfileEdit : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/settings/profile")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home.Timeline
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Following : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/profile/following")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home(tab = Home.Tab.User)
        override val loginRequired: Boolean get() = true
    }

    @Serializable
    data object Followers : Screen {
        override val pathPattern: PathPattern get() = PathPattern("/profile/followers")
        override val pathParameters: Map<String, String> get() = mapOf()
        override val parentScreen: Screen get() = Home(tab = Home.Tab.User)
        override val loginRequired: Boolean get() = true
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

    /**
     * 開いていた画面に重ねて出す。画像の URL は `/` などを含むので、パスの 1 階層に収まるよう Base64 にする
     */
    @Serializable
    data class Image(
        val url: String,
    ) : Screen {
        override val pathPattern: PathPattern get() = PATH_PATTERN
        override val pathParameters: Map<String, String> get() = mapOf(ENCODED_URL_PARAMETER to UrlBase64.encode(url.encodeToByteArray()))
        override val parentScreen: Screen get() = Home.Timeline
        override val loginRequired: Boolean get() = false

        companion object {
            const val ENCODED_URL_PARAMETER: String = "encodedUrl"
            val PATH_PATTERN: PathPattern = PathPattern("/image/{$ENCODED_URL_PARAMETER}")
            private val UrlBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

            fun fromEncodedUrl(encodedUrl: String): Image? {
                val url = runCatching { UrlBase64.decode(encodedUrl).decodeToString() }.getOrNull()
                return if (url == null) null else Image(url = url)
            }
        }
    }

    companion object {
        private val routes: List<Pair<PathPattern, (Map<String, String>) -> Screen?>> by lazy {
            listOf<Pair<PathPattern, (Map<String, String>) -> Screen?>>(
                Login.pathPattern to { _ -> Login },
                Post.pathPattern to { _ -> Post },
                Settings.pathPattern to { _ -> Settings },
                Security.pathPattern to { _ -> Security },
                ProfileEdit.pathPattern to { _ -> ProfileEdit },
                Following.pathPattern to { _ -> Following },
                Followers.pathPattern to { _ -> Followers },
                UserProfile.PATH_PATTERN to { parameters ->
                    UserProfile(username = parameters.getValue(UserProfile.USERNAME_PARAMETER))
                },
                Image.PATH_PATTERN to { parameters ->
                    Image.fromEncodedUrl(parameters.getValue(Image.ENCODED_URL_PARAMETER))
                },
            ) + Home.Tab.entries.map { tab -> tab.pathPattern to { _ -> Home(tab = tab) } }
        }

        fun fromPath(path: String): Screen? {
            return routes.firstNotNullOfOrNull { (pattern, screenOf) ->
                pattern.match(path)?.let(screenOf)
            }
        }
    }
}
