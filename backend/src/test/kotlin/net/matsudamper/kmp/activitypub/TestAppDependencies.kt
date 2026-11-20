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
    val REQUIRED: Map<String, String> =
        mapOf(
            "HOST" to "0.0.0.0",
            "PORT" to "8080",
            "DB_PATH" to "./data/kmp-activitypub.db",
            "COOKIE_SECURE" to "true",
            "TRUST_CF_CONNECTING_IP" to "false",
            "ENABLE_PASSKEY" to "true",
            "PASSKEY_RP_ID" to "localhost",
            "PASSKEY_ORIGINS" to "https://localhost",
            "DOMAIN" to "localhost",
            "ENABLE_S3" to "false",
            "REMOTE_ICON_PROXY_SECRET" to "test-secret",
        )
}
