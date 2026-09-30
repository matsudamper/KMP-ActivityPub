package net.matsudamper.kmp.activitypub

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import net.matsudamper.activitypub.actor.ActorUrls
import net.matsudamper.kmp.activitypub.repository.IncomingFollow
import net.matsudamper.kmp.activitypub.repository.NewRemoteActor
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfile
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles

// 投函する `Create` に入る画面の URL（`url`）を見る。
// 画面が無いのに出すと、相手のタイムラインのリンクが見つからないページを指す。
class AppDependenciesTest {
    private val staticSrcDir: Path = Files.createTempDirectory("app-dependencies-test")

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        staticSrcDir.deleteRecursively()
    }

    @Test
    fun `画面を配信する構成でも投函する Create に url が入らない`() {
        staticSrcDir.resolve(StaticFiles.INDEX_FILE_NAME).writeText("<html></html>")
        val deps = testDependencies(env = TestServerEnv.of("STATIC_SRC_DIR" to staticSrcDir.toString()))
        deps.acceptFollower()

        deps.noteEnqueuer.enqueue(
            sender = ActorUrls(domain = TestServerEnv.DOMAIN, username = TestServerEnv.USERNAME),
            contentHtml = "<p>本文</p>",
        )

        // アカウントと投稿の画面はまだ無い。出すと相手のパーマリンクが見つからないページを指す
        assertFalse(deps.queuedBody().contains(""""url":"""))
    }

    @Test
    fun `画面を配信しない構成では投函する Create に url が入らない`() {
        val deps = testDependencies()
        deps.acceptFollower()

        deps.noteEnqueuer.enqueue(
            sender = ActorUrls(domain = TestServerEnv.DOMAIN, username = TestServerEnv.USERNAME),
            contentHtml = "<p>本文</p>",
        )

        // 出すと相手のパーマリンクが 404 のページを指す。無ければ相手は id に倒す
        assertFalse(deps.queuedBody().contains(""""url":"""))
    }

    /**
     * 投函した 1 件の中身。ワーカーは動かさないので、キューから直に取り出して見る
     */
    private fun AppDependencies.queuedBody(): String =
        repositories.deliveryQueue.claim(now = Instant.now(), limit = 10).single().body

    /**
     * 配信先が 1 つある状態にする。フォロワーが 0 だと 1 件も投函されないので、
     * 投函した中身を見るテストが素通りする
     */
    private fun AppDependencies.acceptFollower() {
        val followerActorUri = "https://remote.example/users/alice"
        repositories.followers.record(
            IncomingFollow(
                username = TestServerEnv.USERNAME,
                follower = NewRemoteActor(
                    actorUri = followerActorUri,
                    inbox = "https://remote.example/users/alice/inbox",
                    sharedInbox = null,
                    publicKeyPem = "",
                    profile = RemoteActorProfile(preferredUsername = null, displayName = null, profileUrl = null, iconUrl = null),
                ),
                followActivityUri = "https://remote.example/activities/follow-1",
                receivedAt = Instant.now(),
                acceptBody = """{"type":"Accept"}""",
            ),
        )
        // Accept が届いて初めてフォロワーになる。投函した行はここで消える
        val now = Instant.now()
        repositories.deliveryQueue.claim(now = now, limit = 10).forEach {
            repositories.deliveryQueue.markDelivered(id = it.id, deliveredAt = now)
        }
    }
}
