package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.NotificationEntry
import net.matsudamper.kmp.activitypub.repository.NotificationEvent
import net.matsudamper.kmp.activitypub.repository.NotificationGroup
import net.matsudamper.kmp.activitypub.repository.NotificationGroupPosition
import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NotificationGroupId
import net.matsudamper.kmp.activitypub.timeline.AccountEvents

/**
 * 通知を記録して流し、通知カラムに出す形で読む
 */
class NotificationService(
    private val notifications: NotificationRepository,
    private val timeline: TimelineRepository,
    private val blockLists: BlockLists,
    private val events: AccountEvents,
) : NotificationRecorder {
    override fun record(event: NotificationEvent) {
        val groupId = notifications.record(event) ?: return
        val group = notifications.find(groupId) ?: return
        events.publishNotification(accountId = group.accountId, groupId = group.id)
    }

    /**
     * @param cursor 前回の [NotificationPage.nextCursor]。先頭から読むなら null
     */
    fun page(accountId: AccountId, cursor: String?, limit: Int): NotificationPage {
        val before = cursor?.let { NotificationCursor.parse(it) }
        require(cursor == null || before != null) { "cursor が読めない" }
        val pageSize = limit.coerceIn(1, 100)
        val groups = notifications.page(accountId, before, pageSize + 1)
        return NotificationPage(
            notifications = viewsOf(accountId, groups.take(pageSize)),
            nextCursor = if (groups.size > pageSize) NotificationCursor.format(groups[pageSize - 1].position) else null,
        )
    }

    /**
     * @return 無いか、[accountId] の通知でないか、相手が誰も残っていなければ null
     */
    fun find(accountId: AccountId, groupId: NotificationGroupId): NotificationView? {
        val group = notifications.find(groupId)?.takeIf { it.accountId == accountId } ?: return null
        return viewsOf(accountId, listOf(group)).singleOrNull()
    }

    private fun viewsOf(accountId: AccountId, groups: List<NotificationGroup>): List<NotificationView> {
        val blockList = blockLists.of(accountId)
        val members = groups.mapNotNull { it.noteMember }.distinct()
        val hydrated = timeline.hydrate(accountId, members).associateBy { it.position.member }
        // hydrate はこのサーバーの投稿を本人のものに限るので、このサーバーの別のアカウントからのメンションは開ける投稿として引き直す
        val posts = hydrated + members.filterNot { it in hydrated }.mapNotNull { member -> timeline.viewablePost(accountId, member)?.let { member to it } }
        return groups.mapNotNull { group ->
            val entries = group.entries.filterNot { blockList.hidesActor(it.actor.actorUri) }
            if (entries.isEmpty()) return@mapNotNull null
            val post = group.noteMember?.let { member -> posts[member] ?: return@mapNotNull null }
            NotificationView(
                group = group,
                recentEntries = entries.take(RECENT_ENTRY_LIMIT),
                actorCount = entries.size,
                post = post,
            )
        }
    }

    private companion object {
        const val RECENT_ENTRY_LIMIT = 5
    }
}

fun interface NotificationRecorder {
    /**
     * 記録して、受け取るアカウントの購読者へ流す
     */
    fun record(event: NotificationEvent)
}

/**
 * @param recentEntries 新しく加わった順の相手。ブロックした相手は除き、数人までにする
 * @param actorCount ブロックした相手を除いた、相手の人数
 * @param post 対象の投稿。フォローでは null
 */
data class NotificationView(
    val group: NotificationGroup,
    val recentEntries: List<NotificationEntry>,
    val actorCount: Int,
    val post: TimelinePost?,
)

data class NotificationPage(val notifications: List<NotificationView>, val nextCursor: String?)

private object NotificationCursor {
    private const val SEPARATOR = '_'

    fun format(position: NotificationGroupPosition): String = "${position.updatedAt}$SEPARATOR${position.id.value}"

    fun parse(cursor: String): NotificationGroupPosition? {
        val updatedAt = runCatching { Instant.parse(cursor.substringBefore(SEPARATOR)) }.getOrNull() ?: return null
        val id = cursor.substringAfter(SEPARATOR, missingDelimiterValue = "").toLongOrNull() ?: return null
        return NotificationGroupPosition(updatedAt = updatedAt, id = NotificationGroupId(id))
    }
}
