package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.Path
import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kotpub.actor.ActorPrivateKey

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
     * 外部に公開するドメイン。WebFinger の `acct:` と Actor の `id` に使う。
     * Mastodon はリモートアクターを永続キャッシュするので、間違えると相手側からは直せない。
     */
    val domain: String =
        run {
            // アクター ID に焼き込まれる値なので、末尾の / は落としておく。
            // https://example.com/ のような URL ごと渡されることも考えて scheme も落とす
            val normalized =
                values["DOMAIN"]
                    ?.removePrefix("https://")
                    ?.removePrefix("http://")
                    ?.trimEnd('/')

            // 既定値を用意して起動できてしまうと、localhost のようなドメインが
            // 焼き込まれたアクター ID を配ることになる。Mastodon はリモートアクターを
            // 永続キャッシュするので、相手側からは直せない。落とす方が安い
            require(!normalized.isNullOrEmpty()) {
                "DOMAIN が未設定。WebFinger の acct とアクターの id に使うので必ず指定すること"
            }
            // パスやクエリが残るとアクター ID や WebFinger の acct が壊れたまま配られる
            val hostName = normalized.substringBefore(':')
            val port = normalized.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
            val isValidHostName = hostName.length <= 253 && hostName.split('.').all { it.length <= 63 }
            require(DOMAIN_PATTERN.matches(normalized) && isValidHostName && (port == null || port.toInt() in 1..65535)) {
                "DOMAIN はホスト名（ポートは付けてよい）だけにすること: $normalized"
            }
            normalized
        }

    val actorPrivateKey: ActorPrivateKey =
        run {
            // PEM は中身をそのまま鍵として読むので、前後の空白も落とさずに渡す
            val pem = env["ACTOR_PRIVATE_KEY_PEM"]?.takeIf { it.isNotBlank() }
            val path = values["ACTOR_PRIVATE_KEY_PATH"]

            // 片方を黙って無視すると、意図していない鍵で起動したことに気付けない。
            // 鍵が変わると相手側は署名検証に失敗し続けるうえ、Mastodon はアクターをキャッシュするので後から直しても戻りが遅い
            require(pem == null || path == null) {
                "ACTOR_PRIVATE_KEY_PEM と ACTOR_PRIVATE_KEY_PATH は同時に指定できない。どちらか一方にすること"
            }

            if (pem != null) {
                ActorPrivateKey.Pem(pem)
            } else {
                ActorPrivateKey.File(Path(path ?: "./data/actor-private-key.pem"))
            }
        }

    val adminPasswordHash: PasswordHash? = values["ADMIN_PASSWORD_HASH"]?.let { PasswordHash.parse(it) }

    /**
     * true のまま http で開くとログインできない
     */
    val cookieSecure: Boolean =
        values["COOKIE_SECURE"]?.let { raw ->
            raw.lowercase().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("COOKIE_SECURE は true か false にすること: $raw")
        } ?: true

    private companion object {
        val DOMAIN_PATTERN = Regex(
            """^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*(?::[1-9][0-9]{0,4})?$""",
        )
    }
}
