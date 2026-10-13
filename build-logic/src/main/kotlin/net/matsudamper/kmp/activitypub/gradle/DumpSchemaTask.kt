package net.matsudamper.kmp.activitypub.gradle

import java.io.File
import java.sql.Connection
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * 開発用 DB から `schema.sql` を書き出す。
 *
 * 入力が Gradle の外にあるので、全プロパティを [Internal] にして毎回実行させる。
 */
abstract class DumpSchemaTask : DefaultTask() {
    @get:Internal
    abstract val devDatabasePath: Property<String>

    @get:Internal
    abstract val driverClasspath: ConfigurableFileCollection

    /**
     * jOOQ codegen の excludes と同じものを渡す
     */
    @get:Internal
    abstract val excludes: Property<String>

    @get:Internal
    abstract val schemaFile: RegularFileProperty

    @get:Internal
    abstract val workDatabase: RegularFileProperty

    @TaskAction
    fun dump() {
        val devDb = resolveDevDatabase()
        val excludePattern = Regex(excludes.get())

        val statements =
            withSqliteConnection(driverClasspath.files, devDb) { connection ->
                readSchemaStatements(connection, excludePattern)
            }

        check(statements.isNotEmpty()) {
            "開発用 DB ($devDb) から書き出せるテーブルが 1 つも無い"
        }

        val verified = roundTrip(statements, excludePattern)

        val target = schemaFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(
            buildString {
                appendLine(HEADER)
                verified.forEach { statement ->
                    appendLine(statement.trimEnd().removeSuffix(";") + ";")
                    appendLine()
                }
            }.trimEnd() + "\n",
        )

        logger.lifecycle("schema.sql を書き出した: $target")
    }

    private fun resolveDevDatabase(): File {
        val path =
            devDatabasePath.orNull
                ?: error(
                    "開発用 DB のパスが指定されていない。" +
                        "./gradlew ${this.path} -PdevDb=/絶対パス/dev.db の形で渡すこと",
                )

        val file = File(path)
        // Gradle ではカレントディレクトリが呼び出し場所で変わる
        require(file.isAbsolute) { "devDb は絶対パスで渡すこと: $path" }
        require(file.isFile) { "開発用 DB が見つからない: $path" }
        return file
    }

    private fun readSchemaStatements(
        connection: Connection,
        excludePattern: Regex,
    ): List<String> =
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    // インデックスや trigger はテーブルより後に作る
                    """
                    SELECT type, name, tbl_name, sql FROM sqlite_master
                    WHERE sql IS NOT NULL
                    ORDER BY
                      CASE type
                        WHEN 'table' THEN 0
                        WHEN 'index' THEN 1
                        WHEN 'view' THEN 2
                        ELSE 3
                      END,
                      name
                    """.trimIndent(),
                ).use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val name = resultSet.getString("name")
                            val tableName = resultSet.getString("tbl_name")
                            if (excludePattern.matches(name) || excludePattern.matches(tableName)) continue
                            add(resultSet.getString("sql"))
                        }
                    }
                }
        }

    /**
     * `schema.sql` が単体で適用できることを、使い捨ての DB に適用し直して確かめる
     */
    private fun roundTrip(
        statements: List<String>,
        excludePattern: Regex,
    ): List<String> {
        val work = workDatabase.get().asFile
        work.parentFile.mkdirs()
        work.delete()

        return withSqliteConnection(driverClasspath.files, work) { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { sql ->
                    try {
                        statement.executeUpdate(sql)
                    } catch (e: Exception) {
                        throw IllegalStateException("dump した CREATE 文が単体で適用できない:\n$sql", e)
                    }
                }
            }
            readSchemaStatements(connection, excludePattern)
        }
    }

    private companion object {
        val HEADER =
            """
            -- スキーマの定義。jOOQ の生成コードはビルド時にこのファイルから作られる。
            --
            -- 開発用 DB で形を決めて
            --   ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
            -- で書き出す。実 DB へは sqlite3def で適用する。詳細は同じディレクトリの README.md
            """.trimIndent()
    }
}
