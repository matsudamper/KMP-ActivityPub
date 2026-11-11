package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowingGroup
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRenameResult
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.FOLLOWING_GROUPS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.FOLLOWING_HOME_GROUPS
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

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

    override fun rename(accountId: AccountId, groupId: FollowingGroupId, name: String): FollowingGroupRenameResult = jooq.transaction { dsl ->
        val ownGroup = FOLLOWING_GROUPS.ACCOUNT_ID.eq(accountId.value)
        if (!dsl.fetchExists(FOLLOWING_GROUPS, ownGroup.and(FOLLOWING_GROUPS.ID.eq(groupId.value)))) {
            return@transaction FollowingGroupRenameResult.NotFound
        }
        if (dsl.fetchExists(FOLLOWING_GROUPS, ownGroup.and(FOLLOWING_GROUPS.NAME.eq(name)).and(FOLLOWING_GROUPS.ID.ne(groupId.value)))) {
            return@transaction FollowingGroupRenameResult.DuplicateName
        }
        dsl.update(FOLLOWING_GROUPS)
            .set(FOLLOWING_GROUPS.NAME, name)
            .where(ownGroup).and(FOLLOWING_GROUPS.ID.eq(groupId.value))
            .execute()
        FollowingGroupRenameResult.Renamed
    }

    override fun homeName(accountId: AccountId): String? = jooq.withConnection { dsl ->
        dsl.select(FOLLOWING_HOME_GROUPS.NAME).from(FOLLOWING_HOME_GROUPS)
            .where(FOLLOWING_HOME_GROUPS.ACCOUNT_ID.eq(accountId.value))
            .fetchOne(FOLLOWING_HOME_GROUPS.NAME)
    }

    override fun setHomeName(accountId: AccountId, name: String) {
        jooq.withConnection { dsl ->
            dsl.insertInto(FOLLOWING_HOME_GROUPS)
                .set(FOLLOWING_HOME_GROUPS.ACCOUNT_ID, accountId.value)
                .set(FOLLOWING_HOME_GROUPS.NAME, name)
                .onConflict(FOLLOWING_HOME_GROUPS.ACCOUNT_ID).doUpdate()
                .set(FOLLOWING_HOME_GROUPS.NAME, name)
                .execute()
        }
    }
}
