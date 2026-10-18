package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.Path
import net.matsudamper.kmp.activitypub.crypto.PasswordHash

/**
 * 空白だけの値は未設定として扱う。STATIC_SRC_DIR が空だとカレントディレクトリを配信してしまう
 */
class ServerEnv(
    env: Map<String, String> = System.getenv(),
) {
    private val values = env.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

    val host: String = values["HOST"] ?: "0.0.0.0"
    val port: Int = values["PORT"]?.toInt() ?: 8080
    val staticSrcDir: Path? = values["STATIC_SRC_DIR"]?.let { Path(it) }
    val dbPath: Path = Path(values["DB_PATH"] ?: "./data/kmp-activitypub.db")

    /**
     * `@user@example.com` の example.com。未設定なら他のサーバーから見つけられない。
     * Mastodon はリモートアクターを永続キャッシュするので、間違えると相手側からは直せない。
     */
    val domain: String? =
        values["DOMAIN"]
            // https://example.com/ のような URL ごと渡されることも考えて scheme と末尾の / を落とす
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.trimEnd('/')
            ?.lowercase()
            ?.also { normalized ->
                // パスやクエリが残るとアクター ID や WebFinger の acct が壊れたまま配られる
                val hostName = normalized.substringBefore(':')
                val port = normalized.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
                val isValidHostName = hostName.length <= 253 && hostName.split('.').all { it.length <= 63 }
                require(DOMAIN_PATTERN.matches(normalized) && isValidHostName && (port == null || port.toInt() in 1..65535)) {
                    "DOMAIN はホスト名（ポートは付けてよい）だけにすること: $normalized"
                }
            }

    val adminPasswordHash: PasswordHash? = values["ADMIN_PASSWORD_HASH"]?.let { PasswordHash.parse(it) }

    /**
     * Valkey を使わないテストでも ServerEnv を作れるよう、読んだときに検査する
     */
    val valkeyUri: String by lazy { checkNotNull(values["VALKEY_URI"]) { "VALKEY_URI を指定すること" } }

    /**
     * true のまま http で開くとログインできない
     */
    val cookieSecure: Boolean = booleanOf("COOKIE_SECURE") ?: true

    /**
     * Cloudflare の後ろに置くと接続元が Cloudflare の IP になるので、CF-Connecting-IP を接続元として使う。
     * Cloudflare を通らずに直接届く経路があると、ヘッダーを偽って他人の IP を名乗れる
     */
    val trustCfConnectingIp: Boolean = booleanOf("TRUST_CF_CONNECTING_IP") ?: false

    private fun booleanOf(name: String): Boolean? =
        values[name]?.let { raw ->
            raw.lowercase().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("$name は true か false にすること: $raw")
        }

    private companion object {
        val DOMAIN_PATTERN = Regex(
            """^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*(?::[1-9][0-9]{0,4})?$""",
        )
    }
}
