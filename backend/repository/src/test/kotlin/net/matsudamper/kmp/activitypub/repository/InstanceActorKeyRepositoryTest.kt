package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class InstanceActorKeyRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-instance-actor-key-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `鍵が無ければ渡した鍵を保存し 以後はそれを返す`() {
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals("first", repositories.instanceActorKeyRepository.findOrCreatePrivateKeyPem { "first" })
        }
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals("first", repositories.instanceActorKeyRepository.findOrCreatePrivateKeyPem { "second" })
        }
    }
}
