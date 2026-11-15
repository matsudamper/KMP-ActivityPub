package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.FetchedNoteCounts
import net.matsudamper.kmp.activitypub.logic.RemoteNoteCounts
import net.matsudamper.kotpub.note.HttpRemoteNoteCounts
import net.matsudamper.kotpub.note.RemoteNoteCountsResult
import org.slf4j.LoggerFactory

internal class KotpubRemoteNoteCounts(
    private val noteCounts: HttpRemoteNoteCounts,
) : RemoteNoteCounts {
    private val logger = LoggerFactory.getLogger(KotpubRemoteNoteCounts::class.java)

    override suspend fun fetch(objectUri: String): FetchedNoteCounts? =
        when (val result = noteCounts.count(noteId = objectUri, maxReplyPages = MAX_REPLY_PAGES)) {
            is RemoteNoteCountsResult.Found -> FetchedNoteCounts(
                replyCount = result.replies?.count,
                favouriteCount = result.likes,
                boostCount = result.shares,
            )

            is RemoteNoteCountsResult.NotFound,
            is RemoteNoteCountsResult.Unavailable,
            -> {
                logger.warn("投稿の数を取れなかった: $objectUri result=$result")
                null
            }
        }

    private companion object {
        /**
         * Mastodon は 1 ページに 20 件ほどしか載せないので、個別投稿画面を開くたびに待たせすぎない数で打ち切る
         */
        const val MAX_REPLY_PAGES = 5
    }
}
