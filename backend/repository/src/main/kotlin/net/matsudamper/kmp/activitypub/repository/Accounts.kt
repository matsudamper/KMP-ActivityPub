package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.sql.DriverManager
import net.matsudamper.kmp.activitypub.repository.jooq.tables.Accounts.ACCOUNTS
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

object Accounts {
    /**
     * @throws AccountAlreadyExists 同じユーザー名が既にある
     * @throws AccountTableMissing accounts が無い
     */
    fun insert(
        databasePath: Path,
        username: String,
        passwordHash: String,
        passwordHashType: String,
        createdAt: String,
    ) {
        try {
            DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
                DSL.using(connection, SQLDialect.SQLITE)
                    .insertInto(ACCOUNTS)
                    .columns(
                        ACCOUNTS.USERNAME,
                        ACCOUNTS.PASSWORD_HASH,
                        ACCOUNTS.PASSWORD_HASH_TYPE,
                        ACCOUNTS.CREATED_AT,
                    )
                    .values(username, passwordHash, passwordHashType, createdAt)
                    .execute()
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
