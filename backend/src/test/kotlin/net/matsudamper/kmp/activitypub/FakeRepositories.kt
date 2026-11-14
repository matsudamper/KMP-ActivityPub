package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountCredential
import net.matsudamper.kmp.activitypub.repository.AccountProfileDetail
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.ActorListPosition
import net.matsudamper.kmp.activitypub.repository.FollowingGroup
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRenameResult
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.LikeTarget
import net.matsudamper.kmp.activitypub.repository.ListedActor
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.repository.OutgoingLike
import net.matsudamper.kmp.activitypub.repository.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.PasskeySummary
import net.matsudamper.kmp.activitypub.repository.ReceivedNotification
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchCursor
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.StoredPasskey
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.NotificationId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineRepository

internal class FakeRepositories : Repositories {
    override val timelineRepository = FakeTimelineRepository()
    override val followingRepository: FollowingRepository = object : FollowingRepository {
        private val entries = mutableSetOf<Pair<AccountId, String>>()
        private val activities = mutableMapOf<Pair<AccountId, String>, String>()
        override fun followId(accountId: AccountId, actorUri: String): String? = activities[accountId to actorUri]
        override fun recordActivity(accountId: AccountId, actorUri: String, followId: String) {
            activities[accountId to actorUri] = followId
        }
        override fun recordActivityAndEnqueueFollow(
            accountId: AccountId,
            actorUri: String,
            followId: String,
            inbox: String,
            body: String,
            now: Instant,
        ): Boolean {
            if (!contains(accountId, actorUri)) return false
            recordActivity(accountId, actorUri, followId)
            deliveryQueueRepository.enqueueFollow(accountId, inbox, body, now)
            return true
        }
        override fun respondToFollow(actorUri: String, followId: String, acceptedAt: Instant?): Int {
            val matches = entries.filter { it.second == actorUri && activities[it] == followId }
            if (acceptedAt == null) entries.removeAll(matches.toSet())
            return matches.size
        }
        override fun contains(accountId: AccountId, actorUri: String): Boolean = (accountId to actorUri) in entries
        override fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean = entries.add(accountId to actorUri)
        private val groups = mutableMapOf<Pair<AccountId, String>, FollowingGroupId>()
        override fun remove(accountId: AccountId, actorUri: String): Boolean {
            groups.remove(accountId to actorUri)
            return entries.remove(accountId to actorUri)
        }
        override fun groupIdOf(accountId: AccountId, actorUri: String): FollowingGroupId? = groups[accountId to actorUri]
        override fun groupIdsAmong(accountId: AccountId, actorUris: Set<String>): Map<String, FollowingGroupId?> =
            actorUris.filter { contains(accountId, it) }.associateWith { groups[accountId to it] }
        override fun setGroup(accountId: AccountId, actorUri: String, groupId: FollowingGroupId?): Boolean {
            if (!contains(accountId, actorUri)) return false
            if (groupId == null) groups.remove(accountId to actorUri) else groups[accountId to actorUri] = groupId
            return true
        }
        override fun list(accountId: AccountId, after: ActorListPosition?, limit: Int): List<ListedActor> = throw NotImplementedError()
        override fun count(accountId: AccountId): Long = entries.count { it.first == accountId }.toLong()
    }
    override val followingGroupRepository: FollowingGroupRepository = object : FollowingGroupRepository {
        private val rows = mutableListOf<Pair<AccountId, FollowingGroup>>()
        override fun list(accountId: AccountId): List<FollowingGroup> = rows.filter { it.first == accountId }.map { it.second }
        override fun create(accountId: AccountId, name: String, createdAt: Instant): FollowingGroup {
            val existing = list(accountId).firstOrNull { it.name == name }
            if (existing != null) return existing
            val group = FollowingGroup(id = FollowingGroupId(rows.size + 1L), name = name)
            rows += accountId to group
            return group
        }
        override fun exists(accountId: AccountId, groupId: FollowingGroupId): Boolean = list(accountId).any { it.id == groupId }
        override fun rename(accountId: AccountId, groupId: FollowingGroupId, name: String): FollowingGroupRenameResult {
            val index = rows.indexOfFirst { it.first == accountId && it.second.id == groupId }.takeIf { it >= 0 }
                ?: return FollowingGroupRenameResult.NotFound
            if (list(accountId).any { it.name == name && it.id != groupId }) return FollowingGroupRenameResult.DuplicateName
            rows[index] = accountId to FollowingGroup(id = groupId, name = name)
            return FollowingGroupRenameResult.Renamed
        }
        private val mainGroupNames = mutableMapOf<AccountId, String>()
        override fun mainGroupName(accountId: AccountId): String? = mainGroupNames[accountId]
        override fun setMainGroupName(accountId: AccountId, name: String) {
            mainGroupNames[accountId] = name
        }
    }
    override val accountRepository: FakeAccountRepository = FakeAccountRepository(hasPasskey = { passkeyRepository.passkeys(it).isNotEmpty() })
    override val followerRepository: FakeFollowerRepository = FakeFollowerRepository()
    override val noteRepository: FakeNoteRepository = FakeNoteRepository()
    override val notePinRepository: FakeNotePinRepository = FakeNotePinRepository()
    override val remoteActorRepository: FakeRemoteActorRepository = FakeRemoteActorRepository()
    override val favouriteRepository: FakeFavouriteRepository = FakeFavouriteRepository()
    override val stampRepository: FakeStampRepository = FakeStampRepository()
    override val likeRepository: LikeRepository = object : LikeRepository {
        override fun target(accountId: AccountId, member: String): LikeTarget? = null
        override fun likeLocal(accountId: AccountId, member: String, likedAt: Instant): Boolean = false
        override fun unlikeLocal(accountId: AccountId, member: String): Boolean = false
        override fun likedLocalNotes(accountId: AccountId, notePublicIds: Set<PublicNoteId>): Set<PublicNoteId> = setOf()
        override fun likeAndEnqueue(like: OutgoingLike): Boolean = false
        override fun unlikeAndEnqueueUndo(
            accountId: AccountId,
            member: String,
            now: Instant,
            undoBody: (likeId: String, objectUri: String) -> String,
        ): Boolean = false
    }
    override val accountBoostRepository: FakeAccountBoostRepository = FakeAccountBoostRepository()
    override val boostRepository: FakeBoostRepository = FakeBoostRepository()
    override val notificationRepository: NotificationRepository = object : NotificationRepository {
        override fun page(accountId: AccountId, after: NotificationId?, limit: Int): List<ReceivedNotification> = listOf()
        override fun find(kind: NotificationKind, notePublicId: PublicNoteId, actorUri: String): ReceivedNotification? = null
    }
    override val deliveryQueueRepository: FakeDeliveryQueueRepository = FakeDeliveryQueueRepository(noteRepository)

