package net.matsudamper.kmp.activitypub.cli

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.isRegularFile
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.repository.AccountAlreadyExists
import net.matsudamper.kmp.activitypub.repository.AccountTableMissing
import net.matsudamper.kmp.activitypub.repository.Accounts

internal object AccountRegistration {
    private val usernamePattern = Regex("^[A-Za-z0-9._-]+$")

    fun register(
        databasePath: Path,
        username: String,
        password: String,
        createdAt: Instant = Instant.now(),
    ) {
        if (!usernamePattern.matches(username)) {
            throw AccountRegistrationException("ユーザー名は英数字と _ . - だけにする: $username")
        }
        if (password.isEmpty()) {
            throw AccountRegistrationException("パスワードを入力すること")
        }
        if (!databasePath.isRegularFile()) {
            throw AccountRegistrationException("DB が無い: $databasePath 。sqlite3def でスキーマを適用すること")
        }

        val passwordHash = checkNotNull(PasswordHashType.Current.encoder.encode(password))

        try {
            Accounts.insert(
                databasePath = databasePath,
                username = username,
                passwordHash = passwordHash,
                passwordHashType = PasswordHashType.Current.dbValue,
                createdAt = createdAt.toString(),
            )
        } catch (exception: AccountAlreadyExists) {
            throw AccountRegistrationException("同じユーザー名が既にある: ${exception.username}")
        } catch (exception: AccountTableMissing) {
            throw AccountRegistrationException("accounts が無い。sqlite3def でスキーマを適用すること")
        }
    }
}

internal class AccountRegistrationException(
    message: String,
) : RuntimeException(message)
