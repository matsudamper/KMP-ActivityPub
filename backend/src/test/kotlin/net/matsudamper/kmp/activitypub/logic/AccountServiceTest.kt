package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class AccountServiceTest {
    private val repositories = FakeRepositories()

    private val service = AccountService(accountRepository = repositories.accountRepository)

    @Test
    fun `登録したユーザーは同じパスワードで照合が通る`() {
        val added = assertIs<AccountService.AddAccountResult.Success>(service.add(username = "user1", password = PASSWORD))

        assertEquals(added.account.accountId, service.authenticate(username = "user1", password = PASSWORD))
    }

    @Test
    fun `名前の前後の空白は落として照合する`() {
        val added = assertIs<AccountService.AddAccountResult.Success>(service.add(username = " user1 ", password = PASSWORD))

        assertEquals("user1", added.account.username)
        assertEquals(added.account.accountId, service.authenticate(username = "user1 ", password = PASSWORD))
    }

    @Test
    fun `パスワードが違えば照合は通らない`() {
        service.add(username = "user1", password = PASSWORD)

        assertNull(service.authenticate(username = "user1", password = "wrong-password"))
    }

    @Test
    fun `いないユーザーは照合が通らない`() {
        assertNull(service.authenticate(username = "unknown", password = PASSWORD))
    }

    @Test
    fun `知らない方式のハッシュは照合で失敗にする`() {
        repositories.accountRepository.add(
            username = "user1",
            passwordHash = "hash",
            passwordHashType = "unknown",
            publicKeyPem = "public",
            privateKeyPem = "private",
            createdAt = Instant.EPOCH,
        )

        assertFailsWith<IllegalStateException> { service.authenticate(username = "user1", password = PASSWORD) }
    }

    @Test
    fun `パスワードが短ければ登録しない`() {
        val failure = assertIs<AccountService.AddAccountResult.Failure>(service.add(username = "user1", password = "1234567"))

        assertEquals(true, failure.passwordTooShort)
        assertNull(repositories.accountRepository.findByUsername("user1"))
    }

    @Test
    fun `使えない文字は入力にあったものを返して登録しない`() {
        val failure = assertIs<AccountService.AddAccountResult.Failure>(service.add(username = "user 1/あ", password = PASSWORD))

        assertEquals(listOf(' ', '/', 'あ'), failure.unusableCharacters)
        assertNull(repositories.accountRepository.findByUsername("user 1/あ"))
    }

    @Test
    fun `大文字小文字だけが違う名前は登録できない`() {
        service.add(username = "user1", password = PASSWORD)

        val failure = assertIs<AccountService.AddAccountResult.Failure>(service.add(username = "USER1", password = PASSWORD))

        assertTrue(failure.duplicated)
    }

    @Test
    fun `登録したユーザーのパスワードはハッシュで持ち鍵も持つ`() {
        service.add(username = "user1", password = PASSWORD)

        val credential = assertNotNull(repositories.accountRepository.findCredential("user1"))
        assertNotEquals(PASSWORD, credential.passwordHash)
        assertTrue(PasswordHashType.Current.encoder.matches(PASSWORD, credential.passwordHash))
        assertContains(assertNotNull(service.publicKeyPem("user1")), "BEGIN PUBLIC KEY")
    }

    @Test
    fun `1 件ずつでも登録した順に辿れる`() {
        service.add(username = "user1", password = PASSWORD)
        service.add(username = "user2", password = PASSWORD)

        val page1 = service.accounts(after = null, limit = 1)
        assertEquals(listOf("user1"), page1.accounts.map { it.username })
        assertTrue(page1.hasMore)

        val page2 = service.accounts(after = page1.nextPosition, limit = 1)
        assertEquals(listOf("user2"), page2.accounts.map { it.username })
        assertFalse(page2.hasMore)
    }

    @Test
    fun `大文字小文字が違ってもユーザー名でユーザーを引ける`() {
        val added = assertIs<AccountService.AddAccountResult.Success>(service.add(username = "user1", password = PASSWORD))

        assertEquals(added.account.accountId, service.account(username = "USER1")?.id)
    }

    @Test
    fun `いないユーザー名では引けない`() {
        assertNull(service.account(username = "user1"))
    }

    private companion object {
        const val PASSWORD = "user-password"
    }
}
