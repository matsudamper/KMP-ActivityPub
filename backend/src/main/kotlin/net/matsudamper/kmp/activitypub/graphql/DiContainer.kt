package net.matsudamper.kmp.activitypub.graphql

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.PasskeyService

class DiContainer(
    val accountService: AccountService,
    /**
     * パスキーを設定していなければ null
     */
    val passkeyService: PasskeyService?,
)
