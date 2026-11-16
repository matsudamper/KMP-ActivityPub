package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.PublishedNotePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 本文で投稿を探す。このサーバーが保存している投稿だけを見て、相手のサーバーへは探しに行かない
 */
class PostSearchService(
    private val timeline: TimelineRepository,
    private val blockLists: BlockLists,
) {
    /**
     * @param query 空白で区切ると、すべてを本文に含む投稿を探す。大文字と小文字は区別しない
     * @return 投稿した新しい順
     */
    fun search(viewer: AccountId, query: String, cursor: PostSearchCursor?): PostSearchPage {
        val keywords = query.split(whitespaces).filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return PostSearchPage(posts = listOf(), nextCursor = null)

        val blockList = blockLists.of(viewer)
        val posts = timeline.searchPosts(
            viewer = viewer,
            before = cursor?.let { PublishedNotePosition(publishedAt = Instant.parse(it.publishedAt), member = it.member) },
            limit = PAGE_SIZE,
            hidesAuthor = blockList::hidesActor,
            // タグの名前や属性に当たらないよう、画面に出る文字にしてから比べる
            matchesContent = { contentHtml ->
                val text = htmlToPlainText(contentHtml)
                keywords.all { text.contains(it, ignoreCase = true) }
            },
        )
        val last = posts.lastOrNull()
        return PostSearchPage(
            posts = posts,
            nextCursor = if (posts.size < PAGE_SIZE || last == null) {
                null
            } else {
                PostSearchCursor(publishedAt = last.publishedAt.toString(), member = last.position.member)
            },
        )
    }

    private companion object {
        const val PAGE_SIZE = 20

        // \s は全角の空白に当たらない
        val whitespaces = Regex("[\\s\\u3000]+")
    }
}

/**
 * @param nextCursor 続きを [PostSearchService.search] で読むときに渡す。続きが無ければ null
 */
data class PostSearchPage(
    val posts: List<TimelinePost>,
    val nextCursor: PostSearchCursor?,
)

/**
 * 検索結果をどこまで返したか。外には [encode] した文字列で渡す
 *
 * @param publishedAt [Instant] の文字列。桁を落とさないよう文字列で持つ
 */
@Serializable
data class PostSearchCursor(
    @SerialName("publishedAt") val publishedAt: String,
    @SerialName("member") val member: String,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): PostSearchCursor? =
            runCatching {
                Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString())
                    .takeIf { cursor -> runCatching { Instant.parse(cursor.publishedAt) }.isSuccess }
            }.getOrNull()
    }
}
