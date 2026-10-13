package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RepositoriesTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-repository-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `DBに書き込んで読み戻せる`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.verifyWritable()
        }
    }

    @Test
    fun `親ディレクトリが無ければ作られる`() {
        val dbPath = tempDir.resolve("nested/dir/test.db")
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.verifyWritable()
        }

        assertTrue(Files.exists(dbPath), "DB ファイルが作られていない: $dbPath")
    }

    @Test
    fun `閉じたあとに開き直しても内容が残っている`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        val config = DatabaseConfig(path = dbPath)

        Repositories.create(config).use { it.verifyWritable() }
        Repositories.create(config).use { it.verifyWritable() }
    }

    @Test
    fun `スキーマ未適用のDBでは書き込み確認が失敗する`() {
        val config = DatabaseConfig(path = tempDir.resolve("empty.db"))

        Repositories.create(config).use { repositories ->
            assertFailsWith<Exception> { repositories.verifyWritable() }
        }
    }
}
