package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class AccountRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-account-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `追加したアカウントを引ける`() {
        withRepositories { repositories ->
            val added =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )

            assertEquals("feed1", added.username)
            assertEquals(CREATED_AT, added.createdAt)
            assertEquals(added, repositories.accountRepository.findByUsername("feed1"))
        }
    }

    @Test
    fun `プロフィールを書き換えると引き直したときに反映されている`() {
        withRepositories { repositories ->
            val added =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )

            val updated = assertNotNull(
                repositories.accountRepository.updateProfile(id = added.id, displayName = "フィード 1", summary = "説明"),
            )

            assertEquals("フィード 1", updated.displayName)
            assertEquals("説明", updated.summary)
            assertEquals(updated, repositories.accountRepository.findByUsername("feed1"))
        }
    }

    @Test
    fun `プロフィールに null を渡すと未設定に戻る`() {
        withRepositories { repositories ->
            val added =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )
            repositories.accountRepository.updateProfile(id = added.id, displayName = "フィード 1", summary = "説明")

            val cleared = assertNotNull(
                repositories.accountRepository.updateProfile(id = added.id, displayName = null, summary = null),
            )

            assertNull(cleared.displayName)
            assertNull(cleared.summary)
        }
    }

    @Test
    fun `CLI から登録したアカウントを id で引ける`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        Accounts.insert(
            databasePath = dbPath,
            username = "user1",
            passwordHash = PASSWORD_HASH,
            passwordHashType = PASSWORD_HASH_TYPE,
            publicKeyPem = PUBLIC_KEY_PEM,
            privateKeyPem = "private-key",
            createdAt = CREATED_AT.toString(),
        )

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val id = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            assertEquals("user1", repositories.accountRepository.findById(id)?.username)
        }
    }

    @Test
    fun `追加したアカウントの鍵を名前で引ける`() {
        withRepositories { repositories ->
            repositories.accountRepository.add(
                username = "feed1",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = CREATED_AT,
            )

            assertEquals(PUBLIC_KEY_PEM, repositories.accountRepository.findPublicKeyPem("Feed1"))
            assertEquals(PRIVATE_KEY_PEM, repositories.accountRepository.findPrivateKeyPem("Feed1"))
        }
    }

    @Test
    fun `大文字小文字の違いは同じ名前として扱う`() {
        withRepositories { repositories ->
            repositories.accountRepository.add(
                username = "Feed1",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = CREATED_AT,
            )

            // 引くときも入れるときも同じ扱いにしないと、引けないアカウントが増える
            assertEquals("feed1", repositories.accountRepository.findByUsername("feed1")?.username)
            assertNull(
                repositories.accountRepository.add(
                    username = "FEED1",
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT,
                ),
            )
        }
    }

    @Test
    fun `複数アカウントをまとめて引ける`() {
        withRepositories { repositories ->
            repositories.accountRepository.add(
                username = "Feed1",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = CREATED_AT,
            )
            repositories.accountRepository.add(
                username = "gihyo",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = CREATED_AT,
            )

            val result = repositories.accountRepository.findByUsernames(listOf("feed1", "GIHYO", "other"))
            assertEquals(2, result.size)
            assertEquals("feed1", result["feed1"]?.username)
            assertEquals("gihyo", result["GIHYO"]?.username)
            assertNull(result["other"])
        }
    }

    @Test
    fun `一覧は追加した順に返る`() {
        withRepositories { repositories ->
            listOf("gihyo", "feed1", "blog").forEach {
                repositories.accountRepository.add(
                    username = it,
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT,
                )
            }

            assertEquals(
                listOf("gihyo", "feed1", "blog"),
                repositories.accountRepository.list().map { it.username },
            )
        }
    }

    @Test
    fun `秒未満の桁が違っても時刻の順に返る`() {
        withRepositories { repositories ->
            // 文字列で保存しているので、桁が揃っていないと並びが時刻の順にならない
            repositories.accountRepository.add(
                username = "late",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = Instant.parse("2026-08-16T00:00:00.5Z"),
            )
            repositories.accountRepository.add(
                username = "early",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = Instant.parse("2026-08-16T00:00:00Z"),
            )

            assertEquals(listOf("early", "late"), repositories.accountRepository.list().map { it.username })
        }
    }

    @Test
    fun `カーソルを指定してページングで取得できる`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d", "e").forEachIndexed { index, username ->
                repositories.accountRepository.add(
                    username = username,
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT.plusSeconds(index.toLong()),
                )
            }

            val page1 = repositories.accountRepository.list(after = null, limit = 2)
            assertEquals(listOf("a", "b"), page1.map { it.username })

            val page2 = repositories.accountRepository.list(after = page1.last().position(), limit = 2)
            assertEquals(listOf("c", "d"), page2.map { it.username })

            val page3 = repositories.accountRepository.list(after = page2.last().position(), limit = 2)
            assertEquals(listOf("e"), page3.map { it.username })

            val page4 = repositories.accountRepository.list(after = page3.last().position(), limit = 2)
            assertEquals(emptyList(), page4.map { it.username })
        }
    }

    @Test
    fun `時刻が同じでもページングで重複や取りこぼしが出ない`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d", "e").forEach { username ->
                repositories.accountRepository.add(
                    username = username,
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT,
                )
            }

            val page1 = repositories.accountRepository.list(after = null, limit = 2)
            assertEquals(listOf("a", "b"), page1.map { it.username })

            val page2 = repositories.accountRepository.list(after = page1.last().position(), limit = 2)
            assertEquals(listOf("c", "d"), page2.map { it.username })

            val page3 = repositories.accountRepository.list(after = page2.last().position(), limit = 2)
            assertEquals(listOf("e"), page3.map { it.username })

            val page4 = repositories.accountRepository.list(after = page3.last().position(), limit = 2)
            assertEquals(emptyList(), page4.map { it.username })
        }
    }

    @Test
    fun `カーソルの指す行が消えていても続きが返る`() {
        withRepositories { repositories ->
            listOf("a", "b", "c", "d").forEachIndexed { index, username ->
                repositories.accountRepository.add(
                    username = username,
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT.plusSeconds(index.toLong()),
                )
            }

            val page1 = repositories.accountRepository.list(after = null, limit = 2)
            val after = page1.last().position()
            repositories.deleteAccount(page1.last())

            assertEquals(listOf("c", "d"), repositories.accountRepository.list(after = after, limit = 2).map { it.username })
        }
    }

    @Test
    fun `消した名前は作り直せない`() {
        withRepositories { repositories ->
            val account =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )
            repositories.deleteAccount(account)

            // 作り直せると、送り残した Delete{Actor} が新しいアカウントのものとして配られる
            assertNull(
                repositories.accountRepository.add(
                    username = "feed1",
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT.plusSeconds(10),
                ),
            )
            assertNull(repositories.accountRepository.findByUsername("feed1"))
        }
    }

    @Test
    fun `開き直しても残っている`() {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        val config = DatabaseConfig(path = dbPath)

        Repositories.create(config).use {
            it.accountRepository.add(
                username = "feed1",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                publicKeyPem = PUBLIC_KEY_PEM,
                privateKeyPem = PRIVATE_KEY_PEM,
                createdAt = CREATED_AT,
            )
        }

        Repositories.create(config).use {
            assertEquals(CREATED_AT, it.accountRepository.findByUsername("feed1")?.createdAt)
        }
    }

    @Test
    fun `照合に使うハッシュと方式を名前で引ける`() {
        withRepositories { repositories ->
            val added =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "Feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )

            val credential = assertNotNull(repositories.accountRepository.findCredential("feed1"))

            assertEquals(added.id, credential.id)
            assertEquals("feed1", credential.username)
            assertEquals(PASSWORD_HASH, credential.passwordHash)
            assertEquals(PASSWORD_HASH_TYPE, credential.passwordHashType)
        }
    }

    @Test
    fun `消したアカウントのハッシュは引けない`() {
        withRepositories { repositories ->
            val account =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )

            assertNotNull(repositories.deleteAccount(account))

            assertNull(repositories.accountRepository.findCredential("feed1"))
        }
    }

    @Test
    fun `消えているアカウントを消しても何も起きない`() {
        withRepositories { repositories ->
            val account =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )
            repositories.deleteAccount(account)

            // 2 回目も投函すると、同じ削除が 2 度配られる
            assertNull(repositories.deleteAccount(account))
        }
    }

    @Test
    fun `消すと宛先ごとに Delete が投函され 送り残した配信は消える`() {
        withRepositories { repositories ->
            val account =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )
            repositories.deliveryQueue.enqueueNote(
                NotePost(
                    note = NewNote(
                        username = "feed1",
                        publicId = PublicNoteId("note-1"),
                        contentHtml = "<p>本文</p>",
                        publishedAt = CREATED_AT,
                    ),
                    body = """{"type":"Create"}""",
                    inboxes = listOf(INBOX),
                    enqueuedAt = CREATED_AT,
                ),
            )

            val deleted = assertNotNull(repositories.deleteAccount(account, inboxes = listOf(INBOX)))

            assertEquals(1, deleted.deletedNotes)
            assertEquals(1, deleted.deliveries)
            // 消えたアカウントの投稿が後から届かないよう、送り残しは消える
            val claimed = repositories.deliveryQueue.claim(now = DELETED_AT, limit = 10)
            assertEquals(listOf(DeliveryKind.DELETE_ACTOR), claimed.map { it.kind })
            assertNull(repositories.notes.find(PublicNoteId("note-1")))
        }
    }

    @Test
    fun `配信を送り切った削除済みアカウントだけが片付く`() {
        withRepositories { repositories ->
            val sending =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed1",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )
            val done =
                assertNotNull(
                    repositories.accountRepository.add(
                        username = "feed2",
                        passwordHash = PASSWORD_HASH,
                        passwordHashType = PASSWORD_HASH_TYPE,
                        publicKeyPem = PUBLIC_KEY_PEM,
                        privateKeyPem = PRIVATE_KEY_PEM,
                        createdAt = CREATED_AT,
                    ),
                )
            repositories.deleteAccount(sending, inboxes = listOf(INBOX))
            repositories.deleteAccount(done)

            assertEquals(1, repositories.accountRepository.purgeDeleted())

            // 送り切るまでは名前を押さえたままにする
            assertNull(
                repositories.accountRepository.add(
                    username = "feed1",
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT,
                ),
            )
            assertNotNull(
                repositories.accountRepository.add(
                    username = "feed2",
                    passwordHash = PASSWORD_HASH,
                    passwordHashType = PASSWORD_HASH_TYPE,
                    publicKeyPem = PUBLIC_KEY_PEM,
                    privateKeyPem = PRIVATE_KEY_PEM,
                    createdAt = CREATED_AT,
                ),
            )
        }
    }

    private fun Repositories.deleteAccount(
        account: Account,
        inboxes: List<String> = emptyList(),
    ): AccountDeletionResult? = accountRepository.markDeleted(
        AccountDeletion(
            id = account.id,
            username = account.username,
            body = """{"type":"Delete"}""",
            inboxes = inboxes,
            deletedAt = DELETED_AT,
        ),
    )

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        const val INBOX = "https://remote.example/inbox"

        const val PASSWORD_HASH = "hash"

        const val PASSWORD_HASH_TYPE = "hash-type"

        const val PUBLIC_KEY_PEM = "public-key"

        const val PRIVATE_KEY_PEM = "private-key"

        // 秒未満まで持つ。文字列で保存しているので、桁が落ちるとここで分かる
        val CREATED_AT: Instant = Instant.parse("2026-08-16T01:02:03.123456Z")

        val DELETED_AT: Instant = CREATED_AT.plusSeconds(60)
    }
}
