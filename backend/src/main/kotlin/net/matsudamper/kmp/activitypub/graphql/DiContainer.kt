package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.AdminLoginService
import net.matsudamper.kmp.activitypub.logic.NoteEnqueuer

class DiContainer(
    passwordHash: PasswordHash?,
    val accountService: AccountService,
    val noteEnqueuer: NoteEnqueuer,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)
}
