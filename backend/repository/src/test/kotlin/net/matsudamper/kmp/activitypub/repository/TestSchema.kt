package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Files
import java.nio.file.Path
import org.sqlite.SQLiteDataSource

internal object TestSchema {
    fun applyTo(dbPath: Path) {
        val schema =
            checkNotNull(TestSchema::class.java.classLoader.getResourceAsStream("db/schema.sql")) {
                "schema.sql がクラスパスに無い"
            }.use { it.readBytes().toString(Charsets.UTF_8) }

        Files.createDirectories(dbPath.toAbsolutePath().normalize().parent)

        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(schema)
            }
        }
    }
}
