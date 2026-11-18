package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.NoteEmojiObjects
import net.matsudamper.kmp.activitypub.logic.NoteImageUrls
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePosition as RepositoryNotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.PollRecord
import net.matsudamper.kmp.activitypub.repository.PollRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteAttachment
import net.matsudamper.kotpub.note.NoteMention
import net.matsudamper.kotpub.note.NotePoll
import net.matsudamper.kotpub.note.NotePollOption
import net.matsudamper.kotpub.note.NotePosition
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.note.StoredNote

internal class RepositoryNoteStore(
    private val noteRepository: NoteRepository,
    private val imageUrls: NoteImageUrls,
    private val emojiObjects: NoteEmojiObjects,
    private val polls: PollRepository,
) : NoteStore {
    override fun find(publicId: KotpubPublicNoteId): StoredNote? = listOfNotNull(noteRepository.find(publicId.toRepository())).toStored().firstOrNull()

    override fun findByPublicIds(publicIds: Set<KotpubPublicNoteId>): Map<KotpubPublicNoteId, StoredNote> =
        noteRepository
            .findByPublicIds(publicIds.map { it.toRepository() }.toSet())
            .values
            .toList()
            .toStored()
            .associateBy { it.publicId }

    override fun delete(publicId: KotpubPublicNoteId) {
        noteRepository.delete(publicId.toRepository())
    }

    override fun deleteByUsername(username: String): Int = noteRepository.deleteByUsername(username)

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<StoredNote> =
        noteRepository
            .list(username = username, after = after?.toRepository(), limit = limit)
            .toStored()

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> =
        noteRepository
            .listPositions(username = username, after = after?.toRepository(), limit = limit)
            .map { it.toStored() }

    override fun listAllPositions(
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> =
        noteRepository
            .listAllPositions(after = after?.toRepository(), limit = limit)
            .map { it.toStored() }

    override fun count(username: String): Long = noteRepository.count(username)

    override fun counts(usernames: Set<String>): Map<String, Long> = noteRepository.counts(usernames)

    private fun KotpubPublicNoteId.toRepository(): PublicNoteId = PublicNoteId(value)

    private fun NotePosition.toRepository(): RepositoryNotePosition =
        RepositoryNotePosition(publishedAt = publishedAt, publicId = publicId.toRepository())

    private fun RepositoryNotePosition.toStored(): NotePosition =
        NotePosition(publishedAt = publishedAt, publicId = KotpubPublicNoteId(publicId.value))

    /**
     * 投票は投稿ごとに引くと件数分の問い合わせになるので、まとめて引く
     */
    private fun List<Note>.toStored(): List<StoredNote> {
        val pollsByPublicId = polls.localPolls(map { it.publicId }.toSet())
        return map { note -> note.toStored(pollsByPublicId[note.publicId]) }
    }

    private fun Note.toStored(poll: PollRecord?): StoredNote =
        StoredNote(
            publicId = KotpubPublicNoteId(publicId.value),
            username = username,
            contentHtml = contentHtml,
            summary = summary,
            publishedAt = publishedAt,
            updatedAt = updatedAt,
            attachments =
            images.map { image ->
                NoteAttachment(url = imageUrls.url(image.publicId), mediaType = image.contentType, description = image.description)
            },
            inReplyTo = inReplyTo,
            quote = quoteUri,
            mentions = mentions.map { mention -> NoteMention(actorId = mention.actorUri, acct = mention.acct) },
            // 投票の選択肢にも :shortcode: を書けるので、本文と一緒に探す
            emojis = emojiObjects.find((listOf(contentHtml) + poll?.options.orEmpty().map { it.name }).joinToString("\n")),
            sensitive = sensitive,
            poll = poll?.toNotePoll(),
        )

    private fun PollRecord.toNotePoll(): NotePoll =
        NotePoll(
            options = options.map { option -> NotePollOption(name = option.name, votesCount = option.votesCount?.toInt() ?: 0) },
            multipleChoice = multipleChoice,
            endsAt = checkNotNull(endsAt) { "このサーバーの投票には締め切りがある" },
            votersCount = votersCount?.toInt() ?: 0,
        )
}
