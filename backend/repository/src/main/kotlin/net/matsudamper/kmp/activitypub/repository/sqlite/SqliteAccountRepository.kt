package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_PASSWORDS
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqliteAccountRepository(
    private val jooq: SqliteJooq,
) : AccountRepository {
    override fun findById(id: AccountId): Account? = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .where(ACCOUNTS.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Account(
                    id = AccountId(checkNotNull(record.get(ACCOUNTS.ID))),
                    username = checkNotNull(record.get(ACCOUNTS.USERNAME)),
                    createdAt = StoredInstant.parse(checkNotNull(record.get(ACCOUNTS.CREATED_AT))),
                )
            }
    }

    override fun findCredential(username: String): AccountCredential? = jooq.withConnection { dsl ->
        dsl
            .select(
                ACCOUNTS.ID,
                ACCOUNTS.USERNAME,
                ACCOUNT_PASSWORDS.PASSWORD_HASH,
                ACCOUNT_PASSWORDS.PASSWORD_HASH_TYPE,
            )
            .from(ACCOUNTS)
            .join(ACCOUNT_PASSWORDS)
            .on(ACCOUNT_PASSWORDS.ACCOUNT_ID.eq(ACCOUNTS.ID))
            .where(ACCOUNTS.USERNAME.eq(username.uppercase()))
            .fetchOne()
            ?.let { record ->
                AccountCredential(
                    id = AccountId(checkNotNull(record.get(ACCOUNTS.ID))),
                    username = checkNotNull(record.get(ACCOUNTS.USERNAME)),
                    passwordHash = checkNotNull(record.get(ACCOUNT_PASSWORDS.PASSWORD_HASH)),
                    passwordHashType = checkNotNull(record.get(ACCOUNT_PASSWORDS.PASSWORD_HASH_TYPE)),
                )
            }
    }
}
