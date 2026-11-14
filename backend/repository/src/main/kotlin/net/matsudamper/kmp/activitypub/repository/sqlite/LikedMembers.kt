package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.LikedNotePosition
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_LIKES
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL

/**
 * [liker] が付けたいいねを、このサーバーの投稿と他のサーバーの投稿を混ぜて、いいねした新しい順に返す。
 * このサーバーの投稿は誰でも読めるので絞らない
 *
 * @param remoteCondition 他のサーバーの投稿を絞る条件。REMOTE_NOTES を参照できる
 * @param before この位置より古いいいねを返す。null なら最新から
 */
internal fun DSLContext.likedMembers(
    liker: Field<Long?>,
    remoteCondition: Condition,
    before: LikedNotePosition?,
    limit: Int,
): List<LikedNotePosition> {
    val likedAt = DSL.field(DSL.name("liked_at"), String::class.java)
    val member = DSL.field(DSL.name("member"), String::class.java)
    val local = select(NOTE_LIKES.CREATED_AT.`as`(likedAt), DSL.inline("l").concat(NOTE_LIKES.NOTE_ID.cast(String::class.java)).`as`(member))
        .from(NOTE_LIKES)
        .join(NOTES).on(NOTES.ID.eq(NOTE_LIKES.NOTE_ID))
        .where(NOTE_LIKES.ACCOUNT_ID.eq(liker))
    val remote = select(REMOTE_NOTE_LIKES.CREATED_AT.`as`(likedAt), DSL.inline("r").concat(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID.cast(String::class.java)).`as`(member))
        .from(REMOTE_NOTE_LIKES)
        .join(REMOTE_NOTES).on(REMOTE_NOTES.ID.eq(REMOTE_NOTE_LIKES.REMOTE_NOTE_ID))
        .where(REMOTE_NOTE_LIKES.ACCOUNT_ID.eq(liker))
        .and(remoteCondition)
    val likes = local.unionAll(remote).asTable("likes")
    val likesLikedAt = DSL.field(DSL.name("likes", "liked_at"), String::class.java)
    val likesMember = DSL.field(DSL.name("likes", "member"), String::class.java)
    val olderThanBefore = if (before == null) {
        DSL.noCondition()
    } else {
        // 時刻は桁を揃えた文字列で持っているので、文字列のまま比べる
        val beforeLikedAt = StoredInstant.format(before.likedAt)
        likesLikedAt.lt(beforeLikedAt).or(likesLikedAt.eq(beforeLikedAt).and(likesMember.lt(before.member)))
    }
    return select(likesLikedAt, likesMember)
        .from(likes)
        .where(olderThanBefore)
        .orderBy(likesLikedAt.desc(), likesMember.desc())
        .limit(limit)
        .fetch { LikedNotePosition(likedAt = StoredInstant.parse(checkNotNull(it.value1())), member = checkNotNull(it.value2())) }
}
