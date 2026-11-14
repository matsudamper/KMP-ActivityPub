package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ServerBlockRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_SERVER_BLOCKS
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqliteServerBlockRepository(
    private val jooq: SqliteJooq,
) : ServerBlockRepository {
    override fun hosts(accountId: AccountId): List<String> = jooq.withConnection { dsl ->
        dsl.select(ACCOUNT_SERVER_BLOCKS.HOST).from(ACCOUNT_SERVER_BLOCKS)
            .where(ACCOUNT_SERVER_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .orderBy(ACCOUNT_SERVER_BLOCKS.CREATED_AT.desc(), ACCOUNT_SERVER_BLOCKS.HOST)
            .fetch { record -> checkNotNull(record.get(ACCOUNT_SERVER_BLOCKS.HOST)) }
    }

    override fun add(accountId: AccountId, host: String, createdAt: Instant) {
        jooq.withConnection { dsl ->
            dsl.insertInto(ACCOUNT_SERVER_BLOCKS)
                .set(ACCOUNT_SERVER_BLOCKS.ACCOUNT_ID, accountId.value)
                .set(ACCOUNT_SERVER_BLOCKS.HOST, host)
                .set(ACCOUNT_SERVER_BLOCKS.CREATED_AT, StoredInstant.format(createdAt))
                .onConflict(ACCOUNT_SERVER_BLOCKS.ACCOUNT_ID, ACCOUNT_SERVER_BLOCKS.HOST).doNothing()
                .execute()
        }
    }

    override fun remove(accountId: AccountId, host: String) {
        jooq.withConnection { dsl ->
            dsl.deleteFrom(ACCOUNT_SERVER_BLOCKS)
                .where(ACCOUNT_SERVER_BLOCKS.ACCOUNT_ID.eq(accountId.value))
                .and(ACCOUNT_SERVER_BLOCKS.HOST.eq(host))
                .execute()
        }
    }
}
