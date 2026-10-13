package net.matsudamper.kmp.activitypub.crypto

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * accounts.password_hash の方式。accounts.password_hash_type に [dbValue] を入れる。
 *
 * 照合は行ごとの方式で行うので、方式を足して [Current] を差し替えても、既存の行は元の方式のまま照合できる。
 * [dbValue] は DB に残るので変えない。
 */
enum class PasswordHashType(
    val dbValue: String,
    val encoder: PasswordEncoder,
) {
    /**
     * 値は `$argon2id$v=19$m=...,t=...,p=...$<salt>$<hash>` の形で、パラメーターを含む。
     * 照合は値の中のパラメーターで行うので、ここの設定を変えても既存の値は通る。
     */
    Argon2(
        dbValue = "argon2",
        encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
    ),
    ;

    companion object {
        /**
         * 新しく作るハッシュの方式
         */
        val Current: PasswordHashType = Argon2

        fun fromDbValue(dbValue: String): PasswordHashType? = entries.firstOrNull { it.dbValue == dbValue }
    }
}
