package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * 空文字と空白だけの指定は未設定と同じに扱う。docker compose で値が空のまま展開されることがあり、
 * STATIC_SRC_DIR が空だとカレントディレクトリをそのまま配信してしまう
 */
class ServerEnv(
    env: Map<String, String> = System.getenv(),
) {
    private val values = env.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

    val host: String = values["HOST"] ?: "0.0.0.0"
    // 数値でない値を既定値に落とすと、設定と違うポートで正常に起動したように見えてしまう
    val port: Int =
        values["PORT"]?.let { raw ->
            requireNotNull(raw.toIntOrNull()?.takeIf { it in 1..65535 }) { "PORT は 1〜65535 の数値にすること: $raw" }
        } ?: 8080
    val staticSrcDir: Path? = values["STATIC_SRC_DIR"]?.let { Path(it) }
}
