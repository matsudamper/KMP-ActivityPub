package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_KEYS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_PASSWORDS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.PASSKEYS
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.impl.DSL

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

    override fun findByUsername(username: String): Account? = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .fetchOne()
            ?.let { record ->
                Account(
                    id = AccountId(checkNotNull(record.get(ACCOUNTS.ID))),
                    username = checkNotNull(record.get(ACCOUNTS.USERNAME)),
                    createdAt = StoredInstant.parse(checkNotNull(record.get(ACCOUNTS.CREATED_AT))),
                )
            }
    }

    override fun searchByUsername(
        keyword: String,
        afterUsername: String?,
        limit: Int,
    ): List<Account> = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNTS.ID, ACCOUNTS.USERNAME, ACCOUNTS.CREATED_AT)
            .from(ACCOUNTS)
            .where(ACCOUNTS.USERNAME.containsIgnoreCase(keyword))
            .and(
                if (afterUsername == null) {
                    DSL.noCondition()
                } else {
                    ACCOUNTS.USERNAME.gt(afterUsername)
                },
            )
            .orderBy(ACCOUNTS.USERNAME)
            .limit(limit)
            .fetch()
            .map { record ->
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
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
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

    override fun findPublicKeyPem(username: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNT_KEYS.PUBLIC_KEY_PEM)
            .from(ACCOUNTS)
            .join(ACCOUNT_KEYS)
            .on(ACCOUNT_KEYS.ACCOUNT_ID.eq(ACCOUNTS.ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .fetchOne()
            ?.get(ACCOUNT_KEYS.PUBLIC_KEY_PEM)
    }

    override fun findPrivateKeyPem(username: String): String? = jooq.withConnection { dsl ->
        dsl
            .select(ACCOUNT_KEYS.PRIVATE_KEY_PEM)
            .from(ACCOUNTS)
            .join(ACCOUNT_KEYS)
            .on(ACCOUNT_KEYS.ACCOUNT_ID.eq(ACCOUNTS.ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .fetchOne()
            ?.get(ACCOUNT_KEYS.PRIVATE_KEY_PEM)
    }

    override fun hasPassword(id: AccountId): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(ACCOUNT_PASSWORDS, ACCOUNT_PASSWORDS.ACCOUNT_ID.eq(id.value))
    }

    override fun deletePasswordIfPasskeyExists(id: AccountId): Boolean = jooq.withConnection { dsl ->
        // 確認と削除を 1 文にし、並んだパスキー削除と合わせて両方消えることを防ぐ
        dsl
            .deleteFrom(ACCOUNT_PASSWORDS)
            .where(ACCOUNT_PASSWORDS.ACCOUNT_ID.eq(id.value))
            .and(
                DSL.exists(
                    DSL
                        .selectOne()
                        .from(PASSKEYS)
                        .where(PASSKEYS.ACCOUNT_ID.eq(id.value)),
                ),
            )
            .execute() == 1
    }
}
