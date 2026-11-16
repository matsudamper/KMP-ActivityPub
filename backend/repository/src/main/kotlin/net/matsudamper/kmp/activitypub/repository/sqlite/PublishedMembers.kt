package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.PublishedNotePosition
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import org.jooq.Condition
import org.jooq.Cursor
import org.jooq.DSLContext
import org.jooq.Record2
import org.jooq.impl.DSL

/**
 * 読めない他のサーバーの投稿のうち、検索で出してよい公開範囲
 */
internal const val SEARCHABLE_UNREADABLE_REMOTE_NOTE_VISIBILITY = "public"

/**
 * 検索で本文を一度に読む投稿の数
 */
internal const val SEARCH_SCAN_CHUNK_SIZE = 200

/**
 * このサーバーの投稿と他のサーバーの投稿を混ぜて、投稿した新しい順に、時刻と member を返す。
 * 本文を並べ替えに巻き込まないよう、ここでは本文を読まない
 *
 * @param remoteCondition 他のサーバーの投稿を絞る条件。REMOTE_NOTES を参照できる
 * @param before この位置より古い投稿を返す。null なら最新から
 */
internal fun DSLContext.publishedMembers(remoteCondition: Condition, before: PublishedNotePosition?): Cursor<Record2<String, String>> {
    val publishedAt = DSL.field(DSL.name("published_at"), String::class.java)
    val member = DSL.field(DSL.name("member"), String::class.java)
    val local = select(NOTES.PUBLISHED_AT.`as`(publishedAt), DSL.inline("l").concat(NOTES.ID.cast(String::class.java)).`as`(member))
        .from(NOTES)
    val remote = select(REMOTE_NOTES.PUBLISHED_AT.`as`(publishedAt), DSL.inline("r").concat(REMOTE_NOTES.ID.cast(String::class.java)).`as`(member))
        .from(REMOTE_NOTES)
        .where(remoteCondition)
    val published = local.unionAll(remote).asTable("published")
    val publishedPublishedAt = DSL.field(DSL.name("published", "published_at"), String::class.java)
    val publishedMember = DSL.field(DSL.name("published", "member"), String::class.java)
    val olderThanBefore = if (before == null) {
        DSL.noCondition()
    } else {
        // 時刻は桁を揃えた文字列で持っているので、文字列のまま比べる
        val beforePublishedAt = StoredInstant.format(before.publishedAt)
        publishedPublishedAt.lt(beforePublishedAt).or(publishedPublishedAt.eq(beforePublishedAt).and(publishedMember.lt(before.member)))
    }
    return select(publishedPublishedAt, publishedMember)
        .from(published)
        .where(olderThanBefore)
        .orderBy(publishedPublishedAt.desc(), publishedMember.desc())
        .fetchLazy()
}

/**
 * [members] のうち、本文が [matchesContent] に合い、投稿者を [hidesAuthor] で隠さないものを、[members] の順のまま返す
 */
internal fun DSLContext.membersToShow(
    members: List<String>,
    hidesAuthor: (actorUri: String) -> Boolean,
    matchesContent: (contentHtml: String) -> Boolean,
): List<String> {
    val localIds = members.mapNotNull { TimelineMembers.localNoteIdOf(it) }
    val remoteIds = members.mapNotNull { TimelineMembers.remoteNoteIdOf(it) }
    val shownLocalIds = select(NOTES.ID, NOTES.CONTENT_HTML).from(NOTES).where(NOTES.ID.`in`(localIds))
        .fetch()
        .filter { matchesContent(checkNotNull(it.get(NOTES.CONTENT_HTML))) }
        .map { it.get(NOTES.ID) }
        .toSet()
    val shownRemoteIds = select(REMOTE_NOTES.ID, REMOTE_NOTES.CONTENT_HTML, REMOTE_ACTORS.ACTOR_URI).from(REMOTE_NOTES)
        .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_NOTES.REMOTE_ACTOR_ID))
        .where(REMOTE_NOTES.ID.`in`(remoteIds))
        .fetch()
        .filter { !hidesAuthor(checkNotNull(it.get(REMOTE_ACTORS.ACTOR_URI))) && matchesContent(checkNotNull(it.get(REMOTE_NOTES.CONTENT_HTML))) }
        .map { it.get(REMOTE_NOTES.ID) }
        .toSet()
    return members.filter { TimelineMembers.localNoteIdOf(it) in shownLocalIds || TimelineMembers.remoteNoteIdOf(it) in shownRemoteIds }
}
