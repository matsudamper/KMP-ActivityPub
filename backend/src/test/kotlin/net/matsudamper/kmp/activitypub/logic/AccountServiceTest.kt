package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class AccountServiceTest {
    private val accountRepository = FakeAccountRepository(hasPasskey = { false })

    private val service = AccountService(accountRepository = accountRepository)

    @Test
    fun `登録したユーザーは同じパスワードで照合が通る`() {
        val id = accountRepository.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertEquals(id, service.authenticate(username = "user1", password = PASSWORD))
    }

    @Test
    fun `名前の前後の空白は落として照合する`() {
        val id = accountRepository.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertEquals(id, service.authenticate(username = " user1 ", password = PASSWORD))
    }

    @Test
    fun `パスワードが違えば照合は通らない`() {
        accountRepository.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertNull(service.authenticate(username = "user1", password = "wrong-password"))
    }

    @Test
    fun `いないユーザーは照合が通らない`() {
        assertNull(service.authenticate(username = "unknown", password = PASSWORD))
    }

    @Test
    fun `知らない方式のハッシュは照合で失敗にする`() {
        accountRepository.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = "unknown")

        assertFailsWith<IllegalStateException> { service.authenticate(username = "user1", password = PASSWORD) }
    }

    @Test
    fun `大文字小文字が違ってもユーザー名でユーザーを引ける`() {
        val id = accountRepository.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertEquals(id, service.account(username = "USER1")?.id)
    }

    @Test
    fun `いないユーザー名では引けない`() {
        assertNull(service.account(username = "user1"))
    }

    @Test
    fun `登録したユーザーは同じパスワードで照合が通り鍵も持つ`() {
        val added = assertIs<AccountService.AddAccountResult.Success>(service.add(username = " user1 ", password = PASSWORD))

        assertEquals("user1", added.account.username)
        assertEquals(added.account.id, service.authenticate(username = "user1", password = PASSWORD))
        assertContains(assertNotNull(service.publicKeyPem("user1")), "BEGIN PUBLIC KEY")
    }

    @Test
    fun `パスワードが短ければ登録しない`() {
        val failure = assertIs<AccountService.AddAccountResult.Failure>(service.add(username = "user1", password = "1234567"))

        assertTrue(failure.passwordTooShort)
        assertNull(service.account(username = "user1"))
    }

    @Test
    fun `使えない文字は入力にあったものを返して登録しない`() {
        val failure = assertIs<AccountService.AddAccountResult.Failure>(service.add(username = "user 1/あ", password = PASSWORD))

        assertEquals(listOf(' ', '/', 'あ'), failure.unusableCharacters)
    }

    @Test
    fun `大文字小文字だけが違う名前は登録できない`() {
        service.add(username = "user1", password = PASSWORD)

        val failure = assertIs<AccountService.AddAccountResult.Failure>(service.add(username = "USER1", password = PASSWORD))

        assertTrue(failure.duplicated)
    }

    @Test
    fun `1 件ずつでも名前順に辿れる`() {
        service.add(username = "user2", password = PASSWORD)
        service.add(username = "user1", password = PASSWORD)

        val page1 = service.accounts(afterUsername = null, limit = 1)
        assertEquals(listOf("user1"), page1.accounts.map { it.username })

        val page2 = service.accounts(afterUsername = page1.nextCursor, limit = 1)
        assertEquals(listOf("user2"), page2.accounts.map { it.username })
        assertNull(page2.nextCursor)
    }

    private companion object {
        const val PASSWORD = "user-password"

        val PASSWORD_HASH: String = checkNotNull(PasswordHashType.Current.encoder.encode(PASSWORD))
    }
}
