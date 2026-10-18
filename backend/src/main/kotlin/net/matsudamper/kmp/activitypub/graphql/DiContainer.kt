package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.FederationDependencies
import net.matsudamper.kmp.activitypub.crypto.PasswordHash
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.AdminLoginService
import net.matsudamper.kmp.activitypub.session.LoginThrottle

/**
 * @param federation `DOMAIN` が未設定なら null。投稿は配れないので受け付けない
 */
class DiContainer(
    passwordHash: PasswordHash?,
    val accountService: AccountService,
    val loginThrottle: LoginThrottle,
    val localDomain: String?,
    val federation: FederationDependencies?,
) {
    val adminLoginService: AdminLoginService = AdminLoginService(passwordHash)
}
