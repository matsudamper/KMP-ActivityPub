package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class DatabaseWriteCheckTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-write-check")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `DBに書き込んで読み戻せる`() {
        val dbPath = tempDir.resolve("test.db")
        applySchema(dbPath)

        DatabaseWriteCheck.verify(dbPath)
    }

    @Test
    fun `親ディレクトリが無ければ作られる`() {
        val dbPath = tempDir.resolve("nested/dir/test.db")

        assertFailsWith<Exception> { DatabaseWriteCheck.verify(dbPath) }

        assertTrue(Files.isDirectory(dbPath.parent))
    }

    @Test
    fun `同じDBへ続けて書ける`() {
        val dbPath = tempDir.resolve("test.db")
        applySchema(dbPath)

        DatabaseWriteCheck.verify(dbPath)
        DatabaseWriteCheck.verify(dbPath)
    }

    @Test
    fun `スキーマ未適用のDBでは失敗する`() {
        assertFailsWith<Exception> {
            DatabaseWriteCheck.verify(tempDir.resolve("empty.db"))
        }
    }

    private fun applySchema(dbPath: Path) {
        val schema =
            checkNotNull(javaClass.classLoader.getResourceAsStream("db/schema.sql")) {
                "schema.sql がクラスパスに無い"
            }.use { it.readBytes().toString(Charsets.UTF_8) }

        dbPath.toAbsolutePath().normalize().parent?.let { Files.createDirectories(it) }
        SqliteConnections.connect(dbPath) { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(schema)
            }
        }
    }
}