    override val passkeyRepository: PasskeyRepository = FakePasskeyRepository(hasPassword = { accountRepository.hasPassword(it) })

    override fun close() = Unit
}

internal class FakeAccountRepository(
    private val hasPasskey: (AccountId) -> Boolean,
) : AccountRepository {
    /**
     * いいねを公開しているアカウントの名前。小文字で入れる
     */
    val likesPublicUsernames: MutableSet<String> = mutableSetOf()

    private val rows = mutableListOf<AccountCredential>()
    private val passwordDeletedIds = mutableSetOf<AccountId>()
    private val publicKeyPems = mutableMapOf<AccountId, String>()
    private val iconPaths = mutableMapOf<AccountId, String>()

    fun add(
        username: String,
        passwordHash: String,
        passwordHashType: String,
    ): AccountId {
        val id = AccountId(rows.size + 1L)
        rows += AccountCredential(id = id, username = username, passwordHash = passwordHash, passwordHashType = passwordHashType)
        return id
    }

    fun addPublicKeyPem(
        id: AccountId,
        publicKeyPem: String,
    ) {
        publicKeyPems[id] = publicKeyPem
    }

    fun setIconPath(
        id: AccountId,
        iconPath: String,
    ) {
        iconPaths[id] = iconPath
    }

    override fun findById(id: AccountId): Account? =
        rows.firstOrNull { it.id == id }?.toAccount()

    override fun countAll(): Long = rows.size.toLong()

    override fun findByUsername(username: String): Account? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.toAccount()

    override fun searchByUsername(
        keyword: String,
        afterUsername: String?,
        limit: Int,
    ): List<Account> =
        rows
            .filter { it.username.contains(keyword, ignoreCase = true) }
            .filter { afterUsername == null || it.username.lowercase() > afterUsername.lowercase() }
            .sortedBy { it.username.lowercase() }
            .take(limit)
            .map { it.toAccount() }

    override fun findCredential(username: String): AccountCredential? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.takeIf { it.id !in passwordDeletedIds }

    override fun findProfileDetail(username: String): AccountProfileDetail? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.let {
            AccountProfileDetail(summary = null, links = listOf(), likesPublic = it.username.lowercase() in likesPublicUsernames)
        }

    override fun findPublicKeyPem(username: String): String? =
        rows.firstOrNull { it.username.lowercase() == username.lowercase() }?.let { publicKeyPems[it.id] }

    override fun findPrivateKeyPem(username: String): String? = null

    override fun hasPassword(id: AccountId): Boolean = rows.any { it.id == id } && id !in passwordDeletedIds

    override fun deletePasswordIfPasskeyExists(id: AccountId): Boolean {
        if (hasPassword(id).not() || hasPasskey(id).not()) return false
        passwordDeletedIds += id
        return true
    }

    private fun AccountCredential.toAccount(): Account =
        Account(id = id, username = username, createdAt = Instant.EPOCH, displayName = null, iconPath = iconPaths[id])
}

