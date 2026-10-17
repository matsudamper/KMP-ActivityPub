package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_KEYS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_PASSWORDS
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

object Accounts {
    /**
     * @throws AccountAlreadyExists 同じユーザー名が既にある
     * @throws AccountTableMissing accounts, account_passwords, account_keys のどれかが無い
     */
    fun insert(
        databasePath: Path,
        username: String,
        passwordHash: String,
        passwordHashType: String,
        publicKeyPem: String,
        privateKeyPem: String,
        createdAt: String,
    ) {
        val storedUsername = username.lowercase()
        try {
            SqliteConnections.connect(databasePath) { connection ->
                DSL.using(connection, SQLDialect.SQLITE).transaction { configuration ->
                    val dsl = configuration.dsl()
                    dsl.insertInto(ACCOUNTS)
                        .columns(
                            ACCOUNTS.USERNAME,
                            ACCOUNTS.CREATED_AT,
                        )
                        .values(storedUsername, createdAt)
                        .execute()
                    val accountId = checkNotNull(dsl.lastID()).toLong()
                    dsl.insertInto(ACCOUNT_PASSWORDS)
                        .columns(
                            ACCOUNT_PASSWORDS.ACCOUNT_ID,
                            ACCOUNT_PASSWORDS.PASSWORD_HASH,
                            ACCOUNT_PASSWORDS.PASSWORD_HASH_TYPE,
                        )
                        .values(accountId, passwordHash, passwordHashType)
                        .execute()
                    dsl.insertInto(ACCOUNT_KEYS)
                        .columns(
                            ACCOUNT_KEYS.ACCOUNT_ID,
                            ACCOUNT_KEYS.PUBLIC_KEY_PEM,
                            ACCOUNT_KEYS.PRIVATE_KEY_PEM,
                        )
                        .values(accountId, publicKeyPem, privateKeyPem)
                        .execute()
                }
            }
        } catch (exception: DataAccessException) {
            val sqlite = generateSequence<Throwable>(exception) { it.cause }.filterIsInstance<SQLiteException>().firstOrNull()
            if (sqlite?.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) {
                throw AccountAlreadyExists(username)
            }
            if (sqlite?.message?.contains("no such table") == true) {
                throw AccountTableMissing()
            }
            throw exception
        }
    }
}

sealed class AccountInsertException : RuntimeException()

class AccountAlreadyExists(
    val username: String,
) : AccountInsertException()

class AccountTableMissing : AccountInsertException()
