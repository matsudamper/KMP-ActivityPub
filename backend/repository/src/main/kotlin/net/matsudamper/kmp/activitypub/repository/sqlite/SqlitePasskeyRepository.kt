package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.repository.StoredPasskey
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.PASSKEYS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.PASSKEY_USER_HANDLES
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqlitePasskeyRepository(
    private val jooq: SqliteJooq,
) : PasskeyRepository {
    override fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> String,
    ): String = jooq.transaction { dsl ->
        val existing =
            dsl
                .select(PASSKEY_USER_HANDLES.USER_HANDLE)
                .from(PASSKEY_USER_HANDLES)
                .where(PASSKEY_USER_HANDLES.ACCOUNT_ID.eq(accountId.value))
                .fetchOne(PASSKEY_USER_HANDLES.USER_HANDLE)
        if (existing != null) return@transaction existing

        val created = newUserHandle()
        dsl
            .insertInto(PASSKEY_USER_HANDLES)
            .set(PASSKEY_USER_HANDLES.ACCOUNT_ID, accountId.value)
            .set(PASSKEY_USER_HANDLES.USER_HANDLE, created)
            .execute()
        created
    }

    override fun passkeys(accountId: AccountId): List<PasskeySummary> = jooq.withConnection { dsl ->
        dsl
            .select(PASSKEYS.CREDENTIAL_ID, PASSKEYS.NAME, PASSKEYS.CREATED_AT, PASSKEYS.LAST_USED_AT)
            .from(PASSKEYS)
            .where(PASSKEYS.ACCOUNT_ID.eq(accountId.value))
            .orderBy(PASSKEYS.CREATED_AT, PASSKEYS.CREDENTIAL_ID)
            .fetch { record ->
                PasskeySummary(
                    credentialId = checkNotNull(record.get(PASSKEYS.CREDENTIAL_ID)),
                    name = checkNotNull(record.get(PASSKEYS.NAME)),
                    createdAt = StoredInstant.parse(checkNotNull(record.get(PASSKEYS.CREATED_AT))),
                    lastUsedAt = record.get(PASSKEYS.LAST_USED_AT)?.let { StoredInstant.parse(it) },
                )
            }
    }

    override fun add(
        accountId: AccountId,
        credentialId: String,
        publicKey: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean = jooq.withConnection { dsl ->
        dsl
            .insertInto(PASSKEYS)
            .set(PASSKEYS.CREDENTIAL_ID, credentialId)
            .set(PASSKEYS.ACCOUNT_ID, accountId.value)
            .set(PASSKEYS.PUBLIC_KEY, publicKey)
            .set(PASSKEYS.SIGN_COUNT, signCount)
            .set(PASSKEYS.NAME, name)
            .set(PASSKEYS.CREATED_AT, StoredInstant.format(createdAt))
            .onConflictDoNothing()
            .execute() == 1
    }

    override fun delete(
        accountId: AccountId,
        credentialId: String,
    ): Boolean = jooq.withConnection { dsl ->
        dsl
            .deleteFrom(PASSKEYS)
            .where(PASSKEYS.CREDENTIAL_ID.eq(credentialId))
            .and(PASSKEYS.ACCOUNT_ID.eq(accountId.value))
            .execute() == 1
    }

    override fun findByCredentialId(credentialId: String): StoredPasskey? = jooq.withConnection { dsl ->
        dsl
            .select(PASSKEYS.ACCOUNT_ID, PASSKEY_USER_HANDLES.USER_HANDLE, PASSKEYS.PUBLIC_KEY, PASSKEYS.SIGN_COUNT)
            .from(PASSKEYS)
            .leftJoin(PASSKEY_USER_HANDLES)
            .on(PASSKEY_USER_HANDLES.ACCOUNT_ID.eq(PASSKEYS.ACCOUNT_ID))
            .where(PASSKEYS.CREDENTIAL_ID.eq(credentialId))
            .fetchOne()
            ?.let { record ->
                StoredPasskey(
                    accountId = AccountId(checkNotNull(record.get(PASSKEYS.ACCOUNT_ID))),
                    userHandle = record.get(PASSKEY_USER_HANDLES.USER_HANDLE),
                    publicKey = checkNotNull(record.get(PASSKEYS.PUBLIC_KEY)),
                    signCount = checkNotNull(record.get(PASSKEYS.SIGN_COUNT)),
                )
            }
    }

    override fun updateSignCount(
        credentialId: String,
        signCount: Long,
        usedAt: Instant,
    ) {
        jooq.withConnection { dsl ->
            dsl
                .update(PASSKEYS)
                .set(PASSKEYS.SIGN_COUNT, signCount)
                .set(PASSKEYS.LAST_USED_AT, StoredInstant.format(usedAt))
                .where(PASSKEYS.CREDENTIAL_ID.eq(credentialId))
                .execute()
        }
    }
}
