package net.matsudamper.kmp.activitypub

@Suppress("FunctionName")
internal fun TestDependencies(
    repositories: FakeRepositories = FakeRepositories(),
    env: ServerEnv = ServerEnv(TestEnv.REQUIRED),
): AppDependencies = AppDependencies(repositories = repositories, sessionStore = FakeUserSessionStore(), env = env)

internal object TestEnv {
    val REQUIRED: Map<String, String> = mapOf("PASSKEY_RP_ID" to "localhost")
}
