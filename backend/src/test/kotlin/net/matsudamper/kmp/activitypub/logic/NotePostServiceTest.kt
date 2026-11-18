package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.FakeDeliveryQueueRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.FakeImageStorage
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.FakeRemoteActorRepository
import net.matsudamper.kmp.activitypub.federation.RepositoryNoteStore
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NoteMention
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.ReplyTarget
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.emoji.EmojiObject
import net.matsudamper.kotpub.json.AppJson
import net.matsudamper.kotpub.note.NotePublisher

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class NotePostServiceTest {
    private val followerRepository = FakeFollowerRepository()
    private val noteRepository = FakeNoteRepository()
    private val deliveryQueueRepository = FakeDeliveryQueueRepository(noteRepository)
    private var enqueuedCount = 0
    private val timelinePosts = mutableListOf<PublicNoteId>()
    private val editedNotes = mutableListOf<PublicNoteId>()
    private val imageStorage = FakeImageStorage()
    private var now: Instant = NOW
    private val imageUrls = NoteImageUrls(DOMAIN)
    private val remoteActorRepository = FakeRemoteActorRepository()
    private val remoteUserLookups = mutableListOf<String>()
    private val remoteUsers =
        object : RemoteUserLookup {
            override suspend fun findByAcct(
                username: String,
                host: String,
            ): RemoteUser? {
                remoteUserLookups += "$username@$host"
                if (username != "alice" || host != "remote.example") return null
                remoteActorRepository.save(ALICE, NOW)
                return RemoteUser(actorUri = REMOTE_ACTOR, username = "alice", displayName = null, iconUrl = null, displayNameEmojis = listOf())
            }

            override suspend fun findByUrl(url: String): RemoteUser? = null
        }
    private val accountRepository = FakeAccountRepository(hasPasskey = { false }).apply { add(username = "user1", passwordHash = "hash", passwordHashType = "type") }

    private val service =
        NotePostService(
            directory = ActorDirectory(domain = DOMAIN, stored = LocalUsers),
            publisher = NotePublisher(RepositoryNoteStore(noteRepository, imageUrls, BlobcatOnly), null),
            accountService = AccountService(accountRepository),
            noteRepository = noteRepository,
            imageStorage = imageStorage,
            imageUrls = imageUrls,
            emojiObjects = BlobcatOnly,
            followerRepository = followerRepository,
            remoteActors = remoteActorRepository,
            remoteUsers = remoteUsers,
            deliveryQueueRepository = deliveryQueueRepository,
            clock = object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId?): Clock = this

                override fun instant(): Instant = now
            },
            onEnqueued = { enqueuedCount++ },
            onPosted = { timelinePosts.add(it) },
            onEdited = { editedNotes.add(it) },
        )

    @Test
    fun `投稿すると本文を HTML にしてフォロワーの inbox ごとに積む`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)

        val result = service.post(
            username = "user1",
            body = "<b>hello</b>\nworld",
            requestId = REQUEST_ID,
            contentWarning = null,
            imageIds = listOf(),
            replyToPostId = null,
            quotePostId = null,
            mentionAcct = null,
        )

        val posted = assertIs<PostNoteResult.Posted>(result)
        assertTrue(posted.url.startsWith("https://$DOMAIN/"))
        val post = deliveryQueueRepository.posts.single()
        assertEquals("user1", post.username)
        assertEquals(posted.publicId, post.publicId)
        assertEquals("<p>&lt;b&gt;hello&lt;/b&gt;<br>world</p>", post.contentHtml)
        assertEquals(listOf(INBOX_A, INBOX_B), post.inboxes)
        assertTrue(post.body.contains("\"Create\""))
        assertEquals(1, enqueuedCount)
        assertEquals(1, timelinePosts.size)
    }

    @Test
    fun `本文に入れたこのサーバーのカスタム絵文字を、配る Note に Emoji として載せる`() = runBlocking<Unit> {
        service.post(
            username = "user1",
            body = "hello :blobcat: :unknown:",
            requestId = REQUEST_ID,
            contentWarning = null,
            imageIds = listOf(),
            replyToPostId = null,
            quotePostId = null,
            mentionAcct = null,
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals("<p>hello :blobcat: :unknown:</p>", post.contentHtml)
        val tag = AppJson.parseToJsonElement(post.body).jsonObject.getValue("object").jsonObject.getValue("tag").jsonArray.single().jsonObject
        assertEquals("Emoji", tag.getValue("type").jsonPrimitive.content)
        assertEquals(":blobcat:", tag.getValue("name").jsonPrimitive.content)
        assertEquals(BLOBCAT.icon.url, tag.getValue("icon").jsonObject.getValue("url").jsonPrimitive.content)
    }

    @Test
    fun `閲覧注意の注意書きを前後の空白を除いて記録し、配る Note に載せる`() = runBlocking<Unit> {
        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = " ネタバレ \n", imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals("ネタバレ", post.summary)
        val note = AppJson.parseToJsonElement(post.body).jsonObject.getValue("object").jsonObject
        assertEquals("ネタバレ", note.getValue("summary").jsonPrimitive.content)
        assertEquals("true", note.getValue("sensitive").jsonPrimitive.content)
    }

    @Test
    fun `空白だけの注意書きなら閲覧注意にしない`() = runBlocking<Unit> {
        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = " \n ", imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        assertEquals(null, deliveryQueueRepository.posts.single().summary)
    }

    @Test
    fun `長すぎる注意書きは積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.ContentWarningTooLong,
            service.post(
                username = "user1",
                body = "hello",
                requestId = REQUEST_ID,
                contentWarning = "あ".repeat(NotePostService.MAX_CONTENT_WARNING_LENGTH + 1),
                imageIds = listOf(),
                replyToPostId = null,
                quotePostId = null,
                mentionAcct = null,
            ),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `空の本文は積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.EmptyBody,
            service.post(username = "user1", body = " \n ", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `長すぎる本文は積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.TooLong,
            service.post(
                username = "user1",
                body = "あ".repeat(NoteComposer.MAX_LENGTH + 1),
                requestId = REQUEST_ID,
                contentWarning = null,
                imageIds = listOf(),
                replyToPostId = null,
                quotePostId = null,
                mentionAcct = null,
            ),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `アクターとして扱えないアカウントでは積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.AccountNotFound,
            service.post(username = "other", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `同じ requestId で送り直されたら最初の投稿の URL を返して積み直さない`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A)

        val first =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )
        val resent =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )

        assertEquals(first, resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
        assertEquals(1, enqueuedCount)
        assertEquals(1, timelinePosts.size)
    }

    @Test
    fun `置いた画像を付けた順に記録し、配る Note に自分のサーバーの URL で載せる`() = runBlocking<Unit> {
        val png = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, description = " 猫 ")))
        val jpeg = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(JPEG, description = " ")))

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "画像", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(png, jpeg), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        val images = deliveryQueueRepository.posts.single().images
        assertEquals(listOf(png, jpeg), images.map { it.publicId })
        assertEquals(listOf("image/png", "image/jpeg"), images.map { it.contentType })
        assertEquals(listOf("猫", null), images.map { it.description })
        assertEquals(listOf(PNG.toList(), JPEG.toList()), images.map { imageStorage.images.getValue(ImagePath(it.path)).bytes.toList() })
        assertTrue(images.all { it.path.startsWith("2026/11/users/1/note-images/") })
        assertEquals(listOf(), noteRepository.uploadedImages)

        val attachment = (AppJson.parseToJsonElement(deliveryQueueRepository.posts.single().body) as JsonObject)
            .getValue("object").jsonObject.getValue("attachment").jsonArray
        assertEquals(images.map { imageUrls.url(it.publicId) }, attachment.map { it.jsonObject.getValue("url").jsonPrimitive.content })
        assertTrue(images.all { imageUrls.url(it.publicId).startsWith("https://$DOMAIN/media/") })
    }

    @Test
    fun `置いた画像には期限を付ける`() {
        uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))

        assertEquals(NOW.plus(NotePostService.UPLOADED_IMAGE_LIFETIME), noteRepository.uploadedImages.single().expiresAt)
    }

    @Test
    fun `画像があれば本文は空でもよい`() = runBlocking<Unit> {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = " ", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(image), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        assertEquals("", deliveryQueueRepository.posts.single().contentHtml)
    }

    @Test
    fun `画像が多すぎたら積まない`() = runBlocking<Unit> {
        val images = List(NotePostService.MAX_IMAGES + 1) { uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null))) }

        assertEquals(
            PostNoteResult.TooManyImages,
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = images, replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `置いていないか期限を過ぎた画像は付けられない`() = runBlocking<Unit> {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))
        now = NOW.plus(NotePostService.UPLOADED_IMAGE_LIFETIME)

        assertEquals(
            PostNoteResult.ImageNotFound,
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(image), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )
        assertEquals(
            PostNoteResult.ImageNotFound,
            service.post(
                username = "user1",
                body = "hello",
                requestId = REQUEST_ID,
                contentWarning = null,
                imageIds = listOf(NoteImagePublicId("missing")),
                replyToPostId = null,
                quotePostId = null,
                mentionAcct = null,
            ),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `動画は先頭のバイト列で判定した形式で記録し、配る Note の mediaType にする`() = runBlocking<Unit> {
        val mp4 = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(MP4, null)))

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "動画", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(mp4), replyToPostId = null, quotePostId = null, mentionAcct = null),
        )

        assertEquals(listOf("video/mp4"), deliveryQueueRepository.posts.single().images.map { it.contentType })
        val attachment = (AppJson.parseToJsonElement(deliveryQueueRepository.posts.single().body) as JsonObject)
            .getValue("object").jsonObject.getValue("attachment").jsonArray
        assertEquals(listOf("video/mp4"), attachment.map { it.jsonObject.getValue("mediaType").jsonPrimitive.content })
    }

    @Test
    fun `HEIC のように ftyp で始まっても動画の brand でなければ置かない`() {
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".encodeToByteArray()

        assertEquals(UploadNoteImageResult.UnsupportedImage, service.uploadImage(username = "user1", image = noteImageUpload(heic, null)))
    }

    @Test
    fun `画像として読めないものや大きすぎるもの、長すぎる説明文は置かない`() {
        assertEquals(UploadNoteImageResult.UnsupportedImage, service.uploadImage(username = "user1", image = noteImageUpload("<svg/>".encodeToByteArray(), null)))
        assertEquals(UploadNoteImageResult.TooLarge, service.uploadImage(username = "user1", image = noteImageUpload(PNG + ByteArray(NotePostService.MAX_IMAGE_BYTES), null)))
        val longDescription = "あ".repeat(NotePostService.MAX_DESCRIPTION_LENGTH + 1)
        assertEquals(UploadNoteImageResult.DescriptionTooLong, service.uploadImage(username = "user1", image = noteImageUpload(PNG, longDescription)))

        assertTrue(imageStorage.images.isEmpty())
        assertEquals(listOf(), noteRepository.uploadedImages)
    }

    @Test
    fun `同じ requestId で送り直されたら、画像が投稿に付いた後でも最初の投稿を返す`() = runBlocking<Unit> {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))
        val first = service.post(
            username = "user1",
            body = "hello",
            requestId = REQUEST_ID,
            contentWarning = null,
            imageIds = listOf(image),
            replyToPostId = null,
            quotePostId = null,
            mentionAcct = null,
        )
        val resent = service.post(
            username = "user1",
            body = "hello",
            requestId = REQUEST_ID,
            contentWarning = null,
            imageIds = listOf(image),
            replyToPostId = null,
            quotePostId = null,
            mentionAcct = null,
        )

        assertEquals(first, resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
    }

    @Test
    fun `投稿を消すと画像を置き場から消し、フォロワーの inbox ごとに Delete を積む`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(image), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )
        enqueuedCount = 0

        assertEquals(DeleteNoteResult.Deleted, service.delete(username = "user1", publicId = posted.publicId))

        assertTrue(imageStorage.images.isEmpty())
        assertEquals(null, noteRepository.find(posted.publicId))
        val deletion = deliveryQueueRepository.noteDeletions.single()
        assertEquals(listOf(INBOX_A, INBOX_B), deletion.inboxes)
        assertEquals("Delete", AppJson.parseToJsonElement(deletion.body).jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(1, enqueuedCount)
    }

    @Test
    fun `画像を置き場から消せなければ投稿を残し、Delete も積まない`() = runBlocking<Unit> {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(image), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )
        imageStorage.deleteFails = true

        assertEquals(DeleteNoteResult.ImageDeleteFailed, service.delete(username = "user1", publicId = posted.publicId))

        assertEquals(posted.publicId, noteRepository.find(posted.publicId)?.publicId)
        assertEquals(listOf(), deliveryQueueRepository.noteDeletions)
    }

    @Test
    fun `無い投稿は消せない`() {
        assertEquals(DeleteNoteResult.NotFound, service.delete(username = "user1", publicId = PublicNoteId("missing")))

        assertEquals(listOf(), deliveryQueueRepository.noteDeletions)
    }

    @Test
    fun `投稿を編集すると本文を書き換え、フォロワーの inbox ごとに Update を積む`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )
        enqueuedCount = 0

        assertEquals(EditNoteResult.Edited, service.edit(username = "user1", publicId = posted.publicId, body = "<b>edited</b>\nworld"))

        assertEquals("<p>&lt;b&gt;edited&lt;/b&gt;<br>world</p>", noteRepository.find(posted.publicId)?.contentHtml)
        assertEquals("<b>edited</b>\nworld", service.editableBody(username = "user1", publicId = posted.publicId))
        val update = deliveryQueueRepository.noteUpdates.single()
        assertEquals(listOf(INBOX_A, INBOX_B), update.inboxes)
        val activity = AppJson.parseToJsonElement(update.body).jsonObject
        assertEquals("Update", activity.getValue("type").jsonPrimitive.content)
        assertEquals(update.contentHtml, activity.getValue("object").jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals(1, enqueuedCount)
        assertEquals(listOf(posted.publicId), editedNotes)
    }

    @Test
    fun `空の本文や長すぎる本文には編集しない`() = runBlocking<Unit> {
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )

        assertEquals(EditNoteResult.EmptyBody, service.edit(username = "user1", publicId = posted.publicId, body = " \n "))
        assertEquals(EditNoteResult.TooLong, service.edit(username = "user1", publicId = posted.publicId, body = "あ".repeat(NoteComposer.MAX_LENGTH + 1)))

        assertEquals("<p>hello</p>", noteRepository.find(posted.publicId)?.contentHtml)
        assertEquals(listOf(), deliveryQueueRepository.noteUpdates)
    }

    @Test
    fun `他のアカウントの投稿は編集できず、下書きも出さない`() = runBlocking<Unit> {
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )
        val othersNote = assertIs<Note>(noteRepository.find(posted.publicId)).copy(username = "user2")
        noteRepository.notes.clear()
        noteRepository.notes += othersNote

        assertEquals(EditNoteResult.NotFound, service.edit(username = "user1", publicId = posted.publicId, body = "edited"))
        assertEquals(null, service.editableBody(username = "user1", publicId = posted.publicId))
        assertEquals(listOf(), deliveryQueueRepository.noteUpdates)
    }

    @Test
    fun `書いたままの本文が無い投稿は、HTML から戻した本文を下書きに出す`() = runBlocking<Unit> {
        val body = "a & <b>\nc\n\nhttps://example.com/x"
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = body, requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = null),
            )
        val recorded = assertIs<Note>(noteRepository.find(posted.publicId))
        noteRepository.notes.clear()
        noteRepository.notes += recorded.copy(sourceText = null)

        assertEquals(body, service.editableBody(username = "user1", publicId = posted.publicId))
    }

    @Test
    fun `他のサーバーの投稿への返信を編集しても、投稿者に宛てたまま投稿者の inbox にも積む`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A)
        noteRepository.replyTargets["r1"] =
            ReplyTarget.Remote(
                objectUri = "https://remote.example/notes/1",
                actorUri = REMOTE_ACTOR,
                preferredUsername = "alice",
                profileUrl = null,
                inbox = REMOTE_INBOX,
            )
        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(
                    username = "user1",
                    body = "@alice@remote.example こんにちは",
                    requestId = REQUEST_ID,
                    contentWarning = null,
                    imageIds = listOf(),
                    replyToPostId = "r1",
                    quotePostId = null,
                    mentionAcct = null,
                ),
            )

        assertEquals(EditNoteResult.Edited, service.edit(username = "user1", publicId = posted.publicId, body = "@alice@remote.example こんばんは"))

        val update = deliveryQueueRepository.noteUpdates.single()
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), update.inboxes)
        assertEquals(
            "<p><span class=\"h-card\" translate=\"no\"><a href=\"$REMOTE_ACTOR\" class=\"u-url mention\">@<span>alice</span></a></span> こんばんは</p>",
            update.contentHtml,
        )
        val note = AppJson.parseToJsonElement(update.body).jsonObject.getValue("object").jsonObject
        assertEquals("https://remote.example/notes/1", note.getValue("inReplyTo").jsonPrimitive.content)
        assertEquals(REMOTE_ACTOR, note.getValue("tag").jsonArray.single().jsonObject.getValue("href").jsonPrimitive.content)
    }

    @Test
    fun `他のサーバーの投稿に返信すると、投稿者に宛てて投稿者の inbox にも積み、消すときも伝える`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A)
        noteRepository.replyTargets["r1"] =
            ReplyTarget.Remote(
                objectUri = "https://remote.example/notes/1",
                actorUri = REMOTE_ACTOR,
                preferredUsername = "alice",
                profileUrl = "https://remote.example/@alice",
                inbox = REMOTE_INBOX,
            )

        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(
                    username = "user1",
                    body = "@alice@remote.example こんにちは",
                    requestId = REQUEST_ID,
                    contentWarning = null,
                    imageIds = listOf(),
                    replyToPostId = "r1",
                    quotePostId = null,
                    mentionAcct = null,
                ),
            )

        val post = deliveryQueueRepository.posts.single()
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), post.inboxes)
        assertEquals("https://remote.example/notes/1", post.inReplyTo)
        assertEquals(listOf(NoteMention(actorUri = REMOTE_ACTOR, acct = "alice@remote.example", inbox = REMOTE_INBOX)), post.mentions)
        assertEquals(
            "<p><span class=\"h-card\" translate=\"no\"><a href=\"https://remote.example/@alice\" class=\"u-url mention\">@<span>alice</span></a></span> こんにちは</p>",
            post.contentHtml,
        )
        val note = AppJson.parseToJsonElement(post.body).jsonObject.getValue("object").jsonObject
        assertEquals("https://remote.example/notes/1", note.getValue("inReplyTo").jsonPrimitive.content)
        assertEquals(REMOTE_ACTOR, note.getValue("tag").jsonArray.single().jsonObject.getValue("href").jsonPrimitive.content)

        assertEquals(DeleteNoteResult.Deleted, service.delete(username = "user1", publicId = posted.publicId))
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), deliveryQueueRepository.noteDeletions.single().inboxes)
    }

    @Test
    fun `本文からメンションを消しても投稿者には宛てる`() = runBlocking<Unit> {
        noteRepository.replyTargets["r1"] =
            ReplyTarget.Remote(
                objectUri = "https://remote.example/notes/1",
                actorUri = REMOTE_ACTOR,
                preferredUsername = "alice",
                profileUrl = null,
                inbox = REMOTE_INBOX,
            )

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "こんにちは", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = "r1", quotePostId = null, mentionAcct = null),
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals("<p>こんにちは</p>", post.contentHtml)
        assertEquals(listOf(REMOTE_INBOX), post.inboxes)
        assertEquals(listOf(REMOTE_ACTOR), post.mentions.map { it.actorUri })
    }

    @Test
    fun `自分の投稿への返信は自分に宛てない`() = runBlocking<Unit> {
        noteRepository.replyTargets["l1"] = ReplyTarget.Local(username = "user1", publicId = PublicNoteId("own"))

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "続き", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = "l1", quotePostId = null, mentionAcct = null),
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals("https://$DOMAIN/notes/own", post.inReplyTo)
        assertEquals(listOf(), post.mentions)
        assertEquals("<p>続き</p>", post.contentHtml)
    }

    @Test
    fun `保存していない他のサーバーのユーザーに宛てると、相手のサーバーから引いて宛て、本文をリンクにして inbox にも積む`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A)

        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(
                    username = "user1",
                    body = "@alice@remote.example こんにちは",
                    requestId = REQUEST_ID,
                    contentWarning = null,
                    imageIds = listOf(),
                    replyToPostId = null,
                    quotePostId = null,
                    mentionAcct = "@alice@remote.example",
                ),
            )

        assertEquals(listOf("alice@remote.example"), remoteUserLookups)
        val post = deliveryQueueRepository.posts.single()
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), post.inboxes)
        assertEquals(null, post.inReplyTo)
        assertEquals(listOf(NoteMention(actorUri = REMOTE_ACTOR, acct = "alice@remote.example", inbox = REMOTE_INBOX)), post.mentions)
        assertEquals(
            "<p><span class=\"h-card\" translate=\"no\"><a href=\"https://remote.example/@alice\" class=\"u-url mention\">@<span>alice</span></a></span> こんにちは</p>",
            post.contentHtml,
        )
        val note = AppJson.parseToJsonElement(post.body).jsonObject.getValue("object").jsonObject
        assertEquals(REMOTE_ACTOR, note.getValue("tag").jsonArray.single().jsonObject.getValue("href").jsonPrimitive.content)

        assertEquals(DeleteNoteResult.Deleted, service.delete(username = "user1", publicId = posted.publicId))
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), deliveryQueueRepository.noteDeletions.single().inboxes)
    }

    @Test
    fun `保存済みの他のサーバーのユーザーに宛てるときは相手のサーバーに引きに行かない`() = runBlocking<Unit> {
        remoteActorRepository.save(ALICE, NOW)

        assertIs<PostNoteResult.Posted>(
            service.post(
                username = "user1",
                body = "こんにちは",
                requestId = REQUEST_ID,
                contentWarning = null,
                imageIds = listOf(),
                replyToPostId = null,
                quotePostId = null,
                mentionAcct = "alice@remote.example",
            ),
        )

        assertEquals(listOf(), remoteUserLookups)
        val post = deliveryQueueRepository.posts.single()
        assertEquals(listOf(REMOTE_INBOX), post.inboxes)
        assertEquals(listOf(REMOTE_ACTOR), post.mentions.map { it.actorUri })
    }

    @Test
    fun `返信先の投稿者に宛てても、メンションを重ねない`() = runBlocking<Unit> {
        noteRepository.replyTargets["r1"] =
            ReplyTarget.Remote(
                objectUri = "https://remote.example/notes/1",
                actorUri = REMOTE_ACTOR,
                preferredUsername = "alice",
                profileUrl = "https://remote.example/@alice",
                inbox = REMOTE_INBOX,
            )

        assertIs<PostNoteResult.Posted>(
            service.post(
                username = "user1",
                body = "こんにちは",
                requestId = REQUEST_ID,
                contentWarning = null,
                imageIds = listOf(),
                replyToPostId = "r1",
                quotePostId = null,
                mentionAcct = "alice@remote.example",
            ),
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals(listOf(REMOTE_INBOX), post.inboxes)
        assertEquals(listOf(REMOTE_ACTOR), post.mentions.map { it.actorUri })
    }

    @Test
    fun `このサーバーのユーザーに宛てると、ドメイン無しの本文をリンクにし、inbox には積まない`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A)

        assertIs<PostNoteResult.Posted>(
            service.post(
                username = "user1",
                body = "@user2 こんにちは",
                requestId = REQUEST_ID,
                contentWarning = null,
                imageIds = listOf(),
                replyToPostId = null,
                quotePostId = null,
                mentionAcct = "user2@$DOMAIN",
            ),
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals(listOf(INBOX_A), post.inboxes)
        assertEquals(listOf(NoteMention(actorUri = "https://$DOMAIN/users/user2", acct = "user2@$DOMAIN", inbox = null)), post.mentions)
        assertEquals(
            "<p><span class=\"h-card\" translate=\"no\"><a href=\"https://$DOMAIN/users/user2\" class=\"u-url mention\">@<span>user2</span></a></span> こんにちは</p>",
            post.contentHtml,
        )
        assertEquals(listOf(), remoteUserLookups)
    }

    @Test
    fun `自分に宛てても宛先にしない`() = runBlocking<Unit> {
        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "メモ", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = "user1"),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts.single().mentions)
    }

    @Test
    fun `宛てるユーザーが見つからなければ積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.MentionTargetNotFound,
            service.post(
                username = "user1",
                body = "hello",
                requestId = REQUEST_ID,
                contentWarning = null,
                imageIds = listOf(),
                replyToPostId = null,
                quotePostId = null,
                mentionAcct = "bob@remote.example",
            ),
        )
        assertEquals(
            PostNoteResult.MentionTargetNotFound,
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = null, mentionAcct = "nobody"),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `返信できない投稿には積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.ReplyTargetNotFound,
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = "r999", quotePostId = null, mentionAcct = null),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `他のサーバーの投稿を引用すると、引用先を載せて投稿者の inbox にも積み、宛てはしないが消すときは伝える`() = runBlocking<Unit> {
        followerRepository.deliveryInboxes += listOf(INBOX_A)
        noteRepository.replyTargets["r1"] =
            ReplyTarget.Remote(
                objectUri = "https://remote.example/notes/1",
                actorUri = REMOTE_ACTOR,
                preferredUsername = "alice",
                profileUrl = null,
                inbox = REMOTE_INBOX,
            )

        val posted =
            assertIs<PostNoteResult.Posted>(
                service.post(username = "user1", body = "これ", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = "r1", mentionAcct = null),
            )

        val post = deliveryQueueRepository.posts.single()
        assertEquals("https://remote.example/notes/1", post.quoteUri)
        assertEquals(REMOTE_INBOX, post.quoteInbox)
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), post.inboxes)
        assertEquals(listOf(), post.mentions)
        val note = AppJson.parseToJsonElement(post.body).jsonObject.getValue("object").jsonObject
        assertEquals("https://remote.example/notes/1", note.getValue("quote").jsonPrimitive.content)

        assertEquals(DeleteNoteResult.Deleted, service.delete(username = "user1", publicId = posted.publicId))
        assertEquals(listOf(INBOX_A, REMOTE_INBOX), deliveryQueueRepository.noteDeletions.single().inboxes)
    }

    @Test
    fun `このサーバーの投稿の引用は投稿の URL を引用先にする`() = runBlocking<Unit> {
        noteRepository.replyTargets["l1"] = ReplyTarget.Local(username = "user1", publicId = PublicNoteId("own"))

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = "続き", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = "l1", mentionAcct = null),
        )

        val post = deliveryQueueRepository.posts.single()
        assertEquals("https://$DOMAIN/notes/own", post.quoteUri)
        assertEquals(listOf(), post.inboxes)
    }

    @Test
    fun `引用なら本文が空でも積む`() = runBlocking<Unit> {
        noteRepository.replyTargets["l1"] = ReplyTarget.Local(username = "user1", publicId = PublicNoteId("own"))

        assertIs<PostNoteResult.Posted>(
            service.post(username = "user1", body = " ", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = "l1", mentionAcct = null),
        )

        assertEquals("", deliveryQueueRepository.posts.single().contentHtml)
    }

    @Test
    fun `引用できない投稿は積まない`() = runBlocking<Unit> {
        assertEquals(
            PostNoteResult.QuoteTargetNotFound,
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, contentWarning = null, imageIds = listOf(), replyToPostId = null, quotePostId = "r999", mentionAcct = null),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    private fun uploaded(result: UploadNoteImageResult): NoteImagePublicId = assertIs<UploadNoteImageResult.Uploaded>(result).publicId

    private object LocalUsers : StoredActorNames {
        override fun find(username: String): String? = username.takeIf { it == "user1" || it == "user2" }

        override fun finds(usernames: Set<String>): Map<String, String> =
            usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
    }

    private object BlobcatOnly : NoteEmojiObjects {
        override fun find(contentHtml: String): List<EmojiObject> = if (":blobcat:" in contentHtml) listOf(BLOBCAT) else listOf()
    }

    private companion object {
        const val DOMAIN = "example.com"
        val BLOBCAT = EmojiObject(
            id = "https://example.com/emojis/1",
            name = ":blobcat:",
            icon = EmojiObject.Icon(url = "https://example.com/custom-emojis/1", mediaType = "image/png"),
        )
        const val INBOX_A = "https://a.example/inbox"
        const val INBOX_B = "https://b.example/inbox"
        const val REQUEST_ID = "request-1"
        const val REMOTE_ACTOR = "https://remote.example/users/alice"
        const val REMOTE_INBOX = "https://remote.example/users/alice/inbox"
        val ALICE = RemoteActorRecord(
            actorUri = REMOTE_ACTOR,
            inbox = REMOTE_INBOX,
            sharedInbox = "https://remote.example/inbox",
            publicKeyPem = "pem",
            profile = RemoteActorProfileRecord(
                preferredUsername = "alice",
                displayName = null,
                profileUrl = "https://remote.example/@alice",
                iconUrl = null,
                headerUrl = null,
                emojis = listOf(),
                summary = null,
                fields = listOf(),
            ),
        )
        val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 2)
        val MP4 = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".encodeToByteArray() + byteArrayOf(3)
    }
}

private fun noteImageUpload(
    bytes: ByteArray,
    description: String?,
): NoteImageUpload = NoteImageUpload(size = bytes.size.toLong(), openStream = { bytes.inputStream() }, description = description)
