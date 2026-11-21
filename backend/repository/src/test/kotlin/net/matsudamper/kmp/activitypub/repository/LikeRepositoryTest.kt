package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class LikeRepositoryTest {
    @Test
    fun likeIsRecordedOnceAndShownOnlyToTheAccountThatLiked() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val target = assertNotNull(likes.target(alice, member))
        assertEquals(LikeTarget.Remote(objectUri = "$ACTOR/notes/1", inbox = "$ACTOR/inbox"), target)

        assertTrue(likes.likeAndEnqueue(like(alice, member, "like-1")))
        assertFalse(likes.likeAndEnqueue(like(alice, member, "like-2")))

        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(member)).single().likedByMe)
        assertEquals(false, repositories.timelineRepository.hydrate(bob, listOf(member)).single().likedByMe)
        assertEquals(listOf(likeBody("like-1")), repositories.deliveryQueueRepository.claim(NOW, 10, setOf()).map { it.body })
    }

    @Test
    fun likesOfReturnsTheEmojiOnlyForTheAccountThatReacted() = withRepository { repositories, alice, bob ->
        val remoteMember = receiveNote(repositories, alice, bob)
        val localMember = postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val likes = repositories.likeRepository
        likes.likeAndEnqueue(like(alice, remoteMember, "like-1", reaction = LikeReaction(emoji = "👍", customEmojiId = null)))
        likes.likeLocal(alice, localMember, NOW, null)

        assertEquals(
            mapOf(
                remoteMember to AccountLike(reactionEmoji = "👍", customEmojiPublicId = null),
                localMember to AccountLike(reactionEmoji = null, customEmojiPublicId = null),
            ),
            likes.likesOf(alice, setOf(remoteMember, localMember)),
        )
        assertEquals(mapOf(), likes.likesOf(bob, setOf(remoteMember, localMember)))
    }

    @Test
    fun localReactionsAreStampsInTheOrderFirstReactedAndNotFavourites() = withRepository { repositories, alice, bob ->
        val member = postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val likes = repositories.likeRepository
        val kotlin = assertNotNull(repositories.customEmojiRepository.add(customEmoji(shortcode = "kotlin", publicId = "kotlin-image", uploadedBy = bob)))
        likes.likeLocal(bob, member, NOW, LikeReaction(emoji = "🎉", customEmojiId = null))
        likes.likeLocal(alice, member, NOW.plusSeconds(1), LikeReaction(emoji = ":kotlin:", customEmojiId = kotlin.id))

        val post = repositories.timelineRepository.hydrate(bob, listOf(member)).single()

        assertEquals(
            listOf(StampCount("🎉", null, 1, null), StampCount(":kotlin:", null, 1, "kotlin-image")),
            post.stamps,
        )
        assertEquals(0L, post.favouriteCount)
        assertEquals(true, post.likedByMe)
    }

    @Test
    fun reactionKeepsNoImageWhenTheEmojiIsReplacedUnderTheSameShortcode() = withRepository { repositories, alice, bob ->
        val member = postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val emojis = repositories.customEmojiRepository
        val old = assertNotNull(emojis.add(customEmoji(shortcode = "kotlin", publicId = "old-image", uploadedBy = bob)))
        repositories.likeRepository.likeLocal(alice, member, NOW, LikeReaction(emoji = ":kotlin:", customEmojiId = old.id))
        emojis.delete(old.id, bob)
        emojis.add(customEmoji(shortcode = "kotlin", publicId = "new-image", uploadedBy = bob))

        val post = repositories.timelineRepository.hydrate(bob, listOf(member)).single()

        assertEquals(listOf(StampCount(":kotlin:", null, 1, null)), post.stamps)
        assertEquals(
            mapOf(member to AccountLike(reactionEmoji = ":kotlin:", customEmojiPublicId = null)),
            repositories.likeRepository.likesOf(alice, setOf(member)),
        )
    }

    @Test
    fun unreadableOrMissingPostCannotBeLiked() = withRepository { repositories, alice, _ ->
        val directOnly = assertNotNull(repositories.timelineRepository.receive(note("direct", "direct", setOf("bob")))).member
        assertNull(repositories.likeRepository.target(alice, directOnly))
        assertNull(repositories.likeRepository.target(alice, "l999"))
    }

    @Test
    fun localPostOfAnotherAccountAndOwnPostCanBeLikedWithoutDelivery() = withRepository { repositories, alice, bob ->
        val member = postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val likes = repositories.likeRepository
        assertEquals(LikeTarget.Local(PublicNoteId("bob-note")), likes.target(alice, member))

        assertTrue(likes.likeLocal(alice, member, NOW, null))
        assertFalse(likes.likeLocal(alice, member, NOW, null))
        assertTrue(likes.likeLocal(bob, member, NOW, null))

        assertEquals(true, repositories.timelineRepository.hydrate(bob, listOf(member)).single().likedByMe)
        assertEquals(2L, repositories.timelineRepository.hydrate(bob, listOf(member)).single().favouriteCount)
        assertEquals(mapOf(PublicNoteId("bob-note") to 2L), repositories.favouriteRepository.counts(setOf(PublicNoteId("bob-note"))))
        assertEquals(setOf(PublicNoteId("bob-note")), likes.likedLocalNotes(alice, setOf(PublicNoteId("bob-note"))))
        assertTrue(repositories.deliveryQueueRepository.claim(NOW, 10, setOf()).isEmpty())
    }

    @Test
    fun unlikeLocalPostRemovesOnlyThatAccountsLike() = withRepository { repositories, alice, bob ->
        val member = postLocalNote(repositories, username = "alice", publicId = "alice-note")
        val likes = repositories.likeRepository
        likes.likeLocal(alice, member, NOW, null)
        likes.likeLocal(bob, member, NOW, null)

        assertTrue(likes.unlikeLocal(alice, member))
        assertFalse(likes.unlikeLocal(alice, member))

        val post = repositories.timelineRepository.hydrate(alice, listOf(member)).single()
        assertEquals(false, post.likedByMe)
        assertEquals(1L, post.favouriteCount)
        assertEquals(setOf(), likes.likedLocalNotes(alice, setOf(PublicNoteId("alice-note"))))
    }

    @Test
    fun unlikeBeforeLikeIsSentCancelsLikeWithoutUndo() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, _ -> "undo:$likeId" })

        assertTrue(repositories.deliveryQueueRepository.claim(NOW, 10, setOf()).isEmpty())
        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(member)).single().likedByMe)
        assertFalse(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, _ -> "undo:$likeId" })
    }

    @Test
    fun unlikeAfterLikeWasSentEnqueuesUndoForThatLike() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.markDelivered(queue.claim(NOW, 10, setOf()).single().id, NOW)

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, _ -> "undo:$likeId" })

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10, setOf()).map { it.body })
    }

    @Test
    fun newLikeWaitsUntilUndoOfThePreviousLikeIsDelivered() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        val objectUri = "$ACTOR/notes/1"
        likes.likeAndEnqueue(like(alice, member, "like-1").copy(body = jsonLikeBody("like-1", objectUri)))
        queue.markDelivered(queue.claim(NOW, 10, setOf()).single().id, NOW)
        likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, uri, _ -> jsonUndoBody(likeId, uri) }
        likes.likeAndEnqueue(like(alice, member, "like-2").copy(body = jsonLikeBody("like-2", objectUri)))

        val undo = queue.claim(NOW, 10, setOf()).single()
        assertEquals(jsonUndoBody("like-1", objectUri), undo.body)

        queue.scheduleRetry(undo.id, NOW, "failed")
        assertEquals(listOf(jsonUndoBody("like-1", objectUri)), queue.claim(NOW, 10, setOf()).map { it.body })

        queue.markDelivered(undo.id, NOW)
        assertEquals(listOf(jsonLikeBody("like-2", objectUri)), queue.claim(NOW, 10, setOf()).map { it.body })
    }

    private fun jsonLikeBody(id: String, objectUri: String): String = """{"id":"$id","type":"Like","object":"$objectUri"}"""

    private fun jsonUndoBody(likeId: String, objectUri: String): String =
        """{"id":"undo-$likeId","type":"Undo","object":{"id":"$likeId","type":"Like","object":"$objectUri"}}"""

    @Test
    fun undoOfReactionReceivesTheEmojiThatWasSent() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        val kotlin = assertNotNull(repositories.customEmojiRepository.add(customEmoji(shortcode = "kotlin", publicId = "kotlin-image", uploadedBy = bob)))
        val reaction = LikeReaction(emoji = ":kotlin:", customEmojiId = kotlin.id)
        likes.likeAndEnqueue(like(alice, member, "like-1", reaction = reaction))
        queue.markDelivered(queue.claim(NOW, 10, setOf()).single().id, NOW)

        var undoneReaction: LikeReaction? = null
        assertTrue(
            likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, sentReaction ->
                undoneReaction = sentReaction
                "undo:$likeId"
            },
        )

        assertEquals(reaction, undoneReaction)
    }

    @Test
    fun unlikeWhileLikeIsRetryingCancelsRetryAndEnqueuesUndo() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.scheduleRetry(queue.claim(NOW, 10, setOf()).single().id, NOW, "接続失敗")

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, _ -> "undo:$likeId" })

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10, setOf()).map { it.body })
    }

    @Test
    fun likeBeingDeliveredIsNotRetriedAfterUnlike() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        val delivering = queue.claim(NOW, 10, setOf()).single()

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, _ -> "undo:$likeId" })
        queue.scheduleRetry(delivering.id, NOW, "接続失敗")

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10, setOf()).map { it.body })
    }

    @Test
    fun likeBeingDeliveredIsNotRecoveredAfterUnlike() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.claim(NOW, 10, setOf())

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, _, _ -> "undo:$likeId" })
        queue.recoverDelivering(NOW)

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10, setOf()).map { it.body })
    }

    @Test
    fun boostedPostIsLikedAsTheBoostedNoteEvenWithoutFollowingTheBooster() = withRepository { repositories, alice, bob ->
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null, null, listOf(), null, listOf())),
            NOW,
        )
        repositories.followingRepository.add(alice, BOOSTER, NOW)
        val boostMember = assertNotNull(
            repositories.timelineRepository.receiveBoost(
                RemoteBoostInput(
                    activityUri = "$BOOSTER/announces/1",
                    boosterUri = BOOSTER,
                    boostedAt = NOW,
                    receivedAt = NOW,
                    note = note("boosted", "public", setOf()),
                ),
            ),
        ).member
        val likes = repositories.likeRepository

        assertEquals(LikeTarget.Remote(objectUri = "$ACTOR/notes/boosted", inbox = "$ACTOR/inbox"), likes.target(alice, boostMember))
        assertEquals(LikeTarget.Remote(objectUri = "$ACTOR/notes/boosted", inbox = "$ACTOR/inbox"), likes.target(bob, boostMember))
        assertTrue(likes.likeAndEnqueue(like(alice, boostMember, "like-1")))
        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(boostMember)).single().likedByMe)
        assertEquals(listOf(likeBody("like-1")), repositories.deliveryQueueRepository.claim(NOW, 10, setOf()).map { it.body })

        assertTrue(likes.unlikeAndEnqueueUndo(alice, boostMember, NOW) { likeId, _, _ -> "undo:$likeId" })
        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(boostMember)).single().likedByMe)
    }

    @Test
    fun likeCanBeRemovedAfterUnfollowingTheAuthor() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, alice)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.markDelivered(queue.claim(NOW, 10, setOf()).single().id, NOW)
        repositories.followingRepository.remove(alice, ACTOR)

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, NOW) { likeId, objectUri, _ -> "undo:$likeId:$objectUri" })

        assertEquals(listOf("$ACTOR/inbox" to "undo:like-1:$ACTOR/notes/1"), queue.claim(NOW, 10, setOf()).map { it.inbox to it.body })
    }

    @Test
    fun publishedLikesListOnlyPublicNotesNewestFirstAndContinueFromCursor() = withRepository { repositories, alice, bob ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val publicMember = assertNotNull(repositories.timelineRepository.receive(note("public", "public", setOf()))).member
        val followersMember = assertNotNull(repositories.timelineRepository.receive(note("followers", "followers", setOf("alice")))).member
        val localMember = postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val likes = repositories.likeRepository
        likes.likeAndEnqueue(like(alice, publicMember, "like-public").copy(likedAt = NOW))
        likes.likeAndEnqueue(like(alice, followersMember, "like-followers").copy(likedAt = NOW.plusSeconds(1)))
        likes.likeLocal(alice, localMember, NOW.plusSeconds(2), null)

        val first = likes.publishedLikes("alice", before = null, limit = 1)
        val rest = likes.publishedLikes("alice", before = first.last().position, limit = 10)

        assertEquals(listOf<TimelineNoteRef>(TimelineNoteRef.Local(PublicNoteId("bob-note"))), first.map { it.noteRef })
        assertEquals(listOf<TimelineNoteRef>(TimelineNoteRef.Remote("$ACTOR/notes/public")), rest.map { it.noteRef })
        assertEquals(2L, likes.countPublishedLikes("alice"))
        assertEquals(listOf(), likes.publishedLikes("bob", before = null, limit = 10))
    }

    @Test
    fun likedPostsShowOnlyPostsTheViewerCanOpen() = withRepository { repositories, alice, bob ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val publicMember = assertNotNull(repositories.timelineRepository.receive(note("public", "public", setOf()))).member
        val directMember = assertNotNull(repositories.timelineRepository.receive(note("direct", "direct", setOf("alice")))).member
        val likes = repositories.likeRepository
        likes.likeAndEnqueue(like(alice, publicMember, "like-public").copy(likedAt = NOW))
        likes.likeAndEnqueue(like(alice, directMember, "like-direct").copy(likedAt = NOW.plusSeconds(1)))

        assertEquals(
            listOf(directMember, publicMember),
            repositories.timelineRepository.likedPosts(liker = alice, viewer = alice, before = null, limit = 10).map { it.post.position.member },
        )
        assertEquals(
            listOf(publicMember),
            repositories.timelineRepository.likedPosts(liker = alice, viewer = bob, before = null, limit = 10).map { it.post.position.member },
        )
    }

    private fun postLocalNote(repositories: Repositories, username: String, publicId: String): String {
        repositories.deliveryQueueRepository.enqueueNote(
            NotePost(
                username = username,
                requestId = "request-$publicId",
                publicId = PublicNoteId(publicId),
                contentHtml = "<p>本文</p>",
                summary = null,
                sourceText = "",
                publishedAt = NOW,
                images = listOf(),
                body = "create",
                inboxes = listOf(),
                inReplyTo = null,
                quoteUri = null,
                quoteInbox = null,
                mentions = listOf(),
                sensitive = false,
                poll = null,
            ),
        )
        return checkNotNull(repositories.timelineRepository.localPosition(publicId)).member
    }

    private fun receiveNote(repositories: Repositories, vararg followers: AccountId): String {
        followers.forEach { repositories.followingRepository.add(it, ACTOR, NOW) }
        return assertNotNull(repositories.timelineRepository.receive(note("1", "public", setOf()))).member
    }

    private fun customEmoji(shortcode: String, publicId: String, uploadedBy: AccountId): NewLocalCustomEmoji = NewLocalCustomEmoji(
        shortcode = shortcode,
        publicId = publicId,
        path = "users/${uploadedBy.value}/custom-emojis/$publicId",
        contentType = "image/png",
        uploadedBy = uploadedBy,
        createdAt = NOW,
    )

    private fun like(accountId: AccountId, member: String, likeId: String, reaction: LikeReaction? = null): OutgoingLike = OutgoingLike(
        accountId = accountId,
        member = member,
        likeId = likeId,
        inbox = "$ACTOR/inbox",
        body = likeBody(likeId),
        likedAt = NOW,
        reaction = reaction,
    )

    private fun likeBody(id: String): String = """{"id":"$id","type":"Like"}"""

    private fun note(id: String, visibility: String, recipients: Set<String>): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", language = null, visibility = visibility,
        localRecipients = recipients, inReplyTo = null, quoteUri = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW, editedAt = null,
        images = listOf(),
        emojis = listOf(),
        linkHref = null,
        poll = null,
    )

    private fun withRepository(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("like-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf(), null, listOf())),
                    NOW,
                )
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                block(repositories, alice, bob)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
        const val BOOSTER = "https://booster.example/actor"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
