package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.navigation3.runtime.NavKey

/**
 * 画面のパス。Navigation 3 のバックスタックに積むキーでもある。
 *
 * サーバーは自分が持つパス以外を全部 `index.html` に落とすので、どの画面を出すかは
 * ブラウザ側で決めることになる。判定をここに集めておかないと、リンクを張る側と
 * 画面を出す側で綴りがずれて「リンクは踏めるが真っ白になる」壊れ方をする。
 */
sealed interface Screen : NavKey {
    /** ブラウザのアドレスバーに出すパス */
    val path: String

    /** `document.title` に入れる文字列 */
    val title: String

    /**
     * 下に画面を敷いたまま重ねて出す画面。
     *
     * ダイアログも 1 つの画面として扱う。URL を持てるので直接開けるし、戻るで閉じられる。
     * 出している間も [background] は生きたままなので、閉じた後に作り直されない。
     */
    sealed interface Overlay : Screen {
        /**
         * URL を直接開いたときに下に敷く画面。アプリの中から開いたときは、開いた画面の上に重ねる
         */
        val background: Screen
    }

    /** トップ。Mastodon と同じくカラムを並べる */
    data object Home : Screen {
        override val path: String = "/"
        override val title: String = SITE_NAME
    }

    /**
     * 管理画面のトップ。
     *
     * ログインと、管理画面の中の各画面への入口だけを置く。操作そのものは
     * 下の階層に分ける。1 つの画面に並べると、開いた時点で必要のない
     * 問い合わせまで走り、URL でその操作を指せなくなる。
     */
    data object Admin : Screen {
        override val path: String = "/$ADMIN_SEGMENT"
        override val title: String = "管理画面 | $SITE_NAME"
    }

    /**
     * ユーザーの一覧
     */
    data object AdminAccounts : Screen {
        override val path: String = "/$ADMIN_SEGMENT/$ACCOUNTS_SEGMENT"
        override val title: String = "ユーザー | $SITE_NAME"
    }

    /**
     * ユーザーの登録
     */
    data object AdminAccountNew : Screen {
        override val path: String = "/$ADMIN_SEGMENT/$ACCOUNTS_SEGMENT/$NEW_SEGMENT"
        override val title: String = "ユーザーの登録 | $SITE_NAME"
    }

    /**
     * 知らないパス。
     *
     * [path] は要求されたパスのまま持つ。ここで `/` に書き換えると、
     * 戻るボタンで元のパスに戻れなくなる。
     */
    data class NotFound(
        override val path: String,
    ) : Screen {
        override val title: String = "見つからない | $SITE_NAME"
    }

    companion object {
        const val SITE_NAME: String = "KMP-ActivityPub"

        /**
         * 管理画面のパスの先頭
         */
        const val ADMIN_SEGMENT: String = "admin"

        private const val ACCOUNTS_SEGMENT: String = "accounts"

        private const val NEW_SEGMENT: String = "new"

        /**
         * `window.location.pathname` から画面を決める。
         *
         * @param path 先頭が `/` のパス。クエリとハッシュは含めない
         */
        fun of(path: String): Screen {
            val segments = path.split('/').filter { it.isNotEmpty() }

            val first = segments.firstOrNull() ?: return Home

            if (first == ADMIN_SEGMENT) {
                // 知らない下の階層は管理画面ではなく見つからない扱いにする。
                // 綴りを間違えたリンクで管理画面が出ると、間違いに気付けない
                return when (segments.drop(1)) {
                    listOf<String>() -> Admin
                    listOf(ACCOUNTS_SEGMENT) -> AdminAccounts
                    listOf(ACCOUNTS_SEGMENT, NEW_SEGMENT) -> AdminAccountNew
                    else -> NotFound(path)
                }
            }

            return NotFound(path)
        }
    }
}
