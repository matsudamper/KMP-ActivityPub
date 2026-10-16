package net.matsudamper.kmp.activitypub.passkey

import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * ブラウザや OS が作った clientDataJSON（WebAuthn Level 3 の 5.8.1）。
 * 署名は JSON の文字列ではなくバイト列に対して行われるので、受け取ったバイト列も持っておく
 */
class PasskeyClientData private constructor(
    private val raw: ByteArray,
    internal val type: String,
    /**
     * 発行した challenge を base64url（パディング無し）にしたもの
     */
    val challenge: String,
    internal val origin: String,
    internal val crossOrigin: Boolean,
) {
    internal fun hash(): ByteArray = MessageDigest.getInstance("SHA-256").digest(raw)

    companion object {
        /**
         * @throws PasskeyVerificationException JSON として読めないか、必要な項目が無い
         */
        fun parse(raw: ByteArray): PasskeyClientData {
            val json =
                try {
                    Json.parseToJsonElement(raw.decodeToString(throwOnInvalidSequence = true))
                } catch (e: SerializationException) {
                    throw PasskeyVerificationException("clientDataJSON を読めない", e)
                } catch (e: CharacterCodingException) {
                    throw PasskeyVerificationException("clientDataJSON が UTF-8 ではない", e)
                } as? JsonObject ?: throw PasskeyVerificationException("clientDataJSON がオブジェクトではない")

            return PasskeyClientData(
                raw = raw,
                type = json.string("type"),
                challenge = json.string("challenge"),
                origin = json.string("origin"),
                crossOrigin = (json["crossOrigin"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }

        private fun JsonObject.string(key: String): String {
            val value = this[key] as? JsonPrimitive
            if (value == null || !value.isString) throw PasskeyVerificationException("clientDataJSON に $key が無い")
            return value.content
        }
    }
}
