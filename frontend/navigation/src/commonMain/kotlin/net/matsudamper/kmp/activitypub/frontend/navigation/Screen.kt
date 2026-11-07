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
     * @param postId ホームタイムラインの投稿の id
     */
    @Serializable
    data class PostDetail(
        val postId: String,
    ) : Screen {
        override val pathPattern: PathPattern get() = PATH_PATTERN
        override val pathParameters: Map<String, String> get() = mapOf(POST_ID_PARAMETER to postId)
        override val parentScreen: Screen get() = Home.Timeline
        override val loginRequired: Boolean get() = true

        companion object {
            const val POST_ID_PARAMETER: String = "postId"
            val PATH_PATTERN: PathPattern = PathPattern("/posts/{$POST_ID_PARAMETER}")
        }
    }

    /**
     * 開いていた画面に重ねて出す。画像の URL は `/` などを含むので、まとめてパスの 1 階層に収まるよう Base64 にする
     *
     * @param urls 左右に送って見られる画像。URL は改行を含まないので改行でつないで 1 つにする
     * @param initialIndex 最初に見せる [urls] の位置
     */
    @Serializable
    data class Image(
        val urls: List<String>,
        val initialIndex: Int,
    ) : Screen {
        override val pathPattern: PathPattern get() = PATH_PATTERN
        override val pathParameters: Map<String, String>
            get() = mapOf(
                INITIAL_INDEX_PARAMETER to initialIndex.toString(),
                ENCODED_URLS_PARAMETER to UrlBase64.encode(urls.joinToString(URL_SEPARATOR).encodeToByteArray()),
            )
        override val parentScreen: Screen get() = Home.Timeline
        override val loginRequired: Boolean get() = false

        companion object {
            const val INITIAL_INDEX_PARAMETER: String = "initialIndex"
            const val ENCODED_URLS_PARAMETER: String = "encodedUrls"
            val PATH_PATTERN: PathPattern = PathPattern("/image/{$INITIAL_INDEX_PARAMETER}/{$ENCODED_URLS_PARAMETER}")
            private const val URL_SEPARATOR = "\n"
            private val UrlBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

            fun fromPathParameters(
                initialIndex: String,
                encodedUrls: String,
            ): Image? {
                val urls = runCatching { UrlBase64.decode(encodedUrls).decodeToString() }.getOrNull()
                    ?.split(URL_SEPARATOR)
                val index = initialIndex.toIntOrNull()
                return if (urls == null || index == null || index !in urls.indices) {
                    null
                } else {
                    Image(urls = urls, initialIndex = index)
                }
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
                PostDetail.PATH_PATTERN to { parameters ->
                    PostDetail(postId = parameters.getValue(PostDetail.POST_ID_PARAMETER))
                },
                Image.PATH_PATTERN to { parameters ->
                    Image.fromPathParameters(
                        initialIndex = parameters.getValue(Image.INITIAL_INDEX_PARAMETER),
                        encodedUrls = parameters.getValue(Image.ENCODED_URLS_PARAMETER),
                    )
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
