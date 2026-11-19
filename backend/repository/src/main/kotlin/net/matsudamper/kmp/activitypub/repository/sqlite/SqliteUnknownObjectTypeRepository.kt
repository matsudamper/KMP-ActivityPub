package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.UnknownObjectType
import net.matsudamper.kmp.activitypub.repository.UnknownObjectTypeRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.UNKNOWN_OBJECT_TYPES
import org.jooq.impl.DSL

internal class SqliteUnknownObjectTypeRepository(
    private val jooq: SqliteJooq,
) : UnknownObjectTypeRepository {
    override fun record(
        typeNames: List<String>,
        sourceUrl: String,
        seenAt: Instant,
    ) {
        val seenAtText = StoredInstant.format(seenAt)
        jooq.transaction { dsl ->
            typeNames.forEach { typeName ->
                dsl.insertInto(UNKNOWN_OBJECT_TYPES)
                    .set(UNKNOWN_OBJECT_TYPES.TYPE_NAME, typeName)
                    .set(UNKNOWN_OBJECT_TYPES.SOURCE_URL, sourceUrl)
                    .set(UNKNOWN_OBJECT_TYPES.SEEN_COUNT, 1L)
                    .set(UNKNOWN_OBJECT_TYPES.FIRST_SEEN_AT, seenAtText)
                    .set(UNKNOWN_OBJECT_TYPES.LAST_SEEN_AT, seenAtText)
                    .onConflict(UNKNOWN_OBJECT_TYPES.TYPE_NAME, UNKNOWN_OBJECT_TYPES.SOURCE_URL).doUpdate()
                    .set(UNKNOWN_OBJECT_TYPES.SEEN_COUNT, UNKNOWN_OBJECT_TYPES.SEEN_COUNT.plus(1L))
                    .set(UNKNOWN_OBJECT_TYPES.LAST_SEEN_AT, seenAtText)
                    .execute()
            }
        }
    }

    override fun page(
        beforeId: Long?,
        limit: Int,
    ): List<UnknownObjectType> = jooq.withConnection { dsl ->
        dsl.selectFrom(UNKNOWN_OBJECT_TYPES)
            .where(if (beforeId == null) DSL.noCondition() else UNKNOWN_OBJECT_TYPES.ID.lt(beforeId))
            .orderBy(UNKNOWN_OBJECT_TYPES.ID.desc())
            .limit(limit)
            .fetch { record ->
                UnknownObjectType(
                    id = checkNotNull(record.id),
                    typeName = checkNotNull(record.typeName),
                    sourceUrl = checkNotNull(record.sourceUrl),
                    seenCount = checkNotNull(record.seenCount).toInt(),
                    firstSeenAt = StoredInstant.parse(checkNotNull(record.firstSeenAt)),
                    lastSeenAt = StoredInstant.parse(checkNotNull(record.lastSeenAt)),
                )
            }
    }
}