internal class FakeRemoteActorRepository : RemoteActorRepository {
    val savedActors = mutableMapOf<String, RemoteActorRecord>()
    private val rows = mutableListOf<RemoteActorSearchRecord>()

    override fun findByUri(actorUri: String): RemoteActorRecord? = savedActors[actorUri]

    override fun remove(actorUri: String) {
        savedActors.remove(actorUri)
        rows.removeAll { it.actorUri == actorUri }
    }

    override fun save(actor: RemoteActorRecord, updatedAt: Instant) {
        savedActors[actor.actorUri] = actor
        val username = actor.profile.preferredUsername ?: return
        rows.removeAll { it.actorUri == actor.actorUri }
        rows.add(RemoteActorSearchRecord(actor.actorUri, username, actor.profile.displayName, actor.profile.iconUrl, listOf(), updatedAt))
    }

    override fun findByAcct(username: String, host: String): RemoteActorSearchRecord? =
        rows.firstOrNull { it.preferredUsername == username && it.actorUri.substringAfter("://").substringBefore('/') == host }

    fun add(record: RemoteActorSearchRecord) {
        rows += record
    }

    override fun listActorUris(after: String?, limit: Int): List<String> =
        (savedActors.keys + rows.map { it.actorUri }).toSortedSet().filter { after == null || it > after }.take(limit)

    override fun count(): Long = (savedActors.keys + rows.map { it.actorUri }).toSet().size.toLong()

    override fun searchByName(
        keyword: String,
        after: RemoteActorSearchCursor?,
        limit: Int,
    ): List<RemoteActorSearchRecord> =
        rows
            .filter { it.preferredUsername.contains(keyword, ignoreCase = true) || it.displayName?.contains(keyword, ignoreCase = true) == true }
            .sortedWith(compareBy<RemoteActorSearchRecord> { it.preferredUsername.sqliteLower() }.thenBy { it.actorUri })
            .filter { record ->
                after == null ||
                    compareValuesBy(
                        record,
                        RemoteActorSearchRecord(
                            actorUri = after.actorUri,
                            preferredUsername = after.preferredUsername,
                            displayName = null,
                            iconUrl = null,
                            updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
                            emojis = listOf(),
                        ),
                        { it.preferredUsername.sqliteLower() },
                        { it.actorUri },
                    ) > 0
            }.take(limit)
}

private class FakePasskeyRepository(
    private val hasPassword: (AccountId) -> Boolean,
) : PasskeyRepository {
    private val userHandles = mutableMapOf<AccountId, PasskeyUserHandle>()
    private val passkeys = mutableMapOf<PasskeyCredentialId, StoredPasskey>()
    private val names = mutableMapOf<PasskeyCredentialId, String>()

    override fun userHandle(
        accountId: AccountId,
        newUserHandle: () -> PasskeyUserHandle,
    ): PasskeyUserHandle = userHandles.getOrPut(accountId, newUserHandle)

    override fun passkeys(accountId: AccountId): List<PasskeySummary> =
        passkeys
            .filterValues { it.accountId == accountId }
            .keys
            .map { PasskeySummary(credentialId = it, name = names.getValue(it), createdAt = Instant.EPOCH, lastUsedAt = null) }

    override fun add(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
        attestedCredentialData: ByteArray,
        signCount: Long,
        name: String,
        createdAt: Instant,
    ): Boolean {
        if (credentialId in passkeys) return false
        passkeys[credentialId] = StoredPasskey(accountId = accountId, userHandle = userHandles[accountId], attestedCredentialData = attestedCredentialData, signCount = signCount)
        names[credentialId] = name
        return true
    }

    override fun delete(
        accountId: AccountId,
        credentialId: PasskeyCredentialId,
    ): PasskeyDeleteResult {
        if (passkeys[credentialId]?.accountId != accountId) return PasskeyDeleteResult.NotFound
        if (hasPassword(accountId).not() && passkeys(accountId).size <= 1) return PasskeyDeleteResult.LastLoginMethod
        passkeys.remove(credentialId)
        names.remove(credentialId)
        return PasskeyDeleteResult.Deleted
    }

    override fun findByCredentialId(credentialId: PasskeyCredentialId): StoredPasskey? = passkeys[credentialId]

    override fun updateSignCount(
        credentialId: PasskeyCredentialId,
        signCount: Long,
        usedAt: Instant,
    ) {
        val passkey = passkeys[credentialId] ?: return
        passkeys[credentialId] = StoredPasskey(passkey.accountId, passkey.userHandle, passkey.attestedCredentialData, maxOf(passkey.signCount, signCount))
    }
}

/**
 * SQLite の lower() と同じく ASCII だけを小文字にする。並び順を本物の SQLite に揃える
 */
private fun String.sqliteLower(): String = map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")
