package net.matsudamper.kmp.activitypub.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordHashTypeTest {
    private val encoder = PasswordHashType.Current.encoder

    @Test
    fun `作ったハッシュは同じパスワードで照合が通る`() {
        val encoded = encoder.encode("correct horse battery")

        assertTrue(encoder.matches("correct horse battery", encoded))
    }

    @Test
    fun `違うパスワードでは照合が通らない`() {
        val encoded = encoder.encode("correct horse battery")

        assertFalse(encoder.matches("wrong horse battery", encoded))
    }

    @Test
    fun `DB に入れた値から方式を引ける`() {
        PasswordHashType.entries.forEach { type ->
            assertEquals(type, PasswordHashType.fromDbValue(type.dbValue))
        }
    }

    @Test
    fun `知らない値からは方式を引けない`() {
        assertNull(PasswordHashType.fromDbValue("unknown"))
    }
}
