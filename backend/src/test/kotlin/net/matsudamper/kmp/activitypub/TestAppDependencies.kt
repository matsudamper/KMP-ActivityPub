package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.delivery.LocalActorKeys
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kotpub.TestActorPrivateKey
import net.matsudamper.kotpub.TestDelivery
import net.matsudamper.kotpub.TestRemoteActors
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.delivery.ActivityDelivery

/**
 * 差し替えたいものだけを名前付きで指定して、残りはフェイクの既定に任せる。
 * ルーティングのテストが必要とするのは大抵 1 つか 2 つなので、
 * 全部を毎回並べると何を差し替えたのかが埋もれる。
 *
 * 他のサーバーとのやり取りは [env] に `DOMAIN` があるときだけ組み立てる
 */
@Suppress("FunctionName")
internal fun TestDependencies(
    repositories: Repositories = FakeRepositories(),
    env: ServerEnv = ServerEnv(emptyMap()),
    remoteActors: RemoteActors = TestRemoteActors(),
    delivery: ActivityDelivery = TestDelivery(),
    actorKeys: LocalActorKeys = LocalActorKeys { TestActorPrivateKey.value },
): AppDependencies =
    AppDependencies(
        repositories = repositories,
        sessionStore = FakeUserSessionStore(),
        loginFailureStore = FakeLoginFailureStore(),
        env = env,
        createFederation = { domain, accountService ->
            FederationDependencies(
                repositories = repositories,
                domain = domain,
                accountService = accountService,
                remoteActors = remoteActors,
                delivery = delivery,
                actorKeys = actorKeys,
            )
        },
    )
