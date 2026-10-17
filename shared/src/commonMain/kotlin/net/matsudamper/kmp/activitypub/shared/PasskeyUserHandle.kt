package net.matsudamper.kmp.activitypub.shared

/**
 * WebAuthn の user handle（user.id）を base64url（パディング無し）にしたもの
 */
data class PasskeyUserHandle(
    val value: String,
)
