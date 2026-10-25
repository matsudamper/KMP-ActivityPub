package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.crypto.PasswordHash

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class AdminLoginServiceTest {
    @Test
    fun `パスワードハッシュが未設定なら設定されていない扱いで照合も通らない`() {
        val service = AdminLoginService(passwordHash = null)

        assertFalse(service.adminPasswordConfigured)
        assertFalse(service.matchesAdminPassword(PASSWORD))
    }

    @Test
    fun `正しいパスワードだけ照合が通る`() {
        val service = AdminLoginService(passwordHash = PasswordHash.create(PASSWORD, iterations = 1_000))

        assertTrue(service.adminPasswordConfigured)
        assertTrue(service.matchesAdminPassword(PASSWORD))
        assertFalse(service.matchesAdminPassword("wrong-password"))
    }

    private companion object {
        const val PASSWORD = "admin-password"
    }
}
