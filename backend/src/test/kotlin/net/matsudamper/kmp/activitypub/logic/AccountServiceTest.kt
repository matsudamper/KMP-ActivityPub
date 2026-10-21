package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
    fun `検索結果が 1 ページに収まらなければ続きのカーソルを返す`() {
        (1..31).forEach { index ->
            accountRepository.add(username = "user%02d".format(index), passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)
        }

        val firstPage = service.searchAccounts(keyword = "user", cursor = null)
        assertEquals(30, firstPage.accounts.size)
        assertEquals(AccountSearchCursor(lastUsername = "user30"), firstPage.nextCursor)

        val secondPage = service.searchAccounts(keyword = "user", cursor = firstPage.nextCursor)
        assertEquals(listOf("user31"), secondPage.accounts.map { it.username })
        assertNull(secondPage.nextCursor)
    }

    @Test
    fun `空白だけの検索語では何も返さない`() {
        accountRepository.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertEquals(AccountSearchResult(accounts = listOf(), nextCursor = null), service.searchAccounts(keyword = " ", cursor = null))
    }

    private companion object {
        const val PASSWORD = "user-password"

        val PASSWORD_HASH: String = checkNotNull(PasswordHashType.Current.encoder.encode(PASSWORD))
    }
}
