package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path

/**
 * @param path 親ディレクトリは接続時に作られる
 */
data class DatabaseConfig(
    val path: Path,
)
