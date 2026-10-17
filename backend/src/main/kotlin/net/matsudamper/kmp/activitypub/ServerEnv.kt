package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.Path

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
    val valkeyUri: String = values["VALKEY_URI"] ?: "redis://localhost:6379"

    /**
     * true のまま http で開くとログインできない
     */
    val cookieSecure: Boolean =
        values["COOKIE_SECURE"]?.let { raw ->
            raw.lowercase().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("COOKIE_SECURE は true か false にすること: $raw")
        } ?: true
}
