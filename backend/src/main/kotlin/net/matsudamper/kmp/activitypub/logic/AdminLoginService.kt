package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.crypto.PasswordHash

class AdminLoginService(
    private val passwordHash: PasswordHash?,
) {
    val adminPasswordConfigured: Boolean = passwordHash != null

    fun matchesAdminPassword(password: String): Boolean = passwordHash?.matches(password) == true
}
