package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.InstanceActorKeyRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.INSTANCE_ACTOR_KEY

internal class SqliteInstanceActorKeyRepository(
    private val jooq: SqliteJooq,
) : InstanceActorKeyRepository {
    override fun findOrCreatePrivateKeyPem(newPrivateKeyPem: () -> String): String = jooq.transaction { dsl ->
        val stored = dsl.select(INSTANCE_ACTOR_KEY.PRIVATE_KEY_PEM).from(INSTANCE_ACTOR_KEY)
            .where(INSTANCE_ACTOR_KEY.ID.eq(ROW_ID))
            .fetchOne(INSTANCE_ACTOR_KEY.PRIVATE_KEY_PEM)
        if (stored != null) return@transaction stored
        val created = newPrivateKeyPem()
        dsl.insertInto(INSTANCE_ACTOR_KEY)
            .set(INSTANCE_ACTOR_KEY.ID, ROW_ID)
            .set(INSTANCE_ACTOR_KEY.PRIVATE_KEY_PEM, created)
            .execute()
        created
    }

    private companion object {
        const val ROW_ID = 1L
    }
}
