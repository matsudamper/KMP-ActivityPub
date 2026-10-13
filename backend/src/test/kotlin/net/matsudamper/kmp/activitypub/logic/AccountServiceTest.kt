package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType

class AccountServiceTest {
    private val accounts = FakeAccountRepository()

    private val service = AccountService(accounts = accounts)

    @Test
    fun `登録したユーザーは同じパスワードで照合が通る`() {
        val id = accounts.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertEquals(id, service.authenticate(username = "user1", password = PASSWORD))
    }

    @Test
    fun `名前の前後の空白は落として照合する`() {
        val id = accounts.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertEquals(id, service.authenticate(username = " user1 ", password = PASSWORD))
    }

    @Test
    fun `パスワードが違えば照合は通らない`() {
        accounts.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PasswordHashType.Current.dbValue)

        assertNull(service.authenticate(username = "user1", password = "wrong-password"))
    }

    @Test
    fun `いないユーザーは照合が通らない`() {
        assertNull(service.authenticate(username = "unknown", password = PASSWORD))
    }

    @Test
    fun `知らない方式のハッシュは照合で失敗にする`() {
        accounts.add(username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = "unknown")

        assertFailsWith<IllegalStateException> { service.authenticate(username = "user1", password = PASSWORD) }
    }

    private companion object {
        const val PASSWORD = "user-password"

        val PASSWORD_HASH: String = checkNotNull(PasswordHashType.Current.encoder.encode(PASSWORD))
    }
}
