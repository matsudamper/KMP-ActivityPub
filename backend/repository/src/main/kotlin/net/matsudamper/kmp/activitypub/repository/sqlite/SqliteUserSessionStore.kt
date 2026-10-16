package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.USER_SESSIONS
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqliteUserSessionStore(
    private val jooq: SqliteJooq,
) : UserSessionStore {
    /**
     * 期限切れの行もここで消す。ログアウトせずに閉じられたセッションは読み直されずに溜まる
     */
    override fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        jooq.transaction { dsl ->
            dsl
                .deleteFrom(USER_SESSIONS)
                .where(USER_SESSIONS.EXPIRES_AT.lt(StoredInstant.format(createdAt)))
                .execute()

            dsl
                .insertInto(USER_SESSIONS)
                .set(USER_SESSIONS.TOKEN_HASH, tokenHash)
                .set(USER_SESSIONS.ACCOUNT_ID, accountId.value)
                .set(USER_SESSIONS.CREATED_AT, StoredInstant.format(createdAt))
                .set(USER_SESSIONS.EXPIRES_AT, StoredInstant.format(expiresAt))
                .execute()
        }
    }

    override fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId? = jooq.withConnection { dsl ->
        dsl
            .select(USER_SESSIONS.ACCOUNT_ID)
            .from(USER_SESSIONS)
            .where(USER_SESSIONS.TOKEN_HASH.eq(tokenHash))
            .and(USER_SESSIONS.EXPIRES_AT.gt(StoredInstant.format(now)))
            .fetchOne(USER_SESSIONS.ACCOUNT_ID)
            ?.let { AccountId(it) }
    }
}
