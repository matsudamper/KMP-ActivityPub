package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class TimelineRepositoryTest {
    @Test
    fun removedFollowingCannotRecordOrEnqueueFollow() = withRepository { repositories, accounts ->
        val alice = accounts.getValue("alice")
        val following = repositories.followingRepository
        val queue = repositories.deliveryQueueRepository
        following.add(alice, ACTOR, NOW)
        following.remove(alice, ACTOR)
        assertEquals(false, following.recordActivityAndEnqueueFollow(alice, ACTOR, "removed", "$ACTOR/inbox", "removed-follow", NOW))
        assertNull(following.followId(alice, ACTOR))
        assertTrue(queue.claim(NOW, 10).isEmpty())
        following.add(alice, ACTOR, NOW)
        assertTrue(following.recordActivityAndEnqueueFollow(alice, ACTOR, "current", "$ACTOR/inbox", "current-follow", NOW))
        assertEquals("current", following.followId(alice, ACTOR))
        assertEquals(listOf("current-follow"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun fanoutActivityWindowIncludesCacheTtlAndThrottledTouchInterval() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val activityAges = mapOf(
            "alice" to 7 * 24 * 60 * 60L + 30 * 60,
            "bob" to 7 * 24 * 60 * 60L + 60 * 60,
            "carol" to 7 * 24 * 60 * 60L + 60 * 60 + 1,
        )
        activityAges.forEach { (username, age) ->
            val account = accounts.getValue(username)
            repositories.followingRepository.add(account, ACTOR, NOW)
            val lastActive = NOW.minusSeconds(age)
            repository.touch(account, lastActive, TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
            repository.touch(account, lastActive.plusSeconds(50 * 60), TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        }
        val position = assertNotNull(repository.receive(note("activity-boundary", "public", setOf())))
        assertEquals(
            listOf(accounts.getValue("alice"), accounts.getValue("bob")),
            repository.recipients(position.member, NOW.minusSeconds(TimelineCachePolicy.FANOUT_ACTIVE_WINDOW_SECONDS), null, 1000),
        )
    }

    @Test
    fun onlyFollowedOrAddressedNotesAreSavedOnceAndDeletedByTheirAuthor() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        assertNull(repository.receive(note("1", "public", setOf())))
        repositories.followingRepository.add(accounts.getValue("alice"), ACTOR, NOW)
        val saved = assertNotNull(repository.receive(note("1", "public", setOf())))
        assertTrue(repository.hasRemoteNote("$ACTOR/notes/1"))
        assertNull(repository.receive(note("1", "public", setOf())))
        assertEquals(listOf(saved), repository.page(accounts.getValue("alice"), null, 10))
        assertTrue(repository.page(accounts.getValue("bob"), null, 10).isEmpty())
        assertNotNull(repository.receive(note("2", "direct", setOf("bob"))))
        repository.deleteRemote("https://other.example/actor", "$ACTOR/notes/1")
        assertEquals(1, repository.page(accounts.getValue("alice"), null, 10).size)
        repository.deleteRemote(ACTOR, "$ACTOR/notes/1")
        assertEquals(false, repository.hasRemoteNote("$ACTOR/notes/1"))
        assertTrue(repository.hydrate(accounts.getValue("alice"), listOf(saved.member)).isEmpty())
    }

    @Test
    fun hydratedRemoteNoteCarriesNoteAndAuthorCustomEmojis() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val authorEmoji = CustomEmojiRecord(name = ":author:", imageUrl = "https://remote.example/author.png")
        repositories.remoteActorRepository.save(
            RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote :author:", null, null, null, listOf(authorEmoji))),
            NOW,
        )
        val noteEmoji = CustomEmojiRecord(name = ":blob:", imageUrl = "https://remote.example/blob.png")
        val position = assertNotNull(repository.receive(note("emoji", "public", setOf()).copy(emojis = listOf(noteEmoji))))

        val post = repository.hydrate(alice, listOf(position.member)).single()

        assertEquals(listOf(noteEmoji), post.emojis)
        assertEquals(listOf(authorEmoji), post.actorEmojis)
    }

    @Test
    fun actorPostsReturnOnlyThatActorsReadableNotesNewestFirst() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val older = assertNotNull(repository.receive(note("older", "public", setOf())))
        val newer = assertNotNull(repository.receive(note("newer", "public", setOf())))
        assertEquals(listOf(newer.member, older.member), repository.actorPosts(alice, ACTOR, 10).map { it.position.member })
        assertEquals(listOf(newer.member), repository.actorPosts(alice, ACTOR, 1).map { it.position.member })
        assertTrue(repository.actorPosts(alice, "https://other.example/actor", 10).isEmpty())
        assertTrue(repository.actorPosts(accounts.getValue("bob"), ACTOR, 10).isEmpty())
    }

    @Test
    fun receivedImagesAreReturnedInOrderWithTheirPost() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val images = listOf(
            TimelinePostImage.Remote(url = "https://remote.example/a.png", description = "説明"),
            TimelinePostImage.Remote(url = "https://remote.example/b.png", description = null),
        )
        val withImages = assertNotNull(repository.receive(note("with-images", "public", setOf()).copy(images = images)))
        val withoutImages = assertNotNull(repository.receive(note("without-images", "public", setOf())))
        val hydrated = repository.hydrate(alice, listOf(withImages.member, withoutImages.member)).associateBy { it.position.member }
        assertEquals(images, hydrated.getValue(withImages.member).images)
        assertTrue(hydrated.getValue(withoutImages.member).images.isEmpty())
        assertEquals(images, repository.actorPosts(alice, ACTOR, 10).single { it.position == withImages }.images)
    }

    @Test
    fun fanoutUsesRecentFollowersAndExplicitRecipientsWithPrivateVisibility() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        listOf("alice", "bob", "carol").forEach { repositories.followingRepository.add(accounts.getValue(it), ACTOR, NOW) }
        repository.touch(accounts.getValue("alice"), NOW, TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        repository.touch(accounts.getValue("bob"), NOW.minusSeconds(604800), TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        repository.touch(accounts.getValue("carol"), NOW.minusSeconds(604801), TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        val public = assertNotNull(repository.receive(note("public", "public", setOf("dave"))))
        assertEquals(listOf("alice", "bob", "dave").map { accounts.getValue(it) }, repository.recipients(public.member, NOW.minusSeconds(604800), null, 1000))
        listOf("alice", "bob", "carol").forEach { username ->
            val account = accounts.getValue(username)
            repositories.followingRepository.recordActivity(account, ACTOR, username)
            repositories.followingRepository.respondToFollow(ACTOR, username, NOW)
        }
        val followers = assertNotNull(repository.receive(note("followers", "followers", setOf())))
        assertEquals(listOf("alice", "bob").map { accounts.getValue(it) }, repository.recipients(followers.member, NOW.minusSeconds(604800), null, 1000))
        val direct = assertNotNull(repository.receive(note("direct", "direct", setOf("dave"))))
        assertEquals(listOf(accounts.getValue("dave")), repository.recipients(direct.member, NOW.minusSeconds(604800), null, 1000))
        assertTrue(repository.hydrate(accounts.getValue("alice"), listOf(direct.member)).isEmpty())
        repositories.followingRepository.remove(accounts.getValue("alice"), ACTOR)
        assertTrue(repository.hydrate(accounts.getValue("alice"), listOf(followers.member)).isEmpty())
    }

    @Test
    fun followersOnlyNotesRequireApprovalAtReceiptAndAtReadTime() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val following = repositories.followingRepository
        val alice = accounts.getValue("alice")
        val bob = accounts.getValue("bob")
        listOf(alice, bob).forEach { account ->
            following.add(account, ACTOR, NOW)
            following.recordActivity(account, ACTOR, "follow-${account.value}")
            repository.touch(account, NOW, TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        }
        following.respondToFollow(ACTOR, "follow-${alice.value}", NOW)
        val first = assertNotNull(repository.receive(note("first-private", "followers", setOf())))
        assertEquals(listOf(alice), repository.recipients(first.member, NOW, null, 1000))
        assertEquals(listOf(first), repository.page(alice, null, 10))
        assertEquals(listOf(first), repository.rebuild(alice, 400))
        assertEquals(listOf(first), repository.hydrate(alice, listOf(first.member)).map { it.position })
        assertTrue(repository.page(bob, null, 10).isEmpty())
        assertTrue(repository.rebuild(bob, 400).isEmpty())
        assertTrue(repository.hydrate(bob, listOf(first.member)).isEmpty())

        following.respondToFollow(ACTOR, "follow-${bob.value}", NOW)
        val second = assertNotNull(repository.receive(note("second-private", "followers", setOf())))
        assertEquals(listOf(alice, bob), repository.recipients(second.member, NOW, null, 1000))
        assertEquals(listOf(second), repository.page(bob, null, 10))
        assertEquals(listOf(second), repository.rebuild(bob, 400))
        assertEquals(listOf(second), repository.hydrate(bob, listOf(first.member, second.member)).map { it.position })

        following.remove(bob, ACTOR)
        following.add(bob, ACTOR, NOW)
        assertEquals(listOf(alice), repository.recipients(second.member, NOW, null, 1000))
        assertTrue(repository.page(bob, null, 10).isEmpty())
        assertTrue(repository.rebuild(bob, 400).isEmpty())
        assertTrue(repository.hydrate(bob, listOf(first.member, second.member)).isEmpty())
        listOf("public", "unlisted").forEach { visibility ->
            val position = assertNotNull(repository.receive(note(visibility, visibility, setOf())))
            assertEquals(listOf(alice, bob), repository.recipients(position.member, NOW, null, 1000))
            assertEquals(listOf(position), repository.hydrate(bob, listOf(position.member)).map { it.position })
            assertTrue(position in repository.page(bob, null, 10))
        }
    }

    @Test
    fun rebuildLimitsEachActorAndCursorPreservesLexicalTies() = withRepository { repositories, accounts ->
        val account = accounts.getValue("alice")
        val repository = repositories.timelineRepository
        repositories.followingRepository.add(account, ACTOR, NOW)
        val positions = (1..430).map { assertNotNull(repository.receive(note(it.toString(), "public", setOf()))) }
        assertEquals(20, repository.rebuild(account, 400).size)
        val expected = positions.sortedByDescending { it.member }
        val first = repository.page(account, null, 400)
        val rest = repository.page(account, first.last(), 100)
        assertEquals(expected, first + rest)
        assertEquals(30, rest.size)
        assertEquals(400, repository.hydrate(account, first.map { it.member }).size)
    }

    @Test
    fun followResponsesMatchBothActorAndOriginalActivity() = withRepository { repositories, accounts ->
        val account = accounts.getValue("alice")
        val repository = repositories.timelineRepository
        repositories.followingRepository.add(account, ACTOR, NOW)
        repositories.followingRepository.recordActivity(account, ACTOR, "follow-1")
        repositories.deliveryQueueRepository.enqueueFollow(account, "$ACTOR/inbox", "follow", NOW)
        assertEquals("follow-1", repositories.followingRepository.followId(account, ACTOR))
        assertEquals("follow", repositories.deliveryQueueRepository.claim(NOW, 10).single().body)
        assertEquals(0, repositories.followingRepository.respondToFollow(ACTOR, "old-follow", null))
        assertTrue(repositories.followingRepository.contains(account, ACTOR))
        assertEquals(1, repositories.followingRepository.respondToFollow(ACTOR, "follow-1", NOW))
        assertEquals(0, repositories.followingRepository.respondToFollow(ACTOR, "follow-1", NOW))
        assertEquals(0, repositories.followingRepository.respondToFollow("https://other.example/actor", "follow-1", null))
        assertTrue(repositories.followingRepository.contains(account, ACTOR))
        assertEquals(1, repositories.followingRepository.respondToFollow(ACTOR, "follow-1", null))
        assertEquals(false, repositories.followingRepository.contains(account, ACTOR))
    }

    @Test
    fun receptionInterestCountsAllMatchingAccountsAndExistingRecipients() = withRepository { repositories, accounts ->
        val following = repositories.followingRepository
        following.add(accounts.getValue("alice"), ACTOR, NOW)
        following.add(accounts.getValue("bob"), ACTOR, NOW)
        following.add(accounts.getValue("alice"), "https://other.example/actor", NOW)
        val interest = repositories.timelineRepository.receptionInterest(ACTOR, setOf("alice", "bob", "unknown"))
        assertEquals(2L, interest.followingAccountCount)
        assertEquals(2L, interest.localRecipientCount)
        assertTrue(interest.shouldReceive)
        val unrelated = repositories.timelineRepository.receptionInterest("https://unknown.example/actor", setOf("unknown"))
        assertEquals(0L, unrelated.followingAccountCount)
        assertEquals(0L, unrelated.localRecipientCount)
        assertEquals(false, unrelated.shouldReceive)
    }

    @Test
    fun localPostsUseEpochMillisecondsAndOnlyReachTheirAuthor() = withRepository { repositories, accounts ->
        val time = NOW.plusNanos(999999999)
        repositories.deliveryQueueRepository.enqueueNote(
            NotePost("alice", "request", PublicNoteId("local-1"), "本文", time, listOf(), "create", listOf()),
        )
        val repository = repositories.timelineRepository
        val position = assertNotNull(repository.localPosition("local-1"))
        assertEquals(time.toEpochMilli(), position.score)
        assertEquals(listOf(accounts.getValue("alice")), repository.recipients(position.member, NOW, null, 1000))
        assertEquals(listOf(position), repository.rebuild(accounts.getValue("alice"), 400))
        assertEquals(listOf(position), repository.page(accounts.getValue("alice"), null, 10))
        assertEquals("alice", repository.hydrate(accounts.getValue("alice"), listOf(position.member)).single().username)
        assertTrue(repository.hydrate(accounts.getValue("bob"), listOf(position.member)).isEmpty())
    }

    @Test
    fun boundedSourcesUseClampedScoreAndCursorBeforeTheirLimits() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        val bob = accounts.getValue("bob")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repositories.followingRepository.add(bob, ACTOR, NOW)
        val positions = (1..70).map { index ->
            val input = note("score-$index", "public", setOf()).copy(
                publishedAt = NOW.plusSeconds(10000L - index),
                receivedAt = NOW.plusSeconds(index.toLong()),
            )
            checkNotNull(repository.receive(input))
        }
        (1..120).forEach { index ->
            repository.receive(note("private-$index", "direct", setOf("bob")))
        }
        assertEquals(positions.reversed().take(20), repository.rebuild(alice, 400))
        val first = repository.page(alice, null, 7)
        val second = repository.page(alice, first.last(), 7)
        assertEquals(positions.reversed().take(14), first + second)
        val ownDirect = checkNotNull(repository.receive(note("own-direct", "direct", setOf("alice"))))
        assertEquals(positions.reversed().take(20), repository.rebuild(alice, 400))
        assertTrue(ownDirect in repository.page(alice, positions.reversed()[19], 100))
        assertEquals(false, repository.shouldReceive("https://unknown.example/actor", setOf()))
        assertTrue(repository.shouldReceive("https://unknown.example/actor", setOf("alice")))
        assertTrue(repository.shouldReceive(ACTOR, setOf()))
    }

    @Test
    fun recipientUnionDeduplicatesAndUsesAccountCursor() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repository.touch(alice, NOW, TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        val position = checkNotNull(repository.receive(note("recipient-union", "public", setOf("alice", "dave"))))
        val first = repository.recipients(position.member, NOW.minusSeconds(604800), null, 1)
        val second = repository.recipients(position.member, NOW.minusSeconds(604800), first.single(), 1)
        assertEquals(listOf(alice, accounts.getValue("dave")), first + second)
        assertTrue(repository.recipients(position.member, NOW.minusSeconds(604800), second.single(), 1).isEmpty())
    }

    @Test
    fun addressedNotesPageWithoutFollowingTheirActor() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val positions = (1..45).map { index -> checkNotNull(repository.receive(note("address-$index", "direct", setOf("alice")))) }
        val expected = positions.sortedByDescending { it.member }
        val first = repository.page(accounts.getValue("alice"), null, 12)
        val second = repository.page(accounts.getValue("alice"), first.last(), 12)
        assertEquals(expected.take(24), first + second)
        assertEquals(expected, repository.rebuild(accounts.getValue("alice"), 400))
        assertTrue(repository.page(accounts.getValue("bob"), null, 100).isEmpty())
    }

    @Test
    fun rebuiltPrefixAndSqlContinuationIncludeEveryPostFromBusyActors() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val account = accounts.getValue("alice")
        val olderActor = "https://older.example/actor"
        repositories.remoteActorRepository.save(
            RemoteActorRecord(olderActor, "$olderActor/inbox", null, "key", RemoteActorProfileRecord("older", null, null, null, null, listOf())),
            NOW,
        )
        listOf(ACTOR, olderActor).forEach { repositories.followingRepository.add(account, it, NOW) }
        val newer = (1..30).map { index ->
            val published = NOW.minusSeconds(index.toLong())
            checkNotNull(repository.receive(note("newer-$index", "public", setOf()).copy(publishedAt = published)))
        }
        val older = (1..20).map { index ->
            checkNotNull(
                repository.receive(
                    note("older-$index", "public", setOf()).copy(
                        actorUri = olderActor,
                        objectUri = "$olderActor/notes/$index",
                        publishedAt = NOW.minusSeconds(100L + index),
                    ),
                ),
            )
        }
        val rebuilt = repository.rebuild(account, 400)
        assertEquals(newer.take(20), rebuilt)
        val allPages = buildList {
            var cursor: TimelinePosition? = null
            do {
                val pageCursor = cursor
                val cached = rebuilt.filter { position ->
                    pageCursor == null || position.score < pageCursor.score ||
                        (position.score == pageCursor.score && position.member < pageCursor.member)
                }.take(7)
                val page = cached + repository.page(account, cached.lastOrNull() ?: pageCursor, 7 - cached.size)
                addAll(page)
                cursor = page.lastOrNull()
            } while (cursor != null)
        }
        assertEquals(newer + older, allPages)
        assertEquals(newer + older, repository.page(account, null, 100))
    }

    @Test
    fun boostsOfFollowedActorAreShownOnlyToItsFollowersWithTheBoostedNote() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null, null, listOf())),
            NOW,
        )
        assertNull(repository.receiveBoost(boost("1", note("boosted", "public", setOf()))))
        repositories.followingRepository.add(alice, BOOSTER, NOW)
        val position = assertNotNull(repository.receiveBoost(boost("1", note("boosted", "public", setOf()))))
        assertNull(repository.receiveBoost(boost("1", note("boosted", "public", setOf()))))
        assertNull(repository.receiveBoost(boost("2", note("private", "followers", setOf()))))

        assertEquals(listOf(position), repository.page(alice, null, 10))
        repository.touch(alice, NOW, TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        assertEquals(listOf(alice), repository.recipients(position.member, NOW.minusSeconds(1), null, 10))
        val post = repository.hydrate(alice, listOf(position.member)).single()
        assertEquals(ACTOR, post.actorUri)
        assertEquals("<p>本文</p>", post.contentHtml)
        assertEquals(BOOSTER, post.boostedBy?.actorUri)
        assertEquals("booster", post.boostedBy?.username)
        assertTrue(repository.hydrate(accounts.getValue("bob"), listOf(position.member)).isEmpty())
        // 投稿者をフォローしていなくても、ブーストされた投稿だけがタイムラインに載ることはない
        assertTrue(repository.page(accounts.getValue("bob"), null, 10).isEmpty())

        assertTrue(repositories.boostRepository.remove(BOOSTER, "$BOOSTER/announces/1"))
        assertTrue(repository.hydrate(alice, listOf(position.member)).isEmpty())
    }

    @Test
    fun actorPostsIncludeBoostsOfThatActorNewestFirst() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null, null, listOf())),
            NOW,
        )
        repositories.followingRepository.add(alice, BOOSTER, NOW)
        val boost = assertNotNull(repository.receiveBoost(boost("1", note("boosted", "public", setOf()))))

        val post = repository.actorPosts(alice, BOOSTER, 10).single()
        assertEquals(boost, post.position)
        assertEquals(ACTOR, post.actorUri)
        assertEquals(BOOSTER, post.boostedBy?.actorUri)
        assertTrue(repository.actorPosts(alice, ACTOR, 10).isEmpty())
        assertTrue(repository.actorPosts(accounts.getValue("bob"), BOOSTER, 10).isEmpty())
    }

    @Test
    fun boostIsRemovedWithTheBoostedNote() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord(null, null, null, null, null, listOf())),
            NOW,
        )
        repositories.followingRepository.add(alice, BOOSTER, NOW)
        val position = assertNotNull(repository.receiveBoost(boost("1", note("boosted", "public", setOf()))))
        repository.deleteRemote(ACTOR, "$ACTOR/notes/boosted")
        assertTrue(repository.hydrate(alice, listOf(position.member)).isEmpty())
        assertTrue(repository.page(alice, null, 10).isEmpty())
    }

    @Test
    fun createAfterBoostStillRegistersExplicitRecipients() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord(null, null, null, null, null, listOf())),
            NOW,
        )
        repositories.followingRepository.add(accounts.getValue("alice"), BOOSTER, NOW)
        assertNotNull(repository.receiveBoost(boost("1", note("mention", "public", setOf()))))
        val position = assertNotNull(repository.receive(note("mention", "public", setOf("dave"))))
        assertEquals(listOf(position), repository.page(accounts.getValue("dave"), null, 10))
        assertNull(repository.receive(note("mention", "public", setOf("dave"))))
    }

    @Test
    fun boosterKeyIsFoundFromRemoteBoosts() = withRepository { repositories, accounts ->
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "booster-key", RemoteActorProfileRecord(null, null, null, null, null, listOf())),
            NOW,
        )
        assertNull(repositories.boostRepository.findPublicKeyPem(BOOSTER))
        repositories.followingRepository.add(accounts.getValue("alice"), BOOSTER, NOW)
        assertNotNull(repositories.timelineRepository.receiveBoost(boost("1", note("boosted", "public", setOf()))))
        assertEquals("booster-key", repositories.boostRepository.findPublicKeyPem(BOOSTER))
    }

    private fun boost(id: String, note: RemoteNoteInput): RemoteBoostInput = RemoteBoostInput(
        activityUri = "$BOOSTER/announces/$id",
        boosterUri = BOOSTER,
        boostedAt = NOW,
        receivedAt = NOW,
        note = note,
    )

    private fun note(id: String, visibility: String, recipients: Set<String>): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", visibility = visibility,
        localRecipients = recipients, inReplyTo = null, summary = null, sensitive = false, publishedAt = NOW.plusSeconds(3600), receivedAt = NOW,
        images = listOf(),
        emojis = listOf(),
    )

    private fun withRepository(block: (Repositories, Map<String, AccountId>) -> Unit) {
        val directory = createTempDirectory("timeline-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob", "carol", "dave").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf())),
                    NOW,
                )
                block(repositories, listOf("alice", "bob", "carol", "dave").associateWith { checkNotNull(repositories.accountRepository.findByUsername(it)).id })
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
