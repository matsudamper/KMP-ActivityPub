package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId

class FollowingGroupRepositoryTest {
    @Test
    fun groupsAreIsolatedByAccountAndSameNameIsReused() {
        withAliceAndBob { repositories, alice, bob ->
            val groups = repositories.followingGroupRepository
            val news = groups.create(alice, "ニュース", Instant.EPOCH)
            val friends = groups.create(alice, "友人", Instant.EPOCH)
            assertEquals(news, groups.create(alice, "ニュース", Instant.EPOCH))
            assertEquals(listOf(news, friends), groups.list(alice))
            assertEquals(listOf(), groups.list(bob))
            assertTrue(groups.exists(alice, news.id))
            assertFalse(groups.exists(bob, news.id))
        }
    }

    @Test
    fun renameKeepsGroupIdAndRejectsDuplicateOrOtherAccount() {
        withAliceAndBob { repositories, alice, bob ->
            val groups = repositories.followingGroupRepository
            val news = groups.create(alice, "ニュース", Instant.EPOCH)
            groups.create(alice, "友人", Instant.EPOCH)
            assertEquals(FollowingGroupRenameResult.DuplicateName, groups.rename(alice, news.id, "友人"))
            assertEquals(FollowingGroupRenameResult.NotFound, groups.rename(bob, news.id, "技術"))
            assertEquals(FollowingGroupRenameResult.Renamed, groups.rename(alice, news.id, "ニュース"))
            assertEquals(FollowingGroupRenameResult.Renamed, groups.rename(alice, news.id, "技術"))
            assertEquals(FollowingGroup(id = news.id, name = "技術"), groups.list(alice).first())
        }
    }

    @Test
    fun mainGroupNameIsStoredPerAccount() {
        withAliceAndBob { repositories, alice, bob ->
            val groups = repositories.followingGroupRepository
            assertNull(groups.mainGroupName(alice))
            groups.setMainGroupName(alice, "すべて")
            groups.setMainGroupName(alice, "全部")
            assertEquals("全部", groups.mainGroupName(alice))
            assertNull(groups.mainGroupName(bob))
        }
    }

    @Test
    fun followingMovesBetweenGroupsAndForgetsGroupWhenUnfollowed() {
        withAliceAndBob { repositories, alice, _ ->
            val actorUri = "https://remote.example/users/carol"
            val following = repositories.followingRepository
            val news = repositories.followingGroupRepository.create(alice, "ニュース", Instant.EPOCH)
            assertFalse(following.setGroup(alice, actorUri, news.id))

            following.add(alice, actorUri, Instant.EPOCH)
            assertNull(following.groupIdOf(alice, actorUri))
            assertTrue(following.setGroup(alice, actorUri, news.id))
            assertEquals(news.id, following.groupIdOf(alice, actorUri))
            assertTrue(following.setGroup(alice, actorUri, null))
            assertNull(following.groupIdOf(alice, actorUri))

            following.setGroup(alice, actorUri, news.id)
            following.remove(alice, actorUri)
            following.add(alice, actorUri, Instant.EPOCH)
            assertNull(following.groupIdOf(alice, actorUri))
        }
    }

    private fun withAliceAndBob(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("following-group-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(
                    databasePath = path,
                    username = username,
                    passwordHash = "hash",
                    passwordHashType = "hash-type",
                    publicKeyPem = "public-key",
                    privateKeyPem = "private-key",
                    createdAt = Instant.EPOCH.toString(),
                )
            }
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                block(repositories, alice, bob)
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }
}
