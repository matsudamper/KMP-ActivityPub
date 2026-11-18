package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlCustomEmoji
import net.matsudamper.kmp.activitypub.graphql.model.QlPostImage
import net.matsudamper.kmp.activitypub.graphql.model.QlPostMention
import net.matsudamper.kmp.activitypub.graphql.model.QlPostStamp
import net.matsudamper.kmp.activitypub.graphql.model.QlPostVideo
import net.matsudamper.kmp.activitypub.logic.htmlToPlainTextWithMentions
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.RemoteMediaKind
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * TimelinePost と ProfilePost で投稿の中身の返し方を揃えるため、両方ここを通す。
 * 閲覧注意でも本文・リンク・画像は隠さない。どう見せるかはクライアントが summary と sensitive で決める
 */
internal class QlPostBody(
    val content: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkUrls: List<String>,
    val mentions: List<QlPostMention>,
    val images: List<QlPostImage>,
    val videos: List<QlPostVideo>,
)

/**
 * @param linkUrlsEnabled OGP を後から取りに行けない投稿は false にして、カードにするリンクを返さない
 */
internal fun createQlPostBody(
    contentHtml: String,
    summary: String?,
    sensitive: Boolean,
    images: List<TimelinePostImage>,
    linkUrlsEnabled: Boolean,
    diContainer: DiContainer,
): QlPostBody {
    val plainText = htmlToPlainTextWithMentions(contentHtml)
    return QlPostBody(
        content = plainText.text,
        summary = summary,
        sensitive = sensitive,
        // DOMAIN が無いと linkPreviewService は null。そのときはカードにするリンクを返さない
        linkUrls = if (linkUrlsEnabled) diContainer.linkPreviewService?.links(contentHtml).orEmpty() else listOf(),
        mentions = plainText.mentions.map { QlPostMention(start = it.start, end = it.end, acct = it.acct) },
        images = images.toQlPostImages(diContainer),
        videos = images.toQlPostVideos(diContainer),
    )
}

internal fun List<StampCount>.toQlPostStamps(diContainer: DiContainer): List<QlPostStamp> = map { stamp ->
    val remoteImageUrl = stamp.emojiImageUrl
    QlPostStamp(
        emoji = stamp.emoji,
        // 画像を中継できないときは :name: の文字で見せる
        imageUrl = if (remoteImageUrl != null) {
            diContainer.remoteImageProxy?.proxyUrlOf(remoteImageUrl)
        } else {
            stamp.localCustomEmojiPublicId?.let { diContainer.customEmojiUrls?.imageUrl(it) }
        },
        count = stamp.count.toInt(),
        customEmojiShortcode = if (stamp.localCustomEmojiPublicId == null) null else stamp.emoji.removeSurrounding(":"),
    )
}

// 画像を中継できないときは :name: の文字のまま見せる
internal fun List<CustomEmojiRecord>.toQlCustomEmojis(diContainer: DiContainer): List<QlCustomEmoji> = mapNotNull { emoji ->
    diContainer.remoteImageProxy?.proxyUrlOf(emoji.imageUrl)?.let { QlCustomEmoji(name = emoji.name, imageUrl = it) }
}

/**
 * このサーバーの投稿の絵文字は記録していないので、本文から引く
 *
 * @param remoteEmojis 他のサーバーの投稿に付いてきた絵文字
 */
internal fun createQlPostEmojis(
    noteRef: TimelineNoteRef,
    contentHtml: String,
    remoteEmojis: List<CustomEmojiRecord>,
    diContainer: DiContainer,
): List<QlCustomEmoji> = when (noteRef) {
    is TimelineNoteRef.Remote -> remoteEmojis.toQlCustomEmojis(diContainer)

    is TimelineNoteRef.Local -> {
        val urls = diContainer.customEmojiUrls
        if (urls == null) {
            listOf()
        } else {
            diContainer.customEmojiService?.findInNoteContent(contentHtml).orEmpty()
                .map { emoji -> QlCustomEmoji(name = ":${emoji.shortcode}:", imageUrl = urls.imageUrl(emoji.publicId)) }
        }
    }
}

internal fun createQlPostImage(url: String, description: String?): QlPostImage = QlPostImage(
    url = url,
    thumbnailUrl = url.toHttpUrl().newBuilder().addQueryParameter("type", "thumbnail").build().toString(),
    description = description,
)

// Web では CORS を返さないサーバーの画像を読めないので、中継できない画像は返さない
private fun List<TimelinePostImage>.toQlPostImages(diContainer: DiContainer): List<QlPostImage> = mapNotNull { image ->
    val url =
        when (image) {
            is TimelinePostImage.Remote ->
                when (image.mediaKind) {
                    RemoteMediaKind.Image -> diContainer.remoteImageProxy?.proxyUrlOf(image.url)
                    RemoteMediaKind.Video -> null
                }

            is TimelinePostImage.Local ->
                if (image.isVideo()) null else diContainer.noteImageUrls?.url(image.publicId)
        }
    url?.let { createQlPostImage(url = it, description = image.description) }
}

// 動画は video 要素で読むので CORS が無くても再生できる。中継はしない。
// https のページから http の動画は混在コンテンツで読めないので、https の URL だけを返す
private fun List<TimelinePostImage>.toQlPostVideos(diContainer: DiContainer): List<QlPostVideo> = mapNotNull { video ->
    val url =
        when (video) {
            is TimelinePostImage.Remote ->
                when (video.mediaKind) {
                    RemoteMediaKind.Image -> null
                    RemoteMediaKind.Video -> video.url.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()
                }

            is TimelinePostImage.Local ->
                if (video.isVideo()) diContainer.noteImageUrls?.url(video.publicId) else null
        }
    url?.let { QlPostVideo(url = it, description = video.description) }
}

private fun TimelinePostImage.Local.isVideo(): Boolean = contentType.startsWith("video/")
