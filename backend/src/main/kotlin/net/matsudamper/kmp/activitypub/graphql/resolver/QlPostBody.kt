package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.model.QlPostImage
import net.matsudamper.kmp.activitypub.graphql.model.QlPostStamp
import net.matsudamper.kmp.activitypub.logic.htmlToPlainText
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage

/**
 * HomeTimelinePost と ProfilePost で投稿の中身の返し方を揃えるため、両方ここを通す。
 * 閲覧注意でも本文・リンク・画像は隠さない。どう見せるかはクライアントが summary と sensitive で決める
 */
internal class QlPostBody(
    val content: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkUrls: List<String>,
    val images: List<QlPostImage>,
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
): QlPostBody =
    QlPostBody(
        content = htmlToPlainText(contentHtml),
        summary = summary,
        sensitive = sensitive,
        // DOMAIN が無いと linkPreviewService は null。そのときはカードにするリンクを返さない
        linkUrls = if (linkUrlsEnabled) diContainer.linkPreviewService?.links(contentHtml).orEmpty() else listOf(),
        images = images.toQlPostImages(diContainer),
    )

internal fun List<StampCount>.toQlPostStamps(diContainer: DiContainer): List<QlPostStamp> = map { stamp ->
    QlPostStamp(
        emoji = stamp.emoji,
        // 画像を中継できないときは :name: の文字で見せる
        imageUrl = stamp.emojiImageUrl?.let { diContainer.remoteImageProxy?.proxyUrlOf(it) },
        count = stamp.count.toInt(),
    )
}

// Web では CORS を返さないサーバーの画像を読めないので、中継できない画像は返さない
private fun List<TimelinePostImage>.toQlPostImages(diContainer: DiContainer): List<QlPostImage> = mapNotNull { image ->
    diContainer.remoteImageProxy?.proxyUrlOf(image.url)?.let { QlPostImage(url = it, description = image.description) }
}
