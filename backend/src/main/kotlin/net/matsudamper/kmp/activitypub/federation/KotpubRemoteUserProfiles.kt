package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.ProfilePost
import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfile
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfiles
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.note.HttpRemoteOutbox
import net.matsudamper.kotpub.note.RemoteOutboxNote
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

internal class KotpubRemoteUserProfiles(
    private val users: RemoteUserLookup,
    private val actors: RemoteActors,
    private val outbox: HttpRemoteOutbox,
) : RemoteUserProfiles {
    override suspend fun find(username: String, host: String): RemoteUserProfile? {
        val user = users.findByAcct(username, host) ?: return null
        return profileFor(user)
    }

    override suspend fun findByActorUri(actorUri: String): RemoteUserProfile? {
        val user = users.findByUrl(actorUri) ?: return null
        return profileFor(user)
    }

    override suspend fun recentPosts(actorUri: String): List<ProfilePost> =
        outbox.recent(actorUri, limit = 20).map { it.toProfilePost(pinned = false) }

    override suspend fun featuredPosts(actorUri: String): List<ProfilePost> =
        outbox.featured(actorUri, limit = 20).map { it.toProfilePost(pinned = true) }

    private fun RemoteOutboxNote.toProfilePost(pinned: Boolean): ProfilePost = ProfilePost(
        contentHtml = contentHtml,
        publishedAt = publishedAt,
        summary = summary?.let { Jsoup.clean(it, Safelist.none()) },
        sensitive = sensitive,
        // 保存していない投稿なので、後から OGP を取りに行く元も個別投稿画面で開く元も無い
        linkPreviewId = null,
        postId = null,
        deletableNoteId = null,
        pinned = pinned,
        pinnableNoteId = null,
        images = attachments.mapNotNull { attachment -> attachment.toImage() },
        emojis = emojis.toRecords(),
        favouriteCount = null,
        stamps = listOf(),
        // 保存していない投稿なので、いいねする投稿を指せない
        like = null,
        noteRef = TimelineNoteRef.Remote(id),
        boostCount = null,
        boostedNoteAuthor = null,
    )

    private suspend fun profileFor(user: RemoteUser): RemoteUserProfile? {
        val actor = actors.findActor(user.actorUri) ?: return null
        return RemoteUserProfile(user = user, headerUrl = actor.profile.headerUrl, biography = actor.profile.summary)
    }
}
