package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowingGroup
import net.matsudamper.kmp.activitypub.repository.FollowingGroupId
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.FOLLOWING_GROUPS
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqliteFollowingGroupRepository(
    private val jooq: SqliteJooq,
) : FollowingGroupRepository {
    override fun list(accountId: AccountId): List<FollowingGroup> = jooq.withConnection { dsl ->
        dsl.select(FOLLOWING_GROUPS.ID, FOLLOWING_GROUPS.NAME).from(FOLLOWING_GROUPS)
            .where(FOLLOWING_GROUPS.ACCOUNT_ID.eq(accountId.value))
            .orderBy(FOLLOWING_GROUPS.ID)
            .fetch { record ->
                FollowingGroup(
                    id = FollowingGroupId(checkNotNull(record.get(FOLLOWING_GROUPS.ID))),
                    name = checkNotNull(record.get(FOLLOWING_GROUPS.NAME)),
                )
            }
    }

    override fun create(accountId: AccountId, name: String, createdAt: Instant): FollowingGroup = jooq.transaction { dsl ->
        dsl.insertInto(FOLLOWING_GROUPS)
            .set(FOLLOWING_GROUPS.ACCOUNT_ID, accountId.value)
            .set(FOLLOWING_GROUPS.NAME, name)
            .set(FOLLOWING_GROUPS.CREATED_AT, StoredInstant.format(createdAt))
            .onConflict(FOLLOWING_GROUPS.ACCOUNT_ID, FOLLOWING_GROUPS.NAME).doNothing()
            .execute()
        val id = dsl.select(FOLLOWING_GROUPS.ID).from(FOLLOWING_GROUPS)
            .where(FOLLOWING_GROUPS.ACCOUNT_ID.eq(accountId.value)).and(FOLLOWING_GROUPS.NAME.eq(name))
            .fetchOne(FOLLOWING_GROUPS.ID)
        FollowingGroup(id = FollowingGroupId(checkNotNull(id)), name = name)
    }

    override fun exists(accountId: AccountId, groupId: FollowingGroupId): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(FOLLOWING_GROUPS, FOLLOWING_GROUPS.ACCOUNT_ID.eq(accountId.value).and(FOLLOWING_GROUPS.ID.eq(groupId.value)))
    }
}
