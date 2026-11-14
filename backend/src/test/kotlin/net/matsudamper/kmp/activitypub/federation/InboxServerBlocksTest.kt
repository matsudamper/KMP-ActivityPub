package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.AccountEvents
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.favourite.FavouriteStore

class InboxServerBlocksTest {
    private val repositories = FakeRepositories()
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private val serverBlocks = InboxServerBlocks(repositories.accountRepository, repositories.noteRepository, repositories.serverBlockRepository)
    private val favourites = RecordedFavourites(
        repositories.favouriteRepository,
        ReceivedNotifications(repositories.notificationRepository, AccountEvents(repositories.timelineRepository)),
        serverBlocks,
    )

    init {
        repositories.accountRepository.add("bob", "hash", "type")
        repositories.noteRepository.notes.add(Note(PublicNoteId("alice-note"), "alice", "<p>投稿</p>", Instant.EPOCH, listOf(), null, listOf()))
        repositories.noteRepository.notes.add(Note(PublicNoteId("bob-note"), "bob", "<p>投稿</p>", Instant.EPOCH, listOf(), null, listOf()))
        repositories.serverBlockRepository.add(alice, "blocked.example", Instant.EPOCH)
    }

    @Test
    fun favouriteFromBlockedServerOnBlockingAccountsNoteIsNotStored() {
        assertFalse(favourites.add(favourite("alice-note", "https://blocked.example/users/carol")))
        repositories.serverBlockRepository.remove(alice, "blocked.example")
        assertTrue(favourites.add(favourite("alice-note", "https://blocked.example/users/carol")))
    }

    @Test
    fun favouriteFromOtherServerOrOnOtherAccountsNoteIsStored() {
        assertTrue(favourites.add(favourite("alice-note", "https://other.example/users/dave")))
        assertTrue(favourites.add(favourite("bob-note", "https://blocked.example/users/carol")))
    }

    @Test
    fun blocksOnlyAccountsThatBlockTheActorsServer() {
        assertTrue(serverBlocks.blocks("alice", "https://blocked.example/users/carol"))
        assertFalse(serverBlocks.blocks("bob", "https://blocked.example/users/carol"))
        assertFalse(serverBlocks.blocks("alice", "https://sub.blocked.example/users/carol"))
    }

    private fun favourite(notePublicId: String, actorUri: String): FavouriteStore.ReceivedFavourite =
        FavouriteStore.ReceivedFavourite(
            KotpubPublicNoteId(notePublicId),
            RemoteActor(actorUri, "$actorUri/inbox", null, "key", RemoteActorProfile("carol", null, actorUri, null, null, emojis = listOf())),
            Instant.EPOCH,
        )
}
