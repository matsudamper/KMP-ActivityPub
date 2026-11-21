package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class ServerSettingsRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-server-settings-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @BeforeTest
    fun setUp() {
        TestSchema.applyTo(dbPath)
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `配る先の上限を保存していなければ null`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertNull(repositories.serverSettingsRepository.findMaxDeliveryTargetsPerHost())
        }
    }

    @Test
    fun `配る先の上限は保存し直すと置き換わり 開き直しても残る`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            repositories.serverSettingsRepository.saveMaxDeliveryTargetsPerHost(30)
            repositories.serverSettingsRepository.saveMaxDeliveryTargetsPerHost(50)
        }
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(50, repositories.serverSettingsRepository.findMaxDeliveryTargetsPerHost())
        }
    }
}
