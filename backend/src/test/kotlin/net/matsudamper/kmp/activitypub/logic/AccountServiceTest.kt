package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.crypto.PasswordHash

class AccountServiceTest {
    private val accounts = FakeAccountRepository()

    private val service = AccountService(accounts = accounts)

    @Test
    fun `登録したユーザーは同じパスワードで照合が通る`() {
        val id = accounts.add(username = "user1", passwordHash = PASSWORD_HASH)

        assertEquals(id, service.authenticate(username = "user1", password = PASSWORD))
    }

    @Test
    fun `名前の前後の空白は落として照合する`() {
        val id = accounts.add(username = "user1", passwordHash = PASSWORD_HASH)

        assertEquals(id, service.authenticate(username = " user1 ", password = PASSWORD))
    }

    @Test
    fun `パスワードが違えば照合は通らない`() {
        accounts.add(username = "user1", passwordHash = PASSWORD_HASH)

        assertNull(service.authenticate(username = "user1", password = "wrong-password"))
    }

    @Test
    fun `いないユーザーは照合が通らない`() {
        assertNull(service.authenticate(username = "unknown", password = PASSWORD))
    }

    private companion object {
        const val PASSWORD = "user-password"

        /**
         * 反復回数は検証にも使われるので、落としても経路は同じ。既定だとテストのたびに待つ
         */
        val PASSWORD_HASH: String = PasswordHash.create(PASSWORD, iterations = 1_000).encode()
    }
}
