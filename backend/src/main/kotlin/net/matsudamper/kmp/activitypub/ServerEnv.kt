package net.matsudamper.kmp.activitypub

import java.nio.file.Path
import kotlin.io.path.Path

class ServerEnv(
    env: Map<String, String> = System.getenv(),
) {
    val host: String = env["HOST"] ?: "0.0.0.0"
    val port: Int = env["PORT"]?.toIntOrNull() ?: 8080
    val staticSrcDir: Path? = env["STATIC_SRC_DIR"]?.let { Path(it) }
}
