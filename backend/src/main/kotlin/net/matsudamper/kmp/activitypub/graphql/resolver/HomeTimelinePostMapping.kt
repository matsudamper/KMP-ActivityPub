package net.matsudamper.kmp.activitypub.graphql.resolver

import java.net.URI
import net.matsudamper.kmp.activitypub.graphql.model.QlHomeTimelinePost
import net.matsudamper.kmp.activitypub.graphql.model.QlPostImage
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.TimelinePost

/**
 * @param noteImageUrls null なら画像を出さない
 */
internal fun TimelinePost.toQlHomeTimelinePost(noteImageUrls: NoteImageUrls?): QlHomeTimelinePost =
    QlHomeTimelinePost(
        id = position.member,
        content = htmlToPlainText(contentHtml),
        publishedAt = publishedAt.toString(),
        username = username,
        host = actorUri?.let { URI(it).host },
        displayName = displayName ?: username,
        summary = summary,
        sensitive = sensitive,
        images = images.toQlPostImages(noteImageUrls),
    )

private fun List<NoteImage>.toQlPostImages(noteImageUrls: NoteImageUrls?): List<QlPostImage> {
    if (noteImageUrls == null) return listOf()
    return map { image ->
        QlPostImage(
            url = noteImageUrls.url(image.path),
            description = image.description,
            width = image.width,
            height = image.height,
        )
    }
}
