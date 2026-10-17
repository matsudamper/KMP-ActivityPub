package net.matsudamper.kmp.activitypub.shared

/**
 * WebAuthn の credential ID を base64url（パディング無し）にしたもの
 */
data class PasskeyCredentialId(
    val value: String,
)
