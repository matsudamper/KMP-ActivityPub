package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.LikedNotePosition
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.like.LikedObject
import net.matsudamper.kotpub.like.LikedObjectStore
import net.matsudamper.kotpub.like.LikedPosition
import net.matsudamper.kotpub.note.NoteUrls

/**
 * このサーバーのアカウントがいいねした公開の投稿を、Actor の liked で他のサーバーへ見せる
 */
internal class RepositoryLikedObjectStore(
    private val domain: String,
    private val likes: LikeRepository,
) : LikedObjectStore {
    override fun list(
        username: String,
        after: LikedPosition?,
        limit: Int,
    ): List<LikedObject> =
        likes.publishedLikes(username = username, before = after?.let { LikedNotePosition(likedAt = it.at, member = it.key) }, limit = limit)
            .map { like ->
                LikedObject(
                    objectUri = when (val noteRef = like.noteRef) {
                        is TimelineNoteRef.Local -> NoteUrls(domain = domain, publicId = PublicNoteId(noteRef.publicId.value)).noteUrl
                        is TimelineNoteRef.Remote -> noteRef.objectUri
                    },
                    position = LikedPosition(at = like.position.likedAt, key = like.position.member),
                )
            }

    override fun count(username: String): Long = likes.countPublishedLikes(username)
}
