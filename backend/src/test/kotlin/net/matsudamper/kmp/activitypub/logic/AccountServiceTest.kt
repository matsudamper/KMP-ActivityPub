package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeRepositories

class AccountServiceTest {
    private val repositories = FakeRepositories()

    private val service = AccountService(accountRepository = repositories.accountRepository, domain = "example.com")

    @Test
    fun `登録したユーザーは同じパスワードで照合が通る`() {
        val added = assertIs<AccountService.AddAccountResult.Success>(service.add(username = "user1", password = PASSWORD))

        assertEquals(added.account.accountId, service.authenticate(username = "user1", password = PASSWORD))
    }

    @Test
    fun `名前の前後の空白は落として照合する`() {
        val added = assertIs<AccountService.AddAccountResult.Success>(service.add(username = " user1 ", password = PASSWORD))

        assertEquals("user1", added.account.urls.username)
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

    private companion object {
        const val PASSWORD = "user-password"
    }
}
