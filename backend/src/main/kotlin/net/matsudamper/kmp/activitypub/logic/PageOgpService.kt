package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.timeline.TimelineService

/**
 * 画面の URL を他のサービスに貼ったときに出すカードの中身を返す。
 * 他のサーバーのユーザーや投稿は、このサーバーが中身を公開する立場にないので出さない
 */
class PageOgpService(
    private val domain: String,
    private val accounts: AccountRepository,
    private val timeline: TimelineService,
    private val profileIconUrls: ProfileIconUrls,
    private val noteImageUrls: NoteImageUrls,
) {
    /**
     * @param segments 画面のパスを `/` で区切ったもの
     * @return カードを出す画面でなければ null
     */
    fun find(segments: List<String>): PageOgp? {
        val nonEmptySegments = segments.filter { it.isNotEmpty() }
        val first = nonEmptySegments.firstOrNull() ?: return null
        return when {
            nonEmptySegments.size == 1 && first.startsWith("@") -> profile(first.drop(1))
            nonEmptySegments.size == 2 && first == POSTS_SEGMENT -> post(nonEmptySegments[1])
            else -> null
        }
    }

    private fun profile(acct: String): PageOgp? {
        val target = ProfileAcct.parse(acct, domain) ?: return null
        if (!target.isLocal) return null
        val account = accounts.findByUsername(target.username) ?: return null
        val biography = accounts.findProfileDetail(account.username)?.summary
        val iconUrl = profileIconUrls.find(account.username)
        return PageOgp(
            type = PageOgpType.Profile,
            title = titleOf(displayName = account.displayName, username = account.username),
            description = biography?.let { truncate(htmlToPlainText(it)) }?.ifBlank { null },
            url = "https://$domain/@${account.username}",
            image = iconUrl?.let { PageOgpImage(url = it, alt = null, large = false) },
        )
    }

    private fun post(member: String): PageOgp? {
        val post = timeline.viewablePost(viewer = null, member = member) ?: return null
        if (post.localPublicId == null || post.boostedBy != null || post.deleted) return null

        val contentWarning = post.summary?.trim()?.ifEmpty { null }
        val hidesContent = post.sensitive || contentWarning != null
        val description = when {
            contentWarning != null -> truncate(contentWarning)
            post.sensitive -> SENSITIVE_DESCRIPTION
            else -> truncate(htmlToPlainText(post.contentHtml)).ifBlank { null }
        }
        val attachedImage = if (hidesContent) null else firstAttachedImage(post)
        val iconImage = profileIconUrls.find(post.username)?.let { PageOgpImage(url = it, alt = null, large = false) }
        return PageOgp(
            type = PageOgpType.Article,
            title = titleOf(displayName = post.displayName, username = post.username),
            description = description,
            url = "https://$domain/$POSTS_SEGMENT/$member",
            image = attachedImage ?: iconImage,
        )
    }

    /**
     * 動画はカードの画像にできないので、最初の静止画を使う
     */
    private fun firstAttachedImage(post: TimelinePost): PageOgpImage? =
        post.images
            .filterIsInstance<TimelinePostImage.Local>()
            .firstOrNull { it.contentType.startsWith("image/") }
            ?.let { PageOgpImage(url = noteImageUrls.url(it.publicId), alt = it.description, large = true) }

    private fun titleOf(displayName: String?, username: String): String {
        val name = displayName?.trim()?.ifEmpty { null } ?: username
        return "$name (@$username@$domain)"
    }

    private fun truncate(text: String): String {
        val oneLine = text.replace(Regex("""\s+"""), " ").trim()
        if (oneLine.codePointCount(0, oneLine.length) <= MAX_DESCRIPTION_CODE_POINTS) return oneLine
        val end = oneLine.offsetByCodePoints(0, MAX_DESCRIPTION_CODE_POINTS - 1)
        return oneLine.substring(0, end) + "…"
    }

    private companion object {
        const val POSTS_SEGMENT = "posts"
        const val MAX_DESCRIPTION_CODE_POINTS = 200
        const val SENSITIVE_DESCRIPTION = "閲覧注意の投稿"
    }
}

/**
 * @param description 出すものが無ければ null
 * @param image 出す画像が無ければ null
 */
data class PageOgp(
    val type: PageOgpType,
    val title: String,
    val description: String?,
    val url: String,
    val image: PageOgpImage?,
)

enum class PageOgpType(val ogType: String) {
    Profile("profile"),
    Article("article"),
}

/**
 * @param alt 画像の説明文。無ければ null
 * @param large カードで画像を大きく出すか。プロフィール画像は小さく出す
 */
data class PageOgpImage(
    val url: String,
    val alt: String?,
    val large: Boolean,
)
