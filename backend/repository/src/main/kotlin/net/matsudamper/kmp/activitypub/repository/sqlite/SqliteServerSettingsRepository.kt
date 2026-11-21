package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.ServerSettingsRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.SERVER_SETTINGS

internal class SqliteServerSettingsRepository(
    private val jooq: SqliteJooq,
) : ServerSettingsRepository {
    override fun findMaxDeliveryTargetsPerHost(): Int? = jooq.withConnection { dsl ->
        dsl.select(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST)
            .from(SERVER_SETTINGS)
            .where(SERVER_SETTINGS.ID.eq(ROW_ID))
            .fetchOne(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST)
            ?.toInt()
    }

    override fun saveMaxDeliveryTargetsPerHost(limit: Int) {
        jooq.transaction { dsl ->
            dsl.insertInto(SERVER_SETTINGS)
                .set(SERVER_SETTINGS.ID, ROW_ID)
                .set(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST, limit.toLong())
                .onConflict(SERVER_SETTINGS.ID).doUpdate()
                .set(SERVER_SETTINGS.MAX_DELIVERY_TARGETS_PER_HOST, limit.toLong())
                .execute()
        }
    }

    private companion object {
        const val ROW_ID = 1L
    }
}
