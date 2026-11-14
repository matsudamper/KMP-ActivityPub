package net.matsudamper.kmp.activitypub.frontend.logic.user

/**
 * @param acct ブロックしたときの username@host
 */
data class UserBlockedAccount(
    val actorUri: String,
    val acct: String,
)
