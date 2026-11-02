package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.timeline.FakeTimelineStore

@Suppress("FunctionName")
internal fun TestDependencies(
    repositories: FakeRepositories = FakeRepositories(),
    env: ServerEnv = ServerEnv(TestEnv.REQUIRED),
): AppDependencies = AppDependencies(
    repositories = repositories,
    sessionStore = FakeUserSessionStore(),
    timelineStore = FakeTimelineStore(mutableListOf()),
    passkeyChallengeStore = FakePasskeyChallengeStore(),
    loginFailureStore = FakeLoginFailureStore(),
    env = env,
)

internal object TestEnv {
    val REQUIRED: Map<String, String> = mapOf("ENABLE_PASSKEY" to "true", "PASSKEY_RP_ID" to "localhost", "DOMAIN" to "localhost", "ENABLE_S3" to "false", "REMOTE_ICON_PROXY_SECRET" to "test-secret")
}
