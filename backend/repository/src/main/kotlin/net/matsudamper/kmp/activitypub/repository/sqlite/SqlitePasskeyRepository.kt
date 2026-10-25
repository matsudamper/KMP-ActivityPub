package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.repository.StoredPasskey
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_PASSWORDS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.PASSKEYS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.PASSKEY_USER_HANDLES
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle
import org.jooq.impl.DSL

internal class SqlitePasskeyRepository(
    private val jooq: SqliteJooq,
) : PasskeyRepository {
    override fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> PasskeyUserHandle,
    ): PasskeyUserHandle = jooq.transaction { dsl ->
        val existing =
            dsl
                .select(PASSKEY_USER_HANDLES.USER_HANDLE)
                .from(PASSKEY_USER_HANDLES)
                .where(PASSKEY_USER_HANDLES.ACCOUNT_ID.eq(accountId.value))
                .fetchOne(PASSKEY_USER_HANDLES.USER_HANDLE)
        if (existing != null) return@transaction PasskeyUserHandle(existing)

        val created = newUserHandle()
        dsl
            .insertInto(PASSKEY_USER_HANDLES)
            .set(PASSKEY_USER_HANDLES.ACCOUNT_ID, accountId.value)
            .set(PASSKEY_USER_HANDLES.USER_HANDLE, created.value)
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
                    credentialId = PasskeyCredentialId(checkNotNull(record.get(PASSKEYS.CREDENTIAL_ID))),
                    name = checkNotNull(record.get(PASSKEYS.NAME)),
                    createdAt = StoredInstant.parse(checkNotNull(record.get(PASSKEYS.CREATED_AT))),
                    lastUsedAt = record.get(PASSKEYS.LAST_USED_AT)?.let { StoredInstant.parse(it) },
                )
            }
    }

    override fun add(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
        attestedCredentialData: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean = jooq.withConnection { dsl ->
        dsl
            .insertInto(PASSKEYS)
            .set(PASSKEYS.CREDENTIAL_ID, credentialId.value)
            .set(PASSKEYS.ACCOUNT_ID, accountId.value)
            .set(PASSKEYS.ATTESTED_CREDENTIAL_DATA, attestedCredentialData)
            .set(PASSKEYS.SIGN_COUNT, signCount)
            .set(PASSKEYS.NAME, name)
            .set(PASSKEYS.CREATED_AT, StoredInstant.format(createdAt))
            .onConflictDoNothing()
            .execute() == 1
    }

    override fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): PasskeyDeleteResult = jooq.withConnection { dsl ->
        val otherPasskeys = PASSKEYS.`as`("other_passkeys")
        // 確認と削除を 1 文にし、並んだ削除で最後のログイン手段まで消えることを防ぐ
        val deleted =
            dsl
                .deleteFrom(PASSKEYS)
                .where(PASSKEYS.CREDENTIAL_ID.eq(credentialId.value))
                .and(PASSKEYS.ACCOUNT_ID.eq(accountId.value))
                .and(
                    DSL
                        .exists(DSL.selectOne().from(ACCOUNT_PASSWORDS).where(ACCOUNT_PASSWORDS.ACCOUNT_ID.eq(accountId.value)))
                        .or(
                            DSL.exists(
                                DSL
                                    .selectOne()
                                    .from(otherPasskeys)
                                    .where(otherPasskeys.ACCOUNT_ID.eq(accountId.value))
                                    .and(otherPasskeys.CREDENTIAL_ID.ne(credentialId.value)),
                            ),
                        ),
                ).execute() == 1
        when {
            deleted -> {
                PasskeyDeleteResult.Deleted
            }

            dsl.fetchExists(
                PASSKEYS,
                PASSKEYS.CREDENTIAL_ID.eq(credentialId.value)
                    .and(PASSKEYS.ACCOUNT_ID.eq(accountId.value)),
            ) -> {
                PasskeyDeleteResult.LastLoginMethod
            }

            else -> {
                PasskeyDeleteResult.NotFound
            }
        }
    }

    override fun findByCredentialId(credentialId: PasskeyCredentialId): StoredPasskey? = jooq.withConnection { dsl ->
        dsl
            .select(PASSKEYS.ACCOUNT_ID, PASSKEY_USER_HANDLES.USER_HANDLE, PASSKEYS.ATTESTED_CREDENTIAL_DATA, PASSKEYS.SIGN_COUNT)
            .from(PASSKEYS)
            .leftJoin(PASSKEY_USER_HANDLES)
            .on(PASSKEY_USER_HANDLES.ACCOUNT_ID.eq(PASSKEYS.ACCOUNT_ID))
            .where(PASSKEYS.CREDENTIAL_ID.eq(credentialId.value))
            .fetchOne()
            ?.let { record ->
                StoredPasskey(
                    accountId = AccountId(checkNotNull(record.get(PASSKEYS.ACCOUNT_ID))),
                    userHandle = record.get(PASSKEY_USER_HANDLES.USER_HANDLE)?.let { PasskeyUserHandle(it) },
                    attestedCredentialData = checkNotNull(record.get(PASSKEYS.ATTESTED_CREDENTIAL_DATA)),
                    signCount = checkNotNull(record.get(PASSKEYS.SIGN_COUNT)),
                )
            }
    }

    override fun updateSignCount(
        credentialId: PasskeyCredentialId,
        signCount: Long,
        usedAt: Instant,
    ) {
        jooq.withConnection { dsl ->
            dsl
                .update(PASSKEYS)
                .set(PASSKEYS.SIGN_COUNT, DSL.greatest(PASSKEYS.SIGN_COUNT, DSL.inline(signCount)))
                .set(PASSKEYS.LAST_USED_AT, StoredInstant.format(usedAt))
                .where(PASSKEYS.CREDENTIAL_ID.eq(credentialId.value))
                .execute()
        }
    }
}
