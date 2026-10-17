package net.matsudamper.kmp.activitypub.repository

import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.repository.sqlite.SqliteRepositories

/**
 * JDBC と jOOQ をモジュールの外に出さないための入口
 */
interface Repositories : AutoCloseable {
    val accountRepository: AccountRepository

    /**
     * 閉じた後は DB のファイルをコピーしたり別のツールで編集したりしてよい
     */
    override fun close()

    companion object {
        fun create(
            config: DatabaseConfig,
            openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
        ): Repositories = SqliteRepositories(config = config, openTelemetry = openTelemetry)
    }
}
